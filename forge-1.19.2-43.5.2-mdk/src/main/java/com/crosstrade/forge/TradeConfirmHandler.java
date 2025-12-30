package com.crosstrade.forge;

import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

public class TradeConfirmHandler extends WebSocketListener {
    private static final String TRANSIT_SERVER = "ws://192.168.1.100:5000";
    private final OkHttpClient client = new OkHttpClient();
    private final Gson gson = new Gson();

    public void startListening() {
        Request request = new Request.Builder().url(TRANSIT_SERVER + "/ws").build();
        client.newWebSocket(request, this);
        client.dispatcher().executorService().shutdown();
    }

    @Override
    public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
        super.onMessage(webSocket, text);
        try {
            TradeConfirmResponse response = gson.fromJson(text, TradeConfirmResponse.class);
            if (response != null && response.code == 200 && response.trade != null) {
                executeTrade(response.trade);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void executeTrade(TradeData trade) {
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(UUID.fromString(trade.from_player));
            if (player == null) return;

            removeTargetItem(player, trade);
            addRewardItem(player, trade);
            sendTradeCompleteMessage(player, trade);
        });
    }

    private void removeTargetItem(ServerPlayer player, TradeData trade) {
        ResourceLocation offerRl = ResourceLocation.parse(trade.offer_item_id);
        Item offerItem = ForgeRegistries.ITEMS.getValue(offerRl);
        if (offerItem == null) return;

        int needRemoveCount = trade.offer_count;
        net.minecraft.world.Container inv = player.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack currentStack = inv.getItem(i);
            if (!currentStack.isEmpty() && currentStack.getItem() == offerItem) {
                int removeCount = Math.min(needRemoveCount, currentStack.getCount());
                currentStack.shrink(removeCount);
                needRemoveCount -= removeCount;
                if (needRemoveCount <= 0) break;
            }
        }
    }

    private void addRewardItem(ServerPlayer player, TradeData trade) {
        ResourceLocation requestRl = ResourceLocation.parse(trade.request_item_id);
        Item requestItem = ForgeRegistries.ITEMS.getValue(requestRl);
        if (requestItem == null) return;

        ItemStack rewardStack = new ItemStack(requestItem, trade.request_count);
        if (!trade.request_nbt.isEmpty()) {
            try {
                CompoundTag rewardTag = net.minecraft.nbt.NbtIo.readCompressed(new ByteArrayInputStream(trade.request_nbt.getBytes()));
                rewardStack.setTag(rewardTag);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        player.getInventory().add(rewardStack);
    }

    private void sendTradeCompleteMessage(ServerPlayer player, TradeData trade) {
        Component completeMessage = Component.literal(
                "跨服交易完成！\n扣除物品：" + trade.offer_item_id + " × " + trade.offer_count + "\n获得物品：" + trade.request_item_id + " × " + trade.request_count
        );
        player.sendSystemMessage(completeMessage);
    }

    @Override
    public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
        super.onClosing(webSocket, code, reason);
        webSocket.close(1000, "正常关闭连接");
    }

    @Override
    public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        t.printStackTrace();
    }

    static class TradeConfirmResponse {
        public int code;
        public String msg;
        public TradeData trade;
    }

    static class TradeData {
        public String trade_id;
        public String from_server;
        public String from_player;
        public String offer_item_id;
        public int offer_count;
        public String offer_nbt;
        public String request_item_id;
        public int request_count;
        public String request_nbt;
        public String to_server;
        public String to_player;
    }
}
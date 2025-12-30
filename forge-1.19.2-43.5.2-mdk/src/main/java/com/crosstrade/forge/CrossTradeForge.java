package com.crosstrade.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.UUID;

@Mod("crosstradeforge")
public class CrossTradeForge {

    private static final String TRANSIT_SERVER_HTTP = "http://192.168.1.100:5000";
    private final OkHttpClient okHttpClient = new OkHttpClient();
    private TradeConfirmHandler tradeConfirmHandler;

    public CrossTradeForge() {
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerJoin);
    }

    private void onServerStarting(@NotNull ServerStartingEvent event) {
        tradeConfirmHandler = new TradeConfirmHandler();
        tradeConfirmHandler.startListening();
        event.getServer().sendSystemMessage(Component.literal("[CrossTradeForge] 跨服交易插件已启动，已连接中转服务器！"));
    }

    private void onPlayerJoin(@NotNull PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        sendTradeRequestToTransit(serverPlayer);
    }

    private void sendTradeRequestToTransit(ServerPlayer player) {
        final ServerPlayer serverPlayer = player;

        String playerUUID = serverPlayer.getUUID().toString();
        String offerItemId = "minecraft:diamond";
        int offerCount = 1;
        String requestItemId = "minecraft:emerald";
        int requestCount = 2;

        String requestUrl = String.format(
                "%s/trade/request?player_uuid=%s&offer_item=%s&offer_count=%d&request_item=%s&request_count=%d",
                TRANSIT_SERVER_HTTP,
                playerUUID,
                offerItemId,
                offerCount,
                requestItemId,
                requestCount
        );

        Request request = new Request.Builder()
                .url(requestUrl)
                .get()
                .build();

        okHttpClient.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NotNull okhttp3.Call call, @NotNull IOException e) {
                if (serverPlayer.getServer() != null) {
                    serverPlayer.getServer().sendSystemMessage(Component.literal(
                            "[CrossTradeForge] 发送交易请求失败：" + e.getMessage()
                    ));
                }
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull okhttp3.Call call, @NotNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    serverPlayer.sendSystemMessage(Component.literal(
                            "[CrossTradeForge] 交易请求已提交：" + responseBody
                    ));
                } else {
                    serverPlayer.sendSystemMessage(Component.literal(
                            "[CrossTradeForge] 交易请求提交失败，响应码：" + response.code()
                    ));
                }
                response.close();
            }
        });
    }

    private Item getItemById(String itemId) {
        ResourceLocation itemRl = ResourceLocation.parse(itemId);
        return ForgeRegistries.ITEMS.getValue(itemRl);
    }

    private void giveItemToPlayer(ServerPlayer player, String itemId, int count) {
        Item item = getItemById(itemId);
        if (item == null) {
            player.sendSystemMessage(Component.literal("[CrossTradeForge] 物品不存在：" + itemId));
            return;
        }

        ItemStack itemStack = new ItemStack(item, count);
        player.getInventory().add(itemStack);
        player.sendSystemMessage(Component.literal(String.format(
                "[CrossTradeForge] 已获得物品：%s × %d",
                itemId,
                count
        )));
    }
}
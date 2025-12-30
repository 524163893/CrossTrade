package com.crosstrade.forge;

public class TradeRequest {
    public String from_server;
    public String from_player;
    public String offer_item_id;
    public int offer_count;
    public String offer_nbt;
    public String request_item_id;
    public int request_count;
    public String request_nbt;

    public TradeRequest(String from_server, String from_player, String offer_item_id, int offer_count, String offer_nbt,
                        String request_item_id, int request_count, String request_nbt) {
        this.from_server = from_server;
        this.from_player = from_player;
        this.offer_item_id = offer_item_id;
        this.offer_count = offer_count;
        this.offer_nbt = offer_nbt;
        this.request_item_id = request_item_id;
        this.request_count = request_count;
        this.request_nbt = request_nbt;
    }
}
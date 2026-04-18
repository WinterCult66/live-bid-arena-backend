package com.live.bid.arena.domain;

/** Definición estática de una subasta en catálogo (sin BD). */
public record AuctionLot(long id, String name, int initialBid, String status) {}

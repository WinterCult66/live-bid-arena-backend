package com.live.bid.arena.session;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Estado mutable de una mesa: sala de espera (LOBBY) → subasta en vivo (LIVE) → FINISHED. */
public final class AuctionRoomState {

    public static final int MAX_PLAYERS = 4;
    private static final int LOBBY_WAIT_SECONDS = 20;
    private static final int LIVE_INITIAL_SECONDS = 30;
    private static final int BID_RESET_SECONDS = 10;

    public enum Phase {
        LOBBY,
        LIVE,
        FINISHED
    }

    private final long auctionId;
    private final String name;
    private final int initialBid;

    private volatile Phase phase = Phase.LOBBY;
    private volatile int lobbyTimeLeft = LOBBY_WAIT_SECONDS;
    private volatile int bidTimeLeft;
    private volatile int currentPrice;
    private volatile String lastBidder = "Sin pujas";

    private final Set<Long> participantIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> idToName = new ConcurrentHashMap<>();
    /** Orden de llegada a la mesa (para asientos). */
    private final CopyOnWriteArrayList<String> participantNamesOrder = new CopyOnWriteArrayList<>();

    /** Total apostado en esta mesa por jugador (suma de pujas), no global. */
    private final Map<Long, Integer> spentByPlayerId = new ConcurrentHashMap<>();

    public AuctionRoomState(long auctionId, String name, int initialBid) {
        this.auctionId = auctionId;
        this.name = name;
        this.initialBid = initialBid;
        this.currentPrice = initialBid;
    }

    public long auctionId() {
        return auctionId;
    }

    public String name() {
        return name;
    }

    public int initialBid() {
        return initialBid;
    }

    public Phase phase() {
        return phase;
    }

    public int lobbyTimeLeft() {
        return lobbyTimeLeft;
    }

    /** Tiempo de cierre de la ronda de pujas (solo LIVE). */
    public int bidTimeLeft() {
        return bidTimeLeft;
    }

    public int currentPrice() {
        return currentPrice;
    }

    public String lastBidder() {
        return lastBidder;
    }

    public Set<Long> participantIdsView() {
        return Collections.unmodifiableSet(participantIds);
    }

    public List<String> participantsOrdered() {
        return List.copyOf(participantNamesOrder);
    }

    public boolean addParticipant(long playerId, String playerName) {
        if (participantIds.size() >= MAX_PLAYERS) {
            return false;
        }
        if (participantIds.contains(playerId)) {
            return true;
        }
        participantIds.add(playerId);
        idToName.put(playerId, playerName);
        participantNamesOrder.add(playerName);
        return true;
    }

    public boolean hasParticipant(long playerId) {
        return participantIds.contains(playerId);
    }

    public int spentBy(long playerId) {
        return spentByPlayerId.getOrDefault(playerId, 0);
    }

    public enum BidOutcome {
        APPLIED,
        INSUFFICIENT_FUNDS,
        REJECTED
    }

    /**
     * Aplica una puja: el precio de mesa sube y el margen de cada jugador es (saldo catálogo − precio mesa).
     * Una puja es válida si {@code saldo >= precioActual + monto} (al pagar el nuevo precio seguirías en positivo).
     */
    public BidOutcome tryApplyBid(long playerId, String playerName, int amount, int playerBalance) {
        if (phase != Phase.LIVE) {
            return BidOutcome.REJECTED;
        }
        if (bidTimeLeft <= 0) {
            return BidOutcome.REJECTED;
        }
        if (!participantIds.contains(playerId)) {
            return BidOutcome.REJECTED;
        }
        int spent = spentByPlayerId.getOrDefault(playerId, 0);
        int newPrice = currentPrice + amount;
        if (newPrice > playerBalance) {
            return BidOutcome.INSUFFICIENT_FUNDS;
        }
        spentByPlayerId.put(playerId, spent + amount);
        currentPrice = newPrice;
        lastBidder = playerName;
        bidTimeLeft = BID_RESET_SECONDS;
        return BidOutcome.APPLIED;
    }

    public void tick() {
        if (phase == Phase.FINISHED) {
            return;
        }
        if (phase == Phase.LOBBY) {
            lobbyTimeLeft = Math.max(0, lobbyTimeLeft - 1);
            if (lobbyTimeLeft == 0) {
                if (participantIds.size() >= 2) {
                    phase = Phase.LIVE;
                    bidTimeLeft = LIVE_INITIAL_SECONDS;
                } else {
                    lobbyTimeLeft = LOBBY_WAIT_SECONDS;
                }
            }
            return;
        }
        if (phase == Phase.LIVE) {
            bidTimeLeft = Math.max(0, bidTimeLeft - 1);
            if (bidTimeLeft == 0) {
                phase = Phase.FINISHED;
            }
        }
    }
}

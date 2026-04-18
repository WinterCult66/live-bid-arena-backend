package com.live.bid.arena.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.live.bid.arena.catalog.CatalogService;
import com.live.bid.arena.domain.AuctionLot;
import com.live.bid.arena.domain.Player;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AuctionSessionService {

    private final CatalogService catalog;
    private final ObjectMapper objectMapper;

    private final Map<Long, AuctionRoomState> rooms = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<WebSocketSession>> sessionsByAuction = new ConcurrentHashMap<>();

    public AuctionSessionService(CatalogService catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public void registerSession(long auctionId, WebSocketSession session) {
        sessionsByAuction.computeIfAbsent(auctionId, k -> new CopyOnWriteArrayList<>()).add(session);
    }

    public void removeSession(long auctionId, WebSocketSession session) {
        List<WebSocketSession> list = sessionsByAuction.get(auctionId);
        if (list != null) {
            list.remove(session);
        }
    }

    /**
     * Borra mesas en memoria y cierra todas las sesiones WebSocket (para demo sin BD).
     */
    public void resetAll() {
        for (CopyOnWriteArrayList<WebSocketSession> list : sessionsByAuction.values()) {
            for (WebSocketSession s : list) {
                if (s.isOpen()) {
                    try {
                        s.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
        }
        sessionsByAuction.clear();
        rooms.clear();
    }

    public String joinRoom(long auctionId, long playerId) {
        AuctionLot lot = catalog.findLot(auctionId).orElse(null);
        if (lot == null) {
            return "Subasta no encontrada";
        }
        Player player = catalog.findPlayer(playerId).orElse(null);
        if (player == null) {
            return "Jugador no encontrado";
        }
        if (player.balance() < lot.initialBid()) {
            return "Saldo insuficiente para la puja inicial";
        }
        AuctionRoomState room = rooms.computeIfAbsent(auctionId,
                id -> new AuctionRoomState(lot.id(), lot.name(), lot.initialBid()));
        if (room.hasParticipant(playerId)) {
            return null;
        }
        if (!room.addParticipant(playerId, player.name())) {
            return "Mesa llena (máx. " + AuctionRoomState.MAX_PLAYERS + " jugadores)";
        }
        return null;
    }

    public void handleBid(long auctionId, long playerId, int amount, WebSocketSession session) throws IOException {
        AuctionRoomState room = rooms.get(auctionId);
        if (room == null) {
            return;
        }
        if (room.phase() != AuctionRoomState.Phase.LIVE) {
            return;
        }
        if (amount != 10 && amount != 50 && amount != 100) {
            return;
        }
        Player player = catalog.findPlayer(playerId).orElse(null);
        if (player == null) {
            return;
        }
        AuctionRoomState.BidOutcome outcome = room.tryApplyBid(playerId, player.name(), amount, player.balance());
        if (outcome == AuctionRoomState.BidOutcome.INSUFFICIENT_FUNDS) {
            int avail = Math.max(0, player.balance() - room.currentPrice());
            sendError(session,
                    "Saldo insuficiente para subir la mesa a $" + (room.currentPrice() + amount)
                            + ". Tu saldo es $" + player.balance()
                            + "; con el precio actual $" + room.currentPrice()
                            + " te quedan $" + avail + " para pujar.");
            return;
        }
        if (outcome == AuctionRoomState.BidOutcome.APPLIED) {
            broadcastState(auctionId);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void tickAllRooms() {
        for (Map.Entry<Long, AuctionRoomState> e : rooms.entrySet()) {
            AuctionRoomState room = e.getValue();
            AuctionRoomState.Phase before = room.phase();
            room.tick();
            AuctionRoomState.Phase after = room.phase();
            // FINISHED: tick es no-op; no spamear STATE repetido cada segundo.
            if (after == AuctionRoomState.Phase.FINISHED && before == AuctionRoomState.Phase.FINISHED) {
                continue;
            }
            broadcastState(e.getKey());
        }
    }

    public void broadcastState(long auctionId) {
        AuctionRoomState room = rooms.get(auctionId);
        if (room == null) {
            return;
        }
        ObjectNode shared = buildSharedStatePayload(auctionId, room);
        String sharedJson;
        try {
            sharedJson = objectMapper.writeValueAsString(shared);
        } catch (JsonProcessingException e) {
            return;
        }
        List<WebSocketSession> list = sessionsByAuction.get(auctionId);
        if (list == null) {
            return;
        }
        for (WebSocketSession s : list) {
            if (!s.isOpen()) {
                continue;
            }
            try {
                ObjectNode payload = (ObjectNode) objectMapper.readTree(sharedJson);
                Object pidObj = s.getAttributes().get("playerId");
                if (pidObj instanceof Number pidNum) {
                    long pid = pidNum.longValue();
                    Player p = catalog.findPlayer(pid).orElse(null);
                    if (p != null) {
                        int spent = room.spentBy(pid);
                        int price = room.currentPrice();
                        int bal = p.balance();
                        /** Disponible para pujar = saldo catálogo − precio actual de la mesa (igual para todos). */
                        int remaining = Math.max(0, bal - price);
                        /** Referencia UI: menor entre saldo y precio (tu “tope” frente a la mesa). */
                        int committed = Math.min(bal, price);
                        payload.put("yourSpent", spent);
                        payload.put("yourRemaining", remaining);
                        payload.put("yourCommittedTotal", committed);
                    }
                }
                s.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private ObjectNode buildSharedStatePayload(long auctionId, AuctionRoomState room) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "STATE");
        payload.put("auctionId", auctionId);
        payload.put("name", room.name());
        payload.put("initialBid", room.initialBid());
        payload.put("price", room.currentPrice());
        payload.put("tableIncrementTotal", Math.max(0, room.currentPrice() - room.initialBid()));
        payload.put("phase", room.phase().name());
        payload.put("lobbyTimeLeft", room.lobbyTimeLeft());
        payload.put("timeLeft", room.bidTimeLeft());
        payload.put("status", mapUiStatus(room.phase()));
        payload.put("lastBidder", room.lastBidder());
        var arr = payload.putArray("participants");
        room.participantsOrdered().forEach(arr::add);
        return payload;
    }

    private static String mapUiStatus(AuctionRoomState.Phase phase) {
        return switch (phase) {
            case LOBBY -> "WAITING";
            case LIVE -> "ACTIVE";
            case FINISHED -> "FINISHED";
        };
    }

    public void sendError(WebSocketSession session, String message) throws IOException {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("type", "ERROR");
        n.put("message", message);
        session.sendMessage(new TextMessage(n.toString()));
    }

    public void handleClientMessage(WebSocketSession session, String raw) throws IOException {
        JsonNode root = objectMapper.readTree(raw);
        String type = root.path("type").asText("");
        if ("JOIN".equals(type)) {
            long auctionId = root.path("auctionId").asLong();
            long playerId = root.path("playerId").asLong();
            session.getAttributes().put("auctionId", auctionId);
            session.getAttributes().put("playerId", playerId);
            String err = joinRoom(auctionId, playerId);
            if (err != null) {
                sendError(session, err);
                return;
            }
            registerSession(auctionId, session);
            broadcastState(auctionId);
            return;
        }
        if ("BID".equals(type)) {
            Object aid = session.getAttributes().get("auctionId");
            Object pid = session.getAttributes().get("playerId");
            if (!(aid instanceof Number) || !(pid instanceof Number)) {
                sendError(session, "Primero envía JOIN");
                return;
            }
            long auctionId = ((Number) aid).longValue();
            long playerId = ((Number) pid).longValue();
            int amount = root.path("amount").asInt();
            handleBid(auctionId, playerId, amount, session);
        }
    }
}

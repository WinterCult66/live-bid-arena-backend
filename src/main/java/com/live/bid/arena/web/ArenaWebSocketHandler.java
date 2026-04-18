package com.live.bid.arena.web;

import com.live.bid.arena.session.AuctionSessionService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ArenaWebSocketHandler extends TextWebSocketHandler {

    private final AuctionSessionService sessions;

    public ArenaWebSocketHandler(AuctionSessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        sessions.handleClientMessage(session, message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object aid = session.getAttributes().get("auctionId");
        if (aid instanceof Number n) {
            sessions.removeSession(n.longValue(), session);
        }
    }
}

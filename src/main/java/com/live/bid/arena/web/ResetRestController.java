package com.live.bid.arena.web;

import com.live.bid.arena.session.AuctionSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ResetRestController {

    private final AuctionSessionService sessions;

    public ResetRestController(AuctionSessionService sessions) {
        this.sessions = sessions;
    }

    /** Reinicia mesas y sesiones en memoria (sin tocar el catálogo de jugadores/subastas). */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        sessions.resetAll();
        return ResponseEntity.ok(Map.of("ok", "true", "message", "Estado de mesas reiniciado"));
    }
}

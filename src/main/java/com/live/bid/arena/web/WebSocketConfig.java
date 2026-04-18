package com.live.bid.arena.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ArenaWebSocketHandler arenaWebSocketHandler;

    public WebSocketConfig(ArenaWebSocketHandler arenaWebSocketHandler) {
        this.arenaWebSocketHandler = arenaWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(arenaWebSocketHandler, "/ws/arena")
                .setAllowedOrigins("http://localhost:4200", "http://127.0.0.1:4200");
    }
}

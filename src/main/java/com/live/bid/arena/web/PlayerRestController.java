package com.live.bid.arena.web;

import com.live.bid.arena.catalog.CatalogService;
import com.live.bid.arena.domain.Player;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/players")
public class PlayerRestController {

    private final CatalogService catalog;

    public PlayerRestController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<Player> list() {
        return catalog.listPlayers();
    }
}

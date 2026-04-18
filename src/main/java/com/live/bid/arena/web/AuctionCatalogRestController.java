package com.live.bid.arena.web;

import com.live.bid.arena.catalog.CatalogService;
import com.live.bid.arena.domain.AuctionLot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auctions")
public class AuctionCatalogRestController {

    private final CatalogService catalog;

    public AuctionCatalogRestController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<AuctionLot> list() {
        return catalog.listLots();
    }
}

package com.live.bid.arena.catalog;

import com.live.bid.arena.domain.AuctionLot;
import com.live.bid.arena.domain.Player;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CatalogService {

    private final List<Player> players = List.of(
            new Player(1, "Kevin", 1000),
            new Player(2, "Allison", 800),
            new Player(3, "Arnold", 1200),
            new Player(4, "Annie", 500)
    );

    private final List<AuctionLot> lots = List.of(
            new AuctionLot(1, "Reloj vintage", 150, "OPEN"),
            new AuctionLot(2, "Arte digital", 300, "OPEN"),
            new AuctionLot(3, "Consola retro", 200, "OPEN")
    );

    public List<Player> listPlayers() {
        return players;
    }

    public List<AuctionLot> listLots() {
        return lots;
    }

    public Optional<Player> findPlayer(long id) {
        return players.stream().filter(p -> p.id() == id).findFirst();
    }

    public Optional<AuctionLot> findLot(long id) {
        return lots.stream().filter(l -> l.id() == id).findFirst();
    }
}

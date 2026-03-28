package com.absolutgg.absolutevents.api;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public interface EventoInterface {

    void startCall();

    void start();

    void forceStart();

    void stop();

    void removePlayers();

    void winner(Player player);

    void setWinner(Player player);

    void setWinners();

    void join(Player player);

    void leave(Player player);

    void remove(Player player);

    void remove(Player player, boolean leaved);

    void spectate(Player player);

    List<Player> getPlayers();

    List<Player> getSpectators();

    YamlConfiguration getConfig();

    String getPermission();

    EventoType getType();

    boolean isElimination();

    boolean isHappening();

    boolean isOpen();

    boolean isSpectatorAllowed();

    boolean requireEmptyInventory();

    boolean countParticipation();

    boolean countWin();
}
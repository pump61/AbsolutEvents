package com.absolutgg.absolutevents.api.events;

import com.absolutgg.absolutevents.api.EventoType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/*
 * Chamado quando um jogador entra em um evento.
 */
public final class PlayerJoinEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String evento;
    private final EventoType type;

    public PlayerJoinEvent(Player player, String evento, EventoType type) {
        this.player = player;
        this.evento = evento;
        this.type = type;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public Player getPlayer() {
        return player;
    }

    public String getEvento() {
        return evento;
    }

    public EventoType getEventoType() {
        return type;
    }
}
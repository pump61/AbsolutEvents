package com.absolutgg.absolutevents.api.events;

import com.absolutgg.absolutevents.api.EventoType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/*
 * Chamado quando o evento é encerrado.
 */
public final class EventoStopEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String evento;
    private final EventoType type;

    public EventoStopEvent(String evento, EventoType type) {
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

    public String getEvento() {
        return evento;
    }

    public EventoType getEventoType() {
        return type;
    }
}
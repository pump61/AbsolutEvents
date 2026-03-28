package com.absolutgg.absolutevents.api.events;

import com.absolutgg.absolutevents.api.EventoType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/*
 * Chamado quando um evento começa a ser iniciado (fase de anúncio / chamadas).
 */
public final class EventoStartingEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String evento;
    private final EventoType type;

    public EventoStartingEvent(String evento, EventoType type) {
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
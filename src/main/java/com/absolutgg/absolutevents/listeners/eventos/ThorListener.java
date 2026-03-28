package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Thor;
import org.bukkit.event.Listener;

public final class ThorListener implements Listener {

    private Thor evento;

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Thor thor) {
            this.evento = thor;
        }
    }

    public Thor getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
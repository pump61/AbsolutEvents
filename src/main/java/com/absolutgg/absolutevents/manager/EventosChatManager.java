package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.eventos.chat.Bolao;
import com.absolutgg.absolutevents.eventos.chat.FastClick;
import com.absolutgg.absolutevents.eventos.chat.Loteria;
import com.absolutgg.absolutevents.eventos.chat.Matematica;
import com.absolutgg.absolutevents.eventos.chat.Palavra;
import com.absolutgg.absolutevents.eventos.chat.Sorteio;
import com.absolutgg.absolutevents.eventos.chat.Votacao;
import com.absolutgg.absolutevents.listeners.EventoChatListener;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;

public final class EventosChatManager {

    private EventoChat evento;
    private final EventoChatListener listener = new EventoChatListener();
    private boolean listenerRegistered;

    public boolean startEvento(EventoType type, YamlConfiguration config) {

        if (type == null || type == EventoType.NONE) {
            this.evento = null;
            unregisterListener();
            return true;
        }

        if (config == null) {
            return false;
        }

        if (evento != null) {
            return false;
        }

        if (!verify(config, type)) {
            return false;
        }

        EventoChat novoEvento = createEvento(type, config);

        if (novoEvento == null) {
            return false;
        }

        this.evento = novoEvento;

        try {
            registerListenerIfNeeded();
            this.evento.startCall();
            return true;
        } catch (Exception exception) {
            this.evento = null;
            unregisterListener();
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao iniciar o evento chat " + type.name() + ".");
            exception.printStackTrace();
            return false;
        }
    }

    public boolean startEvento(EventoType type, YamlConfiguration config, double reward) {
        if (config == null) {
            return false;
        }

        if (reward != -1) {
            config.set("custom_reward", reward);
        }

        return startEvento(type, config);
    }

    private EventoChat createEvento(EventoType type, YamlConfiguration config) {
        switch (type) {
            case VOTACAO:
                return new Votacao(config);

            case LOTERIA:
                return new Loteria(config);

            case BOLAO:
                return new Bolao(config);

            case MATEMATICA:
                return new Matematica(config);

            case PALAVRA:
                return new Palavra(config);

            case FAST_CLICK:
                return new FastClick(config);

            case SORTEIO:
                return new Sorteio(config);

            default:
                return null;
        }
    }

    private boolean verify(YamlConfiguration config, EventoType type) {
        switch (type) {
            case LOTERIA:
            case BOLAO:
            case MATEMATICA:
            case PALAVRA:
            case FAST_CLICK:
            case SORTEIO:
                return AbsolutEventsPlugin.getInstance().getEconomy() != null;

            case VOTACAO:
            default:
                return true;
        }
    }

    private void registerListenerIfNeeded() {
        if (listenerRegistered) {
            return;
        }

        AbsolutEventsPlugin.getInstance()
                .getServer()
                .getPluginManager()
                .registerEvents(listener, AbsolutEventsPlugin.getInstance());

        listenerRegistered = true;
    }

    public void unregisterListener() {
        if (!listenerRegistered) {
            return;
        }

        HandlerList.unregisterAll(listener);
        listenerRegistered = false;
    }

    public EventoChat getEvento() {
        return evento;
    }

    public boolean hasEventoRunning() {
        return evento != null;
    }

    public void stopEvento() {
        if (evento == null) {
            return;
        }

        EventoChat atual = evento;
        evento = null;

        try {
            atual.stop();
        } finally {
            unregisterListener();
        }
    }

    public void clearEvento() {
        this.evento = null;
        unregisterListener();
    }
}
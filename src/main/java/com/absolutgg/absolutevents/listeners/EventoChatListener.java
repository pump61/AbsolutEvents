package com.absolutgg.absolutevents.listeners;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class EventoChatListener implements Listener {

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!AbsolutEventsPlugin.getInstance().getEventoChatManager().hasEventoRunning()) {
            return;
        }

        Bukkit.getScheduler().runTask(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!AbsolutEventsPlugin.getInstance().getEventoChatManager().hasEventoRunning()) {
                        return;
                    }

                    if (AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento() == null) {
                        return;
                    }

                    AbsolutEventsPlugin.getInstance()
                            .getEventoChatManager()
                            .getEvento()
                            .parsePlayerMessage(event.getPlayer(), event.getMessage());
                }
        );
    }
}
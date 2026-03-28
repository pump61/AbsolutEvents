package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Fall;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class FallListener implements Listener {

    private Fall evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        Fall fall = getEvento();
        if (fall == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!fall.getPlayers().contains(player)) {
            return;
        }

        // impede morte real
        if ((player.getHealth() - event.getFinalDamage()) <= 0.0D) {
            event.setCancelled(true);

            fall.eliminate(player);

            PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                    player,
                    fall.getConfig().getString("filename", "").replace(".yml", ""),
                    fall.getType()
            );

            Bukkit.getPluginManager().callEvent(loseEvent);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Fall fall = getEvento();
        if (fall == null) {
            return;
        }

        if (!fall.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Fall fall = getEvento();
        if (fall == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!fall.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Fall fall) {
            this.evento = fall;
        }
    }

    private Fall getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
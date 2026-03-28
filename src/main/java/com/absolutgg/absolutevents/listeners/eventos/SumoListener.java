package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.eventos.Sumo;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class SumoListener implements Listener {

    private Sumo evento;

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Sumo sumo = getEvento();
        if (sumo == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!sumo.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Material type = event.getTo().getBlock().getType();

        if (type == Material.WATER
                || type == Material.LAVA
                || event.getTo().getY() <= sumo.getArenaMinY() - 3.0D) {
            sumo.eliminate(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Sumo sumo = getEvento();
        if (sumo == null) {
            return;
        }

        if (!sumo.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Sumo sumo = getEvento();
        if (sumo == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!sumo.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Sumo sumo = getEvento();
        if (sumo == null) {
            return;
        }

        Player player = event.getEntity();

        if (!sumo.getPlayers().contains(player)) {
            return;
        }

        sumo.eliminate(player);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Sumo sumo = getEvento();
        if (sumo == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged) || !(event.getDamager() instanceof Player damager)) {
            return;
        }

        if (!sumo.getPlayers().contains(damaged) || !sumo.getPlayers().contains(damager)) {
            return;
        }

        if (!sumo.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }
    }

    public void setEvento() {
        if (com.absolutgg.absolutevents.AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Sumo sumo) {
            this.evento = sumo;
        }
    }

    private Sumo getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
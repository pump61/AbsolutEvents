package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Fight;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class TorneioListener implements Listener {

    private Fight evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Fight fight = getEvento();
        if (fight == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player damager = getDamager(event);
        if (damager == null) {
            return;
        }

        if (fight.getSpectators().contains(damaged) || fight.getSpectators().contains(damager)) {
            event.setCancelled(true);
            return;
        }

        if (!fight.getPlayers().contains(damaged) || !fight.getPlayers().contains(damager)) {
            return;
        }

        Player fighter1 = fight.getFighter1();
        Player fighter2 = fight.getFighter2();

        if (fighter1 == null || fighter2 == null) {
            event.setCancelled(true);
            return;
        }

        boolean validFight =
                (damaged == fighter1 || damaged == fighter2)
                        && (damager == fighter1 || damager == fighter2);

        if (!validFight) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Fight fight = getEvento();
        if (fight == null) {
            return;
        }

        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        if (killer == null) {
            return;
        }

        if (!fight.getPlayers().contains(dead) || !fight.getPlayers().contains(killer)) {
            return;
        }

        Player fighter1 = fight.getFighter1();
        Player fighter2 = fight.getFighter2();

        if (fighter1 == null || fighter2 == null) {
            return;
        }

        boolean validFight =
                (dead == fighter1 || dead == fighter2)
                        && (killer == fighter1 || killer == fighter2);

        if (!validFight) {
            return;
        }

        event.getDrops().clear();
        event.setKeepLevel(true);
        fight.setFightLoser(dead);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Fight fight = getEvento();
        if (fight == null) {
            return;
        }

        if (!fight.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Fight fight = getEvento();
        if (fight == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!fight.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Fight fight = getEvento();
        if (fight == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!fight.getPlayers().contains(player)) {
            return;
        }

        Player fighter1 = fight.getFighter1();
        Player fighter2 = fight.getFighter2();

        if (fighter1 != player && fighter2 != player) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Material blockType = event.getTo().getBlock().getType();

        if (blockType == Material.WATER) {
            fight.setFightLoser(player);
        }
    }

    private Player getDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }

        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }

        return null;
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Fight fight) {
            this.evento = fight;
        }
    }

    private Fight getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
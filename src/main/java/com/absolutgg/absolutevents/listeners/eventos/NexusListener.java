package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Nexus;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class NexusListener implements Listener {

    private Nexus evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Nexus nexus = getEvento();
        if (nexus == null) {
            return;
        }

        Player attacker = getDamager(event);

        if (event.getEntity() instanceof EnderCrystal crystal) {
            event.setCancelled(true);

            if (attacker == null) {
                return;
            }

            boolean projectile = event.getDamager() instanceof Projectile;
            nexus.handleCrystalDamage(attacker, crystal, projectile);
            return;
        }

        if (!(event.getEntity() instanceof Player damaged) || attacker == null) {
            return;
        }

        if (!nexus.getPlayers().contains(damaged) || !nexus.getPlayers().contains(attacker)) {
            return;
        }

        if (nexus.getSpectators().contains(damaged) || nexus.getSpectators().contains(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (nexus.getDeadPlayers().contains(damaged) || nexus.getDeadPlayers().contains(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (nexus.getInvinciblePlayers().contains(damaged) || nexus.getInvinciblePlayers().contains(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (!nexus.isPvPEnabled()) {
            event.setCancelled(true);
            return;
        }

        boolean sameBlueTeam =
                nexus.getBlueTeam().containsKey(damaged) && nexus.getBlueTeam().containsKey(attacker);

        boolean sameRedTeam =
                nexus.getRedTeam().containsKey(damaged) && nexus.getRedTeam().containsKey(attacker);

        if (sameBlueTeam || sameRedTeam) {
            event.setCancelled(true);
            return;
        }

        if ((damaged.getHealth() - event.getFinalDamage()) <= 0.0D) {
            event.setCancelled(true);
            nexus.sendKillMessage(attacker, damaged);
            nexus.eliminate(damaged, attacker);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Nexus nexus = getEvento();
        if (nexus == null) {
            return;
        }

        Player player = event.getEntity();

        if (!nexus.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Nexus nexus = getEvento();
        if (nexus == null) {
            return;
        }

        if (!nexus.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Nexus nexus = getEvento();
        if (nexus == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!nexus.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Nexus nexus) {
            this.evento = nexus;
        }
    }

    private Nexus getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
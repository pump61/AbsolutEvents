package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Koth;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Random;

public final class KothListener implements Listener {

    private Koth evento;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!koth.getPlayers().contains(event.getPlayer())) {
            return;
        }

        if (koth.getDeadPlayers().contains(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();

            if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!koth.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player attacker = getDamager(event);
        if (attacker == null) {
            return;
        }

        if (!koth.getPlayers().contains(damaged) || !koth.getPlayers().contains(attacker)) {
            return;
        }

        if (!koth.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (koth.getInvinciblePlayers().contains(damaged)
                || koth.getDeadPlayers().contains(damaged)
                || koth.getDeadPlayers().contains(attacker)) {
            event.setCancelled(true);
            return;
        }

        if ((damaged.getHealth() - event.getFinalDamage()) <= 0) {
            event.setCancelled(true);
            koth.eliminate(damaged);

            double newHealth = Math.min(attacker.getMaxHealth(), attacker.getHealth() + koth.getHearts());
            attacker.setHealth(newHealth);
        }
    }

    @EventHandler
    public void onSuffocationDamage(EntityDamageEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!koth.getPlayers().contains(player) && !koth.getDeadPlayers().contains(player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING
                || event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION) {
            if (!koth.getRespawnLocations().isEmpty()) {
                Random random = new Random();
                player.teleport(
                        koth.getRespawnLocations().get(random.nextInt(koth.getRespawnLocations().size())),
                        PlayerTeleportEvent.TeleportCause.PLUGIN
                );
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!koth.getPlayers().contains(event.getEntity())) {
            return;
        }

        event.setCancelled(true);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        koth.eliminate(event.getEntity());

        Player killer = event.getEntity().getKiller();
        if (killer != null && koth.getPlayers().contains(killer)) {
            double newHealth = Math.min(killer.getMaxHealth(), killer.getHealth() + koth.getHearts());
            killer.setHealth(newHealth);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!koth.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Koth koth = getEvento();
        if (koth == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!koth.getPlayers().contains(player)) {
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Koth koth) {
            this.evento = koth;
        }
    }

    private Koth getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
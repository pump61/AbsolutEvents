package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.CorridaArmada;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class CorridaArmadaListener implements Listener {

    private CorridaArmada evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        CorridaArmada corridaArmada = getEvento();
        if (corridaArmada == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player attacker = getAttacker(event);
        if (attacker == null) {
            return;
        }

        if (!corridaArmada.getPlayers().contains(damaged) || !corridaArmada.getPlayers().contains(attacker)) {
            return;
        }

        if (!corridaArmada.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (corridaArmada.getInvinciblePlayers().contains(damaged)
                || corridaArmada.getDeadPlayers().contains(damaged)
                || corridaArmada.getInvinciblePlayers().contains(attacker)
                || corridaArmada.getDeadPlayers().contains(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        CorridaArmada corridaArmada = getEvento();
        if (corridaArmada == null) {
            return;
        }

        Player victim = event.getEntity();

        if (!corridaArmada.getPlayers().contains(victim)) {
            return;
        }

        Player killer = victim.getKiller();
        if (killer != null && corridaArmada.getPlayers().contains(killer)) {
            double newHealth = Math.min(killer.getMaxHealth(), killer.getHealth() + corridaArmada.getHearts());
            killer.setHealth(newHealth);
        } else {
            killer = null;
        }

        event.setCancelled(true);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        corridaArmada.eliminate(victim, killer);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        CorridaArmada corridaArmada = getEvento();
        if (corridaArmada == null) {
            return;
        }

        if (!corridaArmada.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        CorridaArmada corridaArmada = getEvento();
        if (corridaArmada == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!corridaArmada.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    private Player getAttacker(EntityDamageByEntityEvent event) {
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof CorridaArmada corridaArmada) {
            this.evento = corridaArmada;
        }
    }

    private CorridaArmada getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
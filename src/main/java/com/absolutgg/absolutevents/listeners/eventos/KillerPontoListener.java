package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.KillerPonto;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class KillerPontoListener implements Listener {

    private KillerPonto evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        KillerPonto killerPonto = getEvento();
        if (killerPonto == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!killerPonto.getPlayers().contains(damaged) || !killerPonto.getPlayers().contains(attacker)) {
            return;
        }

        if (!killerPonto.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (killerPonto.getInvinciblePlayers().contains(damaged)
                || killerPonto.getInvinciblePlayers().contains(attacker)
                || killerPonto.getDeadPlayers().contains(damaged)
                || killerPonto.getDeadPlayers().contains(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        KillerPonto killerPonto = getEvento();
        if (killerPonto == null) {
            return;
        }

        Player victim = event.getEntity();
        if (!killerPonto.getPlayers().contains(victim)) {
            return;
        }

        Player killer = victim.getKiller();
        if (killer == null || !killerPonto.getPlayers().contains(killer)) {
            return;
        }

        double newHealth = Math.min(killer.getMaxHealth(), killer.getHealth() + killerPonto.getHearts());
        killer.setHealth(newHealth);

        event.setCancelled(true);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        killerPonto.eliminate(victim, killer);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        KillerPonto killerPonto = getEvento();
        if (killerPonto == null) {
            return;
        }

        if (!killerPonto.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        KillerPonto killerPonto = getEvento();
        if (killerPonto == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!killerPonto.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof KillerPonto killerPonto) {
            this.evento = killerPonto;
        }
    }

    private KillerPonto getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
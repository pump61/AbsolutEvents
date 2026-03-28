package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.KillerPonto;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class KillerPontoListener implements Listener {

    private KillerPonto evento;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        KillerPonto killerPonto = getEvento();
        if (killerPonto == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player attacker = getDamager(event);
        if (attacker == null) {
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
            return;
        }

        if ((damaged.getHealth() - event.getFinalDamage()) > 0.0D) {
            return;
        }

        event.setCancelled(true);

        healKiller(attacker, killerPonto);
        preparePlayer(damaged);
        killerPonto.eliminate(damaged, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
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

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        if (killerPonto.getInvinciblePlayers().contains(player)
                || killerPonto.getDeadPlayers().contains(player)) {
            event.setCancelled(true);
            return;
        }

        if ((player.getHealth() - event.getFinalDamage()) > 0.0D) {
            return;
        }

        event.setCancelled(true);

        preparePlayer(player);
        killerPonto.eliminate(player, null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        KillerPonto killerPonto = getEvento();
        if (killerPonto == null) {
            return;
        }

        Player victim = event.getEntity();
        if (!killerPonto.getPlayers().contains(victim)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDeathMessage(null);
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

    private void healKiller(Player killer, KillerPonto killerPonto) {
        double maxHealth = 20.0D;
        if (killer.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
        }

        double newHealth = Math.min(maxHealth, killer.getHealth() + killerPonto.getHearts());
        killer.setHealth(Math.max(1.0D, newHealth));
    }

    private void preparePlayer(Player player) {
        double maxHealth = 20.0D;
        if (player.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        }

        player.setHealth(Math.max(1.0D, maxHealth));
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
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
package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Guerra;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class GuerraListener implements Listener {

    private Guerra evento;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Guerra guerra = getEvento();
        if (guerra == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player damager = getDamager(event);

        if (damager == null) {
            return;
        }

        if (guerra.getSpectators().contains(damaged) || guerra.getSpectators().contains(damager)) {
            event.setCancelled(true);
            return;
        }

        if (!guerra.getPlayers().contains(damaged) || !guerra.getPlayers().contains(damager)) {
            return;
        }

        if (!guerra.isPvPEnabled()) {
            event.setCancelled(true);
            return;
        }

        if ((damaged.getHealth() - event.getFinalDamage()) > 0.0D) {
            return;
        }

        event.setCancelled(true);

        guerra.getKills().merge(damager, 1, Integer::sum);

        preparePlayer(damaged);
        guerra.eliminate(damaged);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Guerra guerra = getEvento();
        if (guerra == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (guerra.getSpectators().contains(player)) {
            event.setCancelled(true);
            return;
        }

        if (!guerra.getPlayers().contains(player)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        if ((player.getHealth() - event.getFinalDamage()) > 0.0D) {
            return;
        }

        event.setCancelled(true);

        preparePlayer(player);
        guerra.eliminate(player);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Guerra guerra = getEvento();
        if (guerra == null) {
            return;
        }

        Player dead = event.getEntity();

        if (!guerra.getPlayers().contains(dead) && !guerra.getSpectators().contains(dead)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDeathMessage(null);
    }

    private void preparePlayer(Player player) {
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 20.0D;

        player.setHealth(Math.max(1.0D, Math.min(maxHealth, maxHealth)));
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Guerra guerra) {
            this.evento = guerra;
        }
    }

    private Guerra getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
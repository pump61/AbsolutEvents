package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Guerra;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class GuerraListener implements Listener {

    private Guerra evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Guerra guerra = getEvento();
        if (guerra == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player damager = null;

        if (event.getDamager() instanceof Player playerDamager) {
            damager = playerDamager;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player playerShooter) {
            damager = playerShooter;
        }

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
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Guerra guerra = getEvento();
        if (guerra == null) {
            return;
        }

        Player dead = event.getEntity();

        if (!guerra.getPlayers().contains(dead)) {
            return;
        }

        Player killer = dead.getKiller();

        if (killer != null) {
            guerra.getKills().merge(killer, 1, Integer::sum);
        }

        guerra.eliminate(dead);
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
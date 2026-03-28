package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Killer;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class KillerListener implements Listener {

    private Killer evento;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Killer killer = getEvento();
        if (killer == null || killer.isEnding()) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!killer.getPlayers().contains(damaged)) {
            return;
        }

        Player damager = getDamager(event);
        if (damager == null) {
            return;
        }

        if (!killer.getPlayers().contains(damager)) {
            return;
        }

        if (!killer.isPvPEnabled()) {
            event.setCancelled(true);
            return;
        }

        if ((damaged.getHealth() - event.getFinalDamage()) > 0.0D) {
            return;
        }

        event.setCancelled(true);
        eliminatePlayer(killer, damaged, damager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Killer killer = getEvento();
        if (killer == null || killer.isEnding()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!killer.getPlayers().contains(player)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        if ((player.getHealth() - event.getFinalDamage()) > 0.0D) {
            return;
        }

        event.setCancelled(true);
        eliminatePlayer(killer, player, null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Killer killer = getEvento();
        if (killer == null) {
            return;
        }

        Player dead = event.getEntity();

        if (!killer.getPlayers().contains(dead)) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.setDeathMessage(null);
    }

    private void eliminatePlayer(Killer killer, Player dead, Player killerPlayer) {
        if (killer.isEnding()) {
            return;
        }

        if (!killer.getPlayers().contains(dead)) {
            return;
        }

        if (killerPlayer != null && !killer.getPlayers().contains(killerPlayer)) {
            killerPlayer = null;
        }

        if (killerPlayer != null) {
            killer.getKills().merge(killerPlayer, 1, Integer::sum);
        }

        preparePlayer(dead);

        dead.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        killer.remove(dead);
        killer.leaveBungeecord(dead);

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                dead,
                killer.getConfig().getString("filename", "").replace(".yml", ""),
                killer.getType()
        );

        Bukkit.getPluginManager().callEvent(loseEvent);

        if (killer.isHappening() && killer.getPlayers().size() == 1) {
            killer.winner(killer.getPlayers().get(0));
        }
    }

    private void preparePlayer(Player player) {
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                ? player.getAttribute(Attribute.MAX_HEALTH).getValue()
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

        return shooterSafeNull(event);
    }

    private Player shooterSafeNull(EntityDamageByEntityEvent event) {
        return null;
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Killer killer) {
            this.evento = killer;
        }
    }

    private Killer getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
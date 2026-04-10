package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Killer;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;

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

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                dead,
                killer.getConfig().getString("filename", "").replace(".yml", ""),
                killer.getType()
        );
        Bukkit.getPluginManager().callEvent(loseEvent);

        killer.remove(dead);
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
        return resolveTrueAttacker(event.getDamager());
    }

    private Player resolveTrueAttacker(Entity entity) {
        if (entity == null) {
            return null;
        }

        if (entity instanceof Player player) {
            return player;
        }

        if (entity instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();

            if (shooter instanceof Player player) {
                return player;
            }

            if (shooter instanceof Entity shooterEntity) {
                Player resolved = resolveTrueAttacker(shooterEntity);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        if (entity instanceof TNTPrimed tnt) {
            Entity source = tnt.getSource();
            if (source instanceof Player player) {
                return player;
            }

            if (source != null) {
                Player resolved = resolveTrueAttacker(source);
                if (resolved != null) {
                    return resolved;
                }
            }
        }

        if (entity instanceof Tameable tameable) {
            if (tameable.getOwner() instanceof Player player) {
                return player;
            }
        }

        Player metadataResolved = resolveMetadataPlayer(entity, "caster");
        if (metadataResolved != null) {
            return metadataResolved;
        }

        metadataResolved = resolveMetadataPlayer(entity, "owner");
        if (metadataResolved != null) {
            return metadataResolved;
        }

        metadataResolved = resolveMetadataPlayer(entity, "player");
        if (metadataResolved != null) {
            return metadataResolved;
        }

        return null;
    }

    private Player resolveMetadataPlayer(Entity entity, String key) {
        if (!entity.hasMetadata(key)) {
            return null;
        }

        for (MetadataValue meta : entity.getMetadata(key)) {
            Object value = meta.value();

            if (value instanceof Player player) {
                return player;
            }

            if (value instanceof String stringValue) {
                Player byName = Bukkit.getPlayerExact(stringValue);
                if (byName != null) {
                    return byName;
                }
            }
        }

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
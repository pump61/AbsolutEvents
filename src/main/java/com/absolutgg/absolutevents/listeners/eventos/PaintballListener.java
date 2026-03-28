package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Paintball;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.scheduler.BukkitRunnable;

public final class PaintballListener implements Listener {

    private Paintball evento;

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Paintball paintball = getEvento();
        if (paintball == null) {
            return;
        }

        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }

        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }

        if (!paintball.getPlayers().contains(shooter)) {
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock() || arrow.isOnGround()) {
                    cancel();
                    return;
                }

                arrow.getWorld().spawnParticle(
                        Particle.DUST,
                        arrow.getLocation(),
                        2,
                        0.0, 0.0, 0.0, 0.0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 255), 1.0F)
                );
            }
        }.runTaskTimer(AbsolutEventsPlugin.getInstance(), 0L, 1L);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Paintball paintball = getEvento();
        if (paintball == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }

        if (!(projectile instanceof Arrow arrow)) {
            return;
        }

        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }

        if (!paintball.getPlayers().contains(damaged) || !paintball.getPlayers().contains(shooter)) {
            return;
        }

        if (!paintball.isPvPEnabled()) {
            event.setCancelled(true);
            if (arrow instanceof AbstractArrow abstractArrow) {
                abstractArrow.remove();
            }
            return;
        }

        boolean sameBlueTeam = paintball.getBlueTeam().contains(damaged) && paintball.getBlueTeam().contains(shooter);
        boolean sameRedTeam = paintball.getRedTeam().contains(damaged) && paintball.getRedTeam().contains(shooter);

        if (sameBlueTeam || sameRedTeam) {
            event.setCancelled(true);
            if (arrow instanceof AbstractArrow abstractArrow) {
                abstractArrow.remove();
            }
            return;
        }

        event.setCancelled(true);

        damaged.getWorld().spawnParticle(Particle.FIREWORK, damaged.getLocation().add(0, 1, 0), 20, 0.25, 0.45, 0.25, 0.02);
        shooter.playSound(shooter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.8F);
        damaged.playSound(damaged.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8F, 1.4F);

        if (arrow instanceof AbstractArrow abstractArrow) {
            abstractArrow.remove();
        }

        paintball.registerElimination(shooter, damaged);
        paintball.eliminate(damaged);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Paintball paintball = getEvento();
        if (paintball == null) {
            return;
        }

        if (!paintball.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Paintball paintball = getEvento();
        if (paintball == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!paintball.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Paintball paintball = getEvento();
        if (paintball == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!paintball.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Paintball paintball) {
            this.evento = paintball;
        }
    }

    private Paintball getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
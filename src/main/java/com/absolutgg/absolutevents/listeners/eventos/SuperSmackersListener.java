package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.SuperSmackers;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class SuperSmackersListener implements Listener {

    private SuperSmackers evento;

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        SuperSmackers smackers = getEvento();
        if (smackers == null || !smackers.isHappening()) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!smackers.getPlayers().contains(attacker) || !smackers.getPlayers().contains(victim)) {
            return;
        }

        if (!smackers.isPvPEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (!attacker.equals(smackers.getCurrentP1()) && !attacker.equals(smackers.getCurrentP2())) {
            event.setCancelled(true);
            return;
        }

        if (!victim.equals(smackers.getCurrentP1()) && !victim.equals(smackers.getCurrentP2())) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        Vector direction = victim.getLocation().toVector()
                .subtract(attacker.getLocation().toVector())
                .normalize();

        direction.multiply(smackers.getKnockbackHorizontal());
        direction.setY(smackers.getKnockbackVertical());

        victim.setVelocity(direction);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
    }

    @EventHandler
    public void onGenericDamage(EntityDamageEvent event) {
        SuperSmackers smackers = getEvento();
        if (smackers == null || !smackers.isHappening()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!smackers.getPlayers().contains(player)) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            smackers.handleRoundLose(player);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        SuperSmackers smackers = getEvento();
        if (smackers == null || !smackers.isHappening()) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.equals(smackers.getCurrentP1()) && !player.equals(smackers.getCurrentP2())) {
            return;
        }

        if (!smackers.isRoundStarting()) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onPad(PlayerMoveEvent event) {
        SuperSmackers smackers = getEvento();
        if (smackers == null || !smackers.isHappening() || !smackers.isPvPEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.equals(smackers.getCurrentP1()) && !player.equals(smackers.getCurrentP2())) {
            return;
        }

        Material below = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();

        if (smackers.isJumpPad(below)) {
            if (!smackers.canUseJumpPad(player)) {
                return;
            }

            smackers.markJumpPadUse(player);

            Vector vector = player.getVelocity().clone();
            vector.setY(smackers.getJumpPower());
            player.setVelocity(vector);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.1F);
            return;
        }

        if (smackers.isSpeedPad(below)) {
            if (!smackers.canUseSpeedPad(player)) {
                return;
            }

            smackers.markSpeedPadUse(player);

            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED,
                    smackers.getSpeedDuration() * 20,
                    Math.max(0, smackers.getSpeedAmplifier() - 1),
                    false,
                    false,
                    true
            ));

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        SuperSmackers smackers = getEvento();
        if (smackers == null || !smackers.isHappening()) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        if (!player.equals(smackers.getCurrentP1()) && !player.equals(smackers.getCurrentP2())) {
            return;
        }

        if (smackers.isRoundStarting()) {
            event.setCancelled(true);
        }
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof SuperSmackers smackers) {
            this.evento = smackers;
        }
    }

    private SuperSmackers getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
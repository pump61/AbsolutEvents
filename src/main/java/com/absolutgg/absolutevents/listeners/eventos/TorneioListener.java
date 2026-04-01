package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Torneio;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class TorneioListener implements Listener {

    private Torneio evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Torneio torneio = getEvento();
        if (torneio == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        Player damager = getDamager(event);
        if (damager == null) {
            return;
        }

        if (torneio.getSpectators().contains(damaged) || torneio.getSpectators().contains(damager)) {
            event.setCancelled(true);
            return;
        }

        if (!torneio.getPlayers().contains(damaged) || !torneio.getPlayers().contains(damager)) {
            return;
        }

        Player fighter1 = torneio.getFighter1();
        Player fighter2 = torneio.getFighter2();

        if (fighter1 == null || fighter2 == null) {
            event.setCancelled(true);
            return;
        }

        boolean validFight =
                (damaged == fighter1 || damaged == fighter2)
                        && (damager == fighter1 || damager == fighter2);

        if (!validFight) {
            event.setCancelled(true);
            return;
        }

        double finalDamage = event.getFinalDamage();
        double remainingHealth = damaged.getHealth() - finalDamage;

        if (remainingHealth <= 0.0D) {
            event.setCancelled(true);
            torneio.fightWinner(damager);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGenericDamage(EntityDamageEvent event) {
        Torneio torneio = getEvento();
        if (torneio == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!torneio.getPlayers().contains(damaged)) {
            return;
        }

        Player fighter1 = torneio.getFighter1();
        Player fighter2 = torneio.getFighter2();

        if (fighter1 == null || fighter2 == null) {
            return;
        }

        if (damaged != fighter1 && damaged != fighter2) {
            return;
        }

        double finalDamage = event.getFinalDamage();
        double remainingHealth = damaged.getHealth() - finalDamage;

        if (remainingHealth > 0.0D) {
            return;
        }

        event.setCancelled(true);

        Player winner = damaged == fighter1 ? fighter2 : fighter1;
        torneio.fightWinner(winner);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Torneio torneio = getEvento();
        if (torneio == null) {
            return;
        }

        if (!torneio.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Torneio torneio = getEvento();
        if (torneio == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!torneio.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Torneio torneio = getEvento();
        if (torneio == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!torneio.getPlayers().contains(player)) {
            return;
        }

        Player fighter1 = torneio.getFighter1();
        Player fighter2 = torneio.getFighter2();

        if (fighter1 != player && fighter2 != player) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Material blockType = event.getTo().getBlock().getType();

        if (blockType == Material.WATER || blockType == Material.LAVA) {
            Player winner = player == fighter1 ? fighter2 : fighter1;
            torneio.fightWinner(winner);
        }
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Torneio torneio) {
            this.evento = torneio;
        }
    }

    private Torneio getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
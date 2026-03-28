package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Hunter;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class HunterListener implements Listener {

    private Hunter evento;

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {

        Hunter hunter = getEvento();
        if (hunter == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!(event.getDamager() instanceof Arrow arrow)) {
            return;
        }

        if (!(arrow.getShooter() instanceof Player shooter)) {
            return;
        }

        if (!hunter.getPlayers().contains(damaged) || !hunter.getPlayers().contains(shooter)) {
            return;
        }

        // cancelamos dano real
        event.setCancelled(true);

        // bloqueia se capturado
        if (hunter.getCapturedPlayers().contains(damaged) || hunter.getCapturedPlayers().contains(shooter)) {
            return;
        }

        // bloqueia antes do PvP
        if (!hunter.isPvpEnabled()) {
            return;
        }

        // bloqueia invencibilidade
        if (hunter.getInvinciblePlayers().contains(damaged) || hunter.getInvinciblePlayers().contains(shooter)) {
            return;
        }

        boolean sameBlue = hunter.getBlueTeam().containsKey(damaged) && hunter.getBlueTeam().containsKey(shooter);
        boolean sameRed = hunter.getRedTeam().containsKey(damaged) && hunter.getRedTeam().containsKey(shooter);

        if (sameBlue || sameRed) {
            return;
        }

        // captura
        hunter.eliminate(damaged, shooter);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Hunter hunter = getEvento();
        if (hunter == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!hunter.getPlayers().contains(player)) {
            return;
        }

        if (!hunter.getCapturedPlayers().contains(player)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) {
            return;
        }

        // evita cálculos desnecessários
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());

        event.setTo(locked);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {

        Hunter hunter = getEvento();
        if (hunter == null) {
            return;
        }

        if (!hunter.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {

        Hunter hunter = getEvento();
        if (hunter == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!hunter.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        Hunter hunter = getEvento();
        if (hunter == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!hunter.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Hunter hunter) {
            this.evento = hunter;
        }
    }

    private Hunter getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.BatataQuente;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class BatataQuenteListener implements Listener {

    private BatataQuente evento;

    @EventHandler
    public void onPotatoHit(EntityDamageByEntityEvent event) {
        BatataQuente batataQuente = getEvento();
        if (batataQuente == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged) || !(event.getDamager() instanceof Player damager)) {
            return;
        }

        if (!batataQuente.getPlayers().contains(damager) || !batataQuente.getPlayers().contains(damaged)) {
            return;
        }

        if (batataQuente.getPotatoHolder() == null) {
            return;
        }

        if (!batataQuente.getPotatoHolder().getUniqueId().equals(damager.getUniqueId())) {
            return;
        }

        Material potatoType = XMaterial.POTATO.parseMaterial();
        if (potatoType == null) {
            potatoType = Material.POTATO;
        }

        if (damager.getInventory().getItemInMainHand().getType() != potatoType) {
            return;
        }

        event.setCancelled(true);

        batataQuente.setHolder(damaged, batataQuente.getPotatoHolderChanges());

        damager.getInventory().clear();
        damager.getInventory().setHelmet(null);
        damager.updateInventory();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        BatataQuente batataQuente = getEvento();
        if (batataQuente == null) {
            return;
        }

        if (!batataQuente.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        BatataQuente batataQuente = getEvento();
        if (batataQuente == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!batataQuente.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        BatataQuente batataQuente = getEvento();
        if (batataQuente == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!batataQuente.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof BatataQuente batataQuente) {
            this.evento = batataQuente;
        }
    }

    private BatataQuente getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
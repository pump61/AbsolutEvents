package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.BlockParty;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class BlockPartyListener implements Listener {

    private BlockParty evento;

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        Player player = event.getPlayer();
        if (!blockParty.getPlayers().contains(player)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        Player player = event.getPlayer();
        if (!blockParty.getPlayers().contains(player)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        if (!(event.getEntity() instanceof Player player)) return;
        if (!blockParty.getPlayers().contains(player)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!blockParty.getPlayers().contains(player)) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        Player player = event.getPlayer();
        if (!blockParty.getPlayers().contains(player)) return;
        if (!blockParty.isHappening()) return;
        if (event.getTo() == null) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        if (event.getTo().getY() < blockParty.getArenaMinY() - 2) {
            blockParty.eliminate(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        Player player = event.getPlayer();
        if (!blockParty.getPlayers().contains(player)) return;

        blockParty.eliminate(player);
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        BlockParty blockParty = getEvento();
        if (blockParty == null) return;

        Player player = event.getPlayer();
        if (!blockParty.getPlayers().contains(player)) return;

        blockParty.eliminate(player);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof BlockParty blockParty) {
            this.evento = blockParty;
        }
    }

    private BlockParty getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
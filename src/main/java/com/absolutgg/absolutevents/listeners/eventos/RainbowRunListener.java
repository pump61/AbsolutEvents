package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.RainbowRun;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Map;

public final class RainbowRunListener implements Listener {

    private RainbowRun evento;
    private final Map<Block, RainbowRun.RainbowPlayer> conqueredBlocks = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        RainbowRun rainbowRun = getEvento();
        if (rainbowRun == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!rainbowRun.getPlayers().contains(player) || !rainbowRun.isStarted()) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        RainbowRun.RainbowPlayer conqueror = rainbowRun.getRainbowPlayer(player);
        if (conqueror == null) {
            return;
        }

        Block steppedBlock = event.getTo().getBlock().getRelative(BlockFace.DOWN);

        if (!rainbowRun.getCuboid().isIn(steppedBlock.getLocation())) {
            return;
        }

        if (steppedBlock.getType() == conqueror.getMaterial().parseMaterial()) {
            return;
        }

        RainbowRun.RainbowPlayer previousOwner = conqueredBlocks.get(steppedBlock);
        if (previousOwner != null && previousOwner != conqueror) {
            previousOwner.decrementPoints();
            previousOwner.getConqueredBlocks().remove(steppedBlock);
            conqueredBlocks.remove(steppedBlock);
        }

        if (previousOwner == conqueror) {
            return;
        }

        conqueredBlocks.put(steppedBlock, conqueror);
        conqueror.incrementPoints();
        conqueror.getConqueredBlocks().add(steppedBlock);

        Bukkit.getScheduler().runTask(AbsolutEventsPlugin.getInstance(), () -> {
            if (!rainbowRun.isHappening()) {
                return;
            }

            if (conqueror.getMaterial().parseMaterial() != null) {
                steppedBlock.setType(conqueror.getMaterial().parseMaterial());
            }
        });
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        RainbowRun rainbowRun = getEvento();
        if (rainbowRun == null) {
            return;
        }

        if (!rainbowRun.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        RainbowRun rainbowRun = getEvento();
        if (rainbowRun == null) {
            return;
        }

        if (!rainbowRun.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        RainbowRun rainbowRun = getEvento();
        if (rainbowRun == null) {
            return;
        }

        if (!rainbowRun.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        RainbowRun rainbowRun = getEvento();
        if (rainbowRun == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!rainbowRun.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public Map<Block, RainbowRun.RainbowPlayer> getConqueredBlocks() {
        return conqueredBlocks;
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof RainbowRun rainbowRun) {
            this.evento = rainbowRun;
        }
    }

    private RainbowRun getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
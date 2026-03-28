package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Spleef;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpleefListener implements Listener {

    private Spleef evento;
    private final Map<UUID, Long> lastMove = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Spleef spleef = getEvento();
        if (spleef == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!spleef.getPlayers().contains(player)) {
            return;
        }

        Material type = event.getBlock().getType();
        Material snowBlock = XMaterial.SNOW_BLOCK.parseMaterial();
        Material snow = XMaterial.SNOW.parseMaterial();

        boolean validSnow =
                (snowBlock != null && type == snowBlock) ||
                (snow != null && type == snow);

        if (!validSnow) {
            event.setCancelled(true);
            return;
        }

        if (spleef.canNotBreakBlocks()) {
            event.setCancelled(true);
            return;
        }

        // força a quebra
        event.setCancelled(false);
        event.setDropItems(false);
        event.setExpToDrop(0);
        event.getBlock().setType(Material.AIR, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamageBlock(BlockDamageEvent event) {
        Spleef spleef = getEvento();
        if (spleef == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!spleef.getPlayers().contains(player)) {
            return;
        }

        Material type = event.getBlock().getType();
        Material snowBlock = XMaterial.SNOW_BLOCK.parseMaterial();
        Material snow = XMaterial.SNOW.parseMaterial();

        boolean validSnow =
                (snowBlock != null && type == snowBlock) ||
                (snow != null && type == snow);

        if (!validSnow) {
            return;
        }

        if (spleef.canNotBreakBlocks()) {
            event.setCancelled(true);
            return;
        }

        // quebra instantânea mesmo com proteção enchendo o saco
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR, false);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Spleef spleef = getEvento();
        if (spleef == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!spleef.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        Material blockType = event.getTo().getBlock().getType();

        if (blockType == Material.WATER || blockType == Material.LAVA || event.getTo().getY() <= spleef.getArenaMinY() - 3.0D) {
            spleef.eliminate(player);
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        lastMove.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Spleef spleef = getEvento();
        if (spleef == null) {
            return;
        }

        if (!spleef.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Spleef spleef = getEvento();
        if (spleef == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged) || !(event.getDamager() instanceof Player damager)) {
            return;
        }

        if (spleef.getSpectators().contains(damaged) || spleef.getSpectators().contains(damager)) {
            event.setCancelled(true);
            return;
        }

        if (!spleef.getPlayers().contains(damaged) || !spleef.getPlayers().contains(damager)) {
            return;
        }

        event.setCancelled(true);
        event.setDamage(0.0D);
    }

    public Map<UUID, Long> getLastMove() {
        return lastMove;
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Spleef spleef) {
            this.evento = spleef;
        }
    }

    private Spleef getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
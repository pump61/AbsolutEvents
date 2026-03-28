package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.BattleRoyale;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class BattleRoyaleListener implements Listener {

    private BattleRoyale evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!br.getPlayers().contains(damaged)) {
            return;
        }

        if (!br.isPvPEnabled()) {
            event.setCancelled(true);
            return;
        }

        if ((damaged.getHealth() - event.getFinalDamage()) <= 0.0D) {
            event.setCancelled(true);

            Player killer = getDamager(event);
            br.eliminate(damaged, killer);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGenericDamage(EntityDamageEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        if ((player.getHealth() - event.getFinalDamage()) <= 0.0D) {
            event.setCancelled(true);
            br.eliminate(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getEntity();

        if (!br.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        br.eliminate(player, player.getKiller());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (!br.isInsideMapRegion(event.getBlockPlaced())) {
            return;
        }

        if (br.removePlayerPlacedBlocks()) {
            br.getBlocksToRemove().add(event.getBlockPlaced());
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (!br.isInsideMapRegion(event.getBlock())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!br.getPlayers().contains(player)) {
            return;
        }

        Block target = event.getBlockClicked().getRelative(event.getBlockFace());

        if (!br.isInsideMapRegion(target)) {
            return;
        }

        if (br.removePlayerPlacedBlocks()) {
            br.getBlocksToRemove().add(target);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (!br.isInsideMapRegion(event.getBlock())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onLiquidSpread(BlockFromToEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!br.isInsideMapRegion(event.getBlock()) && !br.isInsideMapRegion(event.getToBlock())) {
            return;
        }

        if (!br.removePlayerPlacedBlocks()) {
            event.setCancelled(true);
            return;
        }

        if (!br.getBlocksToRemove().contains(event.getBlock())
                && !br.getBlocksToRemove().contains(event.getToBlock())) {
            event.setCancelled(true);
            return;
        }

        br.getBlocksToRemove().add(event.getToBlock());

        for (BlockFace face : BlockFace.values()) {
            Block relative = event.getToBlock().getRelative(face);
            if (relative.isLiquid()) {
                br.getBlocksToRemove().add(relative);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onIgnite(BlockIgniteEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!br.isInsideMapRegion(event.getBlock())) {
            return;
        }

        if (!br.removePlayerPlacedBlocks()) {
            event.setCancelled(true);
            return;
        }

        if (event.getPlayer() != null) {
            if (!br.getPlayers().contains(event.getPlayer())) {
                return;
            }

            if (!br.getBlocksToRemove().contains(event.getBlock())) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getIgnitingBlock() != null
                && br.getBlocksToRemove().contains(event.getIgnitingBlock())
                && !br.getBlocksToRemove().contains(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!br.isInsideMapRegion(event.getBlock())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        event.blockList().removeIf(br::isInsideMapRegion);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        event.blockList().removeIf(br::isInsideMapRegion);
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof BattleRoyale br) {
            this.evento = br;
        }
    }

    private BattleRoyale getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
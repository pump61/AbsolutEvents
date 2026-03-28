package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.BattleRoyale;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
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

        // impede morte real por PvP
        if ((damaged.getHealth() - event.getFinalDamage()) <= 0.0D) {
            event.setCancelled(true);

            Player killer = null;
            if (event.getDamager() instanceof Player player) {
                killer = player;
            }

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

        // PvP já é tratado em outro evento
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }

        // impede morte real por qualquer outra fonte
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

        // segurança extra caso algo passe pelas checagens de dano
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        br.eliminate(player, player.getKiller());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) {
            return;
        }

        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (!br.removePlayerPlacedBlocks()) {
            return;
        }

        br.getBlocksToRemove().add(event.getBlockPlaced());
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.isCancelled()) {
            return;
        }

        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (!br.removePlayerPlacedBlocks()) {
            return;
        }

        if (!br.getBlocksToRemove().contains(event.getBlock())) {
            event.setCancelled(true);
        }
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

        // água não elimina mais
    }

    @EventHandler
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (event.isCancelled()) {
            return;
        }

        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!br.getPlayers().contains(player)) {
            return;
        }

        if (!br.removePlayerPlacedBlocks()) {
            return;
        }

        br.getBlocksToRemove().add(
                event.getBlockClicked().getRelative(event.getBlockFace())
        );
    }

    @EventHandler
    public void onLiquidSpread(BlockFromToEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!br.getBlocksToRemove().contains(event.getBlock())
                && !br.getBlocksToRemove().contains(event.getToBlock())) {
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
        if (event.isCancelled()) {
            return;
        }

        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        if (!br.removePlayerPlacedBlocks()) {
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
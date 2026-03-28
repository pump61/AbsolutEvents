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

        // permitido normalmente
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

        // permitido normalmente
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

        // permitido normalmente
    }

    @EventHandler
    public void onLiquidSpread(BlockFromToEvent event) {
        BattleRoyale br = getEvento();
        if (br == null) {
            return;
        }

        // com restauração por schematic/FAWE não precisa rastrear líquidos
        Block from = event.getBlock();
        Block to = event.getToBlock();

        if (from == null || to == null) {
            return;
        }

        for (BlockFace face : BlockFace.values()) {
            Block relative = to.getRelative(face);
            if (relative == null) {
                continue;
            }

            if (relative.isLiquid()) {
                // sem rastreamento manual
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

        if (event.getPlayer() != null) {
            if (!br.getPlayers().contains(event.getPlayer())) {
                return;
            }
        }

        // permitido normalmente
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
package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.TNTRun;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.Set;

public final class TNTRunListener implements Listener {

    private TNTRun evento;
    private final Set<String> queuedBlocks = new HashSet<>();

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        Player player = event.getPlayer();
        if (!tntRun.getPlayers().contains(player)) return;
        if (!tntRun.isTntRunHappening() || !tntRun.isHappening() || event.getTo() == null) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        Material toType = to.getBlock().getType();
        boolean inWater = toType == XMaterial.WATER.parseMaterial();
        boolean fellBelowArena = to.getY() < Math.min(
                tntRun.getCuboid().getPoint1().getY(),
                tntRun.getCuboid().getPoint2().getY()
        ) - 3;

        if (inWater || fellBelowArena) {
            eliminate(player, tntRun);
            return;
        }

        Block currentUnder = to.getBlock().getRelative(BlockFace.DOWN);
        forceScheduleBreak(currentUnder);

        if (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ()) {
            Block previousUnder = from.getBlock().getRelative(BlockFace.DOWN);
            if (!sameBlock(previousUnder, currentUnder)) {
                forceScheduleBreak(previousUnder);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        if (!tntRun.getPlayers().contains(event.getPlayer())) return;

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        if (!tntRun.getPlayers().contains(event.getPlayer())) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        if (!tntRun.getPlayers().contains(event.getPlayer())) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        if (!(event.getEntity() instanceof Player attacked) || !(event.getDamager() instanceof Player damager)) return;

        if (tntRun.getPlayers().contains(attacked) && tntRun.getPlayers().contains(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        if (!(event.getEntity() instanceof Player player)) return;

        if (tntRun.getPlayers().contains(player) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;

        if (event.getEntityType() != EntityType.TNT) return;

        if (tntRun.getCuboid().isIn(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    public void forceScheduleBreak(Block topBlock) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) return;
        if (topBlock == null) return;
        if (!tntRun.isHappening() || !tntRun.isTntRunHappening()) return;
        if (!tntRun.getCuboid().isIn(topBlock.getLocation())) return;
        if (!tntRun.getTriggers().contains(topBlock.getType())) return;

        String key = blockKey(topBlock);
        if (!queuedBlocks.add(key)) {
            return;
        }

        Block below = topBlock.getRelative(BlockFace.DOWN);

        tntRun.getScheduler().runTaskLater(AbsolutEventsPlugin.getInstance(), () -> {
            try {
                if (!tntRun.isHappening()) return;

                if (tntRun.getTriggers().contains(topBlock.getType())) {
                    topBlock.setType(Material.AIR, false);
                }

                if (below.getType() == XMaterial.TNT.parseMaterial()) {
                    below.setType(Material.AIR, false);
                }
            } finally {
                queuedBlocks.remove(key);
            }
        }, tntRun.getDelay());
    }

    private void eliminate(Player player, TNTRun tntRun) {
        if (!tntRun.getPlayers().contains(player)) return;

        player.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance().getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        tntRun.remove(player);
        tntRun.notifyLeave(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                tntRun.getConfig().getString("filename", "").replace(".yml", ""),
                tntRun.getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        if (tntRun.isHappening() && tntRun.getPlayers().size() == 1) {
            tntRun.winner(tntRun.getPlayers().get(0));
        }
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof TNTRun tntRun) {
            this.evento = tntRun;
        }
    }

    private TNTRun getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }

    private boolean sameBlock(Block a, Block b) {
        if (a == null || b == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getX() == b.getX()
                && a.getY() == b.getY()
                && a.getZ() == b.getZ();
    }

    private String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
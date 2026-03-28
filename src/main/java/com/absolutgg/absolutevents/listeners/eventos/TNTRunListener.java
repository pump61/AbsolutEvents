package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.TNTRun;
import com.cryptomorin.xseries.XMaterial;
import com.iridium.iridiumcolorapi.IridiumColorAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class TNTRunListener implements Listener {

    private TNTRun evento;

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!tntRun.getPlayers().contains(player)) {
            return;
        }

        if (!tntRun.isTntRunHappening() || !tntRun.isHappening() || event.getTo() == null) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Block fromBlockUnder = from.getBlock().getRelative(BlockFace.DOWN);

        if (tntRun.getTriggers().contains(fromBlockUnder.getType())) {
            Block below = fromBlockUnder.getRelative(BlockFace.DOWN);

            if (below.getType() == XMaterial.TNT.parseMaterial()) {
                tntRun.getScheduler().runTaskLater(AbsolutEventsPlugin.getInstance(), () -> {
                    if (!tntRun.isHappening()) {
                        return;
                    }

                    if (fromBlockUnder.getType() != Material.AIR) {
                        fromBlockUnder.setType(Material.AIR, false);
                    }

                    if (below.getType() != Material.AIR) {
                        below.setType(Material.AIR, false);
                    }
                }, tntRun.getDelay());
            }
        }

        Material toType = to.getBlock().getType();
        boolean inWater = toType == XMaterial.WATER.parseMaterial();
        boolean fellBelowArena = to.getY() < Math.min(
                tntRun.getCuboid().getPoint1().getY(),
                tntRun.getCuboid().getPoint2().getY()
        ) - 3;

        if (inWater || fellBelowArena) {
            eliminate(player, tntRun);
        }
    }

    private void eliminate(Player player, TNTRun tntRun) {
        if (!tntRun.getPlayers().contains(player)) {
            return;
        }

        player.sendMessage(IridiumColorAPI.process(
                AbsolutEventsPlugin.getInstance().getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
                        .replace("&", "§")
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

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) {
            return;
        }

        if (!tntRun.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player attacked) || !(event.getDamager() instanceof Player damager)) {
            return;
        }

        if (tntRun.getPlayers().contains(attacked) && tntRun.getPlayers().contains(damager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (tntRun.getPlayers().contains(player) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        TNTRun tntRun = getEvento();
        if (tntRun == null) {
            return;
        }

        if (event.getEntityType() != EntityType.TNT) {
            return;
        }

        if (tntRun.getCuboid().isIn(event.getLocation())) {
            event.setCancelled(true);
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
}
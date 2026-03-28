package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Splegg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;

import java.util.HashMap;
import java.util.Map;

public final class SpleggListener implements Listener {

    private Splegg evento;
    private final Map<Player, Long> lastMove = new HashMap<>();

    @EventHandler
    public void onThrowEgg(ProjectileLaunchEvent event) {
        Splegg splegg = getEvento();
        if (splegg == null) {
            return;
        }

        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return;
        }

        if (!(event.getEntity() instanceof Egg)) {
            return;
        }

        if (!splegg.getPlayers().contains(shooter)) {
            return;
        }

        if (!splegg.isStarted()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEggHit(ProjectileHitEvent event) {
        Splegg splegg = getEvento();
        if (splegg == null || !splegg.isStarted() || !(event.getEntity() instanceof Egg egg)) {
            return;
        }

        Block hitBlock = event.getHitBlock();

        if (hitBlock == null) {
            BlockIterator iterator = new BlockIterator(
                    egg.getWorld(),
                    egg.getLocation().toVector(),
                    egg.getVelocity().normalize(),
                    0,
                    4
            );

            while (iterator.hasNext()) {
                Block next = iterator.next();
                if (next.getType() != Material.AIR) {
                    hitBlock = next;
                    break;
                }
            }
        }

        if (hitBlock == null) {
            return;
        }

        if (!splegg.isInsideArena(hitBlock)) {
            return;
        }

        if (!splegg.getBreakableMaterials().contains(hitBlock.getType())) {
            return;
        }

        hitBlock.setType(Material.AIR);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Splegg splegg = getEvento();
        if (splegg == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!splegg.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        Material blockType = event.getTo().getBlock().getType();

        if (blockType == Material.WATER
                || blockType == Material.LAVA
                || event.getTo().getY() <= splegg.getArenaMinY() - 3.0D) {
            splegg.eliminate(player);
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        lastMove.put(player, System.currentTimeMillis());
    }

    @EventHandler
    public void onEggHatching(PlayerEggThrowEvent event) {
        Splegg splegg = getEvento();
        if (splegg == null) {
            return;
        }

        if (!splegg.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.getPlayer().getInventory().addItem(new ItemStack(Material.EGG, 1));
        event.setHatching(false);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Splegg splegg = getEvento();
        if (splegg == null) {
            return;
        }

        if (!splegg.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onSwapHandItem(PlayerSwapHandItemsEvent event) {
        Splegg splegg = getEvento();
        if (splegg == null) {
            return;
        }

        if (!splegg.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    public Map<Player, Long> getLastMove() {
        return lastMove;
    }

    public void clearLastMove() {
        lastMove.clear();
    }

    public void removeLastMove(Player player) {
        lastMove.remove(player);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Splegg splegg) {
            this.evento = splegg;
        }
    }

    private Splegg getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
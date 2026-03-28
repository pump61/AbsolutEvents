package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Frog;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class FrogListener implements Listener {

    private Frog evento;
    private Block wool;

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Frog frog = getEvento();
        if (frog == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!frog.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        Block targetWool = getWool();
        Block below = player.getLocation().clone().subtract(0, 1, 0).getBlock();

        if (targetWool != null && below.equals(targetWool)) {
            frog.winner(player);
            return;
        }

        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Material blockType = event.getTo().getBlock().getType();

        if (blockType == Material.WATER) {
            player.sendMessage(ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance()
                            .getConfig()
                            .getString("Messages.Eliminated", "&cVocê foi eliminado.")
            ));

            frog.leave(player);
            return;
        }

        if (targetWool != null && below.equals(targetWool)) {
            frog.winner(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Frog frog = getEvento();
        if (frog == null) {
            return;
        }

        if (!frog.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Frog frog = getEvento();
        if (frog == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!frog.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Frog frog) {
            this.evento = frog;
        }
    }

    public void setWool() {
        Frog frog = getEvento();
        if (frog != null) {
            this.wool = frog.getWoolBlock();
        }
    }

    private Frog getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }

    private Block getWool() {
        if (wool == null) {
            setWool();
        }
        return wool;
    }
}
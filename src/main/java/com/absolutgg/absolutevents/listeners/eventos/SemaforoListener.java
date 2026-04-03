package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Semaforo;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

public final class SemaforoListener implements Listener {

    private Semaforo evento;

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Semaforo semaforo = getEvento();
        if (semaforo == null) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        if (!semaforo.getPlayers().contains(player)) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (!block.getType().name().contains("SIGN")) {
            return;
        }

        if (!(block.getState() instanceof Sign sign)) {
            return;
        }

        List<String> victory = semaforo.getConfig().getStringList("Messages.Sign");
        if (victory.size() < 4) {
            return;
        }

        String line0 = ColorUtils.strip(sign.getLine(0)).trim();
        String line1 = ColorUtils.strip(sign.getLine(1)).trim();
        String line2 = ColorUtils.strip(sign.getLine(2)).trim();
        String line3 = ColorUtils.strip(sign.getLine(3)).trim();

        String expected0 = ColorUtils.strip(victory.get(0)).trim();
        String expected1 = ColorUtils.strip(victory.get(1)).trim();
        String expected2 = ColorUtils.strip(victory.get(2)).trim();
        String expected3 = ColorUtils.strip(victory.get(3)).trim();

        if (!line0.equalsIgnoreCase(expected0)) {
            return;
        }

        if (!line1.equalsIgnoreCase(expected1)) {
            return;
        }

        if (!line2.equalsIgnoreCase(expected2)) {
            return;
        }

        if (!line3.equalsIgnoreCase(expected3)) {
            return;
        }

        semaforo.winner(player);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Semaforo semaforo = getEvento();
        if (semaforo == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!semaforo.getPlayers().contains(player)) {
            return;
        }

        if (semaforo.canWalk()) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        boolean moved =
                event.getFrom().getBlockX() != event.getTo().getBlockX()
                        || event.getFrom().getBlockY() != event.getTo().getBlockY()
                        || event.getFrom().getBlockZ() != event.getTo().getBlockZ();

        if (!moved) {
            return;
        }

        semaforo.eliminate(player);

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                player,
                semaforo.getConfig().getString("filename", "").replace(".yml", ""),
                semaforo.getType()
        );

        Bukkit.getPluginManager().callEvent(loseEvent);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Semaforo semaforo = getEvento();
        if (semaforo == null) {
            return;
        }

        if (!semaforo.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Semaforo semaforo = getEvento();
        if (semaforo == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!semaforo.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Semaforo semaforo = getEvento();
        if (semaforo == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!semaforo.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Semaforo semaforo) {
            this.evento = semaforo;
        }
    }

    private Semaforo getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
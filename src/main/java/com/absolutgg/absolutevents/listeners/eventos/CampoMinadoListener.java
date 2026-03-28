package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.CampoMinado;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class CampoMinadoListener implements Listener {

    private CampoMinado evento;

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        CampoMinado campoMinado = getEvento();
        if (campoMinado == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!campoMinado.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Material blockType = event.getTo().getBlock().getType();

        if (blockType == Material.WATER) {
            campoMinado.eliminate(player);
            return;
        }

        campoMinado.handleLevelProgress(player);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        CampoMinado campoMinado = getEvento();
        if (campoMinado == null) {
            return;
        }

        if (!campoMinado.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        CampoMinado campoMinado = getEvento();
        if (campoMinado == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!campoMinado.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof CampoMinado campoMinado) {
            this.evento = campoMinado;
        }
    }

    private CampoMinado getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
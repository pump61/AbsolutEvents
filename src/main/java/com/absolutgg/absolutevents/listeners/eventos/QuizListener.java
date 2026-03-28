package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Quiz;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class QuizListener implements Listener {

    private Quiz evento;

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Quiz quiz = getEvento();
        if (quiz == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!quiz.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (quiz.isLockedAnswers()) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                    || event.getFrom().getBlockY() != event.getTo().getBlockY()
                    || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setTo(event.getFrom());
                return;
            }
        }

        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        if (event.getTo().getBlock().getType() == Material.WATER) {
            quiz.eliminate(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Quiz quiz = getEvento();
        if (quiz == null) {
            return;
        }

        if (!quiz.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Quiz quiz = getEvento();
        if (quiz == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!quiz.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Quiz quiz) {
            this.evento = quiz;
        }
    }

    private Quiz getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
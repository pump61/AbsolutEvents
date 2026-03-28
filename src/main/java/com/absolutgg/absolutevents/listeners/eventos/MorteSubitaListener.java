package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.MorteSubita;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class MorteSubitaListener implements Listener {

    private MorteSubita evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        MorteSubita morteSubita = getEvento();
        if (morteSubita == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        Player attacker = getDamager(event);
        if (attacker == null) {
            return;
        }

        if (!morteSubita.getPlayers().contains(defender) || !morteSubita.getPlayers().contains(attacker)) {
            return;
        }

        if (!morteSubita.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }

        boolean sameBlueTeam = morteSubita.getBlueTeam().contains(defender) && morteSubita.getBlueTeam().contains(attacker);
        boolean sameRedTeam = morteSubita.getRedTeam().contains(defender) && morteSubita.getRedTeam().contains(attacker);

        if (sameBlueTeam || sameRedTeam) {
            event.setCancelled(true);
            return;
        }

        // Intercepta dano letal para o jogador não morrer de verdade
        if ((defender.getHealth() - event.getFinalDamage()) <= 0.0D) {
            event.setCancelled(true);

            double newHealth = Math.min(attacker.getMaxHealth(), attacker.getHealth() + morteSubita.getHeartsAdded());
            attacker.setHealth(newHealth);

            morteSubita.eliminate(defender, attacker);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        MorteSubita morteSubita = getEvento();
        if (morteSubita == null) {
            return;
        }

        if (!morteSubita.getPlayers().contains(event.getEntity())) {
            return;
        }

        // Fallback de segurança: nunca deixa morte real acontecer no evento
        event.setCancelled(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        MorteSubita morteSubita = getEvento();
        if (morteSubita == null) {
            return;
        }

        if (!morteSubita.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        MorteSubita morteSubita = getEvento();
        if (morteSubita == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!morteSubita.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
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
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof MorteSubita morteSubita) {
            this.evento = morteSubita;
        }
    }

    private MorteSubita getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
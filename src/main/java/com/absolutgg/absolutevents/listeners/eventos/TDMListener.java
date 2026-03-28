package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.eventos.TeamDeathmatch;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class TDMListener implements Listener {

    private TeamDeathmatch evento;

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        TeamDeathmatch tdm = getEvento();
        if (tdm == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player defender)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!tdm.getPlayers().contains(defender) || !tdm.getPlayers().contains(attacker)) {
            return;
        }

        if (!tdm.isPvpEnabled()) {
            event.setCancelled(true);
            return;
        }

        if (tdm.getDeadPlayers().contains(defender) || tdm.getDeadPlayers().contains(attacker)) {
            event.setCancelled(true);
            return;
        }

        if (tdm.getInvinciblePlayers().contains(defender)) {
            event.setCancelled(true);
            return;
        }

        boolean sameBlue = tdm.getBlueTeam().containsKey(defender) && tdm.getBlueTeam().containsKey(attacker);
        boolean sameRed = tdm.getRedTeam().containsKey(defender) && tdm.getRedTeam().containsKey(attacker);

        if (sameBlue || sameRed) {
            event.setCancelled(true);
            return;
        }

        double finalHealth = defender.getHealth() - event.getFinalDamage();

        // Só intercepta o golpe que mataria.
        // Todos os outros hits ficam 100% vanilla.
        if (finalHealth <= 0.0D) {
            event.setCancelled(true);
            tdm.handleKill(defender, attacker);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        TeamDeathmatch tdm = getEvento();
        if (tdm == null) {
            return;
        }

        Player victim = event.getEntity();

        if (!tdm.getPlayers().contains(victim)) {
            return;
        }

        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);
        event.setDeathMessage(null);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        TeamDeathmatch tdm = getEvento();
        if (tdm == null) {
            return;
        }

        if (!tdm.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        TeamDeathmatch tdm = getEvento();
        if (tdm == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!tdm.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        TeamDeathmatch tdm = getEvento();
        if (tdm == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!tdm.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    public void setEvento() {
        if (com.absolutgg.absolutevents.AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof TeamDeathmatch tdm) {
            this.evento = tdm;
        }
    }

    private TeamDeathmatch getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
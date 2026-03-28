package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Killer;
import com.iridium.iridiumcolorapi.IridiumColorAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class KillerListener implements Listener {

    private Killer evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        Killer killer = getEvento();
        if (killer == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player damaged)) {
            return;
        }

        if (!killer.getPlayers().contains(damaged)) {
            return;
        }

        if (!killer.isPvPEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Killer killer = getEvento();
        if (killer == null || killer.isEnding()) {
            return;
        }

        Player dead = event.getEntity();

        if (!killer.getPlayers().contains(dead)) {
            return;
        }

        Player killerPlayer = dead.getKiller();

        if (killerPlayer != null && !killer.getPlayers().contains(killerPlayer)) {
            return;
        }

        if (killerPlayer != null) {
            killer.getKills().merge(killerPlayer, 1, Integer::sum);
        }

        dead.sendMessage(IridiumColorAPI.process(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
                        .replace("&", "§")
        ));

        killer.remove(dead);
        killer.leaveBungeecord(dead);

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                dead,
                killer.getConfig().getString("filename", "").replace(".yml", ""),
                killer.getType()
        );

        Bukkit.getPluginManager().callEvent(loseEvent);

        if (killer.isHappening() && killer.getPlayers().size() == 1) {
            killer.winner(killer.getPlayers().get(0));
        }
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Killer killer) {
            this.evento = killer;
        }
    }

    private Killer getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
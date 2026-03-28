package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.eventos.Montaria;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public final class MontariaListener implements Listener {

    private Montaria evento;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Montaria montaria = getEvento();
        if (montaria == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!montaria.isEventPlayer(player)) {
            return;
        }

        if (!montaria.isRaceStarted()) {
            if (event.getTo() == null) {
                return;
            }

            boolean changedBlock =
                    event.getFrom().getBlockX() != event.getTo().getBlockX()
                            || event.getFrom().getBlockY() != event.getTo().getBlockY()
                            || event.getFrom().getBlockZ() != event.getTo().getBlockZ();

            if (changedBlock) {
                montaria.lockPlayerAtStart(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        Montaria montaria = getEvento();
        if (montaria == null) {
            return;
        }

        if (event.getEntity() instanceof Player player && montaria.isEventPlayer(player)) {
            event.setCancelled(true);
            return;
        }

        if (event.getEntity() instanceof Horse horse) {
            for (Player player : montaria.getPlayers()) {
                if (montaria.getHorse(player) == horse) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDismount(EntityDismountEvent event) {
        Montaria montaria = getEvento();
        if (montaria == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!montaria.isEventPlayer(player)) {
            return;
        }

        if (event.getDismounted() instanceof Horse) {
            event.setCancelled(true);
            montaria.remount(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVehicleExit(VehicleExitEvent event) {
        Montaria montaria = getEvento();
        if (montaria == null) {
            return;
        }

        if (!(event.getExited() instanceof Player player)) {
            return;
        }

        if (!montaria.isEventPlayer(player)) {
            return;
        }

        event.setCancelled(true);
        montaria.remount(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onMount(EntityMountEvent event) {
        Montaria montaria = getEvento();
        if (montaria == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!montaria.isEventPlayer(player)) {
            return;
        }

        if (!(event.getMount() instanceof Horse horse)) {
            return;
        }

        Horse ownHorse = montaria.getHorse(player);
        if (ownHorse == null) {
            return;
        }

        // Se for o cavalo do próprio jogador no evento, força liberação do mount
        if (ownHorse == horse) {
            if (event.isCancelled()) {
                event.setCancelled(false);
            }
            return;
        }

        // Qualquer outro cavalo continua bloqueado
        event.setCancelled(true);
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Montaria montaria) {
            this.evento = montaria;
        }
    }

    private Montaria getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
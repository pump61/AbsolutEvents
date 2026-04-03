package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Anvil;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class AnvilListener implements Listener {

    private final NamespacedKey anvilKey = new NamespacedKey(AbsolutEventsPlugin.getInstance(), "absolutevents_anvil");
    private Anvil evento;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAnvilLand(EntityChangeBlockEvent event) {
        Anvil anvilEvent = getEvento();
        if (anvilEvent == null) {
            return;
        }

        if (event.getEntityType() != EntityType.FALLING_BLOCK) {
            return;
        }

        FallingBlock fallingBlock = (FallingBlock) event.getEntity();
        if (!fallingBlock.getPersistentDataContainer().has(anvilKey, PersistentDataType.BYTE)) {
            return;
        }

        Material material = fallingBlock.getBlockData().getMaterial();
        if (!Tag.ANVIL.isTagged(material)) {
            return;
        }

        Block landedBlock = event.getBlock();

        event.setCancelled(true);
        fallingBlock.setDropItem(false);
        fallingBlock.remove();

        List<Player> eliminate = new ArrayList<>();

        for (Player player : new ArrayList<>(anvilEvent.getPlayers())) {
            if (isHitByAnvil(player, landedBlock)) {
                eliminate.add(player);
            }
        }

        for (Player player : eliminate) {
            anvilEvent.eliminate(player);

            Bukkit.getPluginManager().callEvent(
                    new PlayerLoseEvent(
                            player,
                            anvilEvent.getConfig().getString("filename", "").replace(".yml", ""),
                            anvilEvent.getType()
                    )
            );
        }

        anvilEvent.markAnvilResolved(landedBlock);

        if (anvilEvent.getPlayers().isEmpty()) {
            anvilEvent.noWinner();
            return;
        }

        if (anvilEvent.getPlayers().size() == 1) {
            anvilEvent.winner(anvilEvent.getPlayers().get(0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvilDirectHit(EntityDamageByEntityEvent event) {
        Anvil anvilEvent = getEvento();
        if (anvilEvent == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!(event.getDamager() instanceof FallingBlock fallingBlock)) {
            return;
        }

        if (!fallingBlock.getPersistentDataContainer().has(anvilKey, PersistentDataType.BYTE)) {
            return;
        }

        if (!anvilEvent.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);

        anvilEvent.eliminate(player);

        Bukkit.getPluginManager().callEvent(
                new PlayerLoseEvent(
                        player,
                        anvilEvent.getConfig().getString("filename", "").replace(".yml", ""),
                        anvilEvent.getType()
                )
        );

        fallingBlock.remove();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Anvil anvilEvent = getEvento();
        if (anvilEvent == null) {
            return;
        }

        if (!anvilEvent.getPlayers().contains(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Anvil anvilEvent = getEvento();
        if (anvilEvent == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!anvilEvent.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
    }

    private boolean isHitByAnvil(Player player, Block block) {
        double px = player.getLocation().getX();
        double pz = player.getLocation().getZ();
        double py = player.getLocation().getY();

        double bx = block.getX() + 0.5;
        double bz = block.getZ() + 0.5;

        double dx = Math.abs(px - bx);
        double dz = Math.abs(pz - bz);

        return dx <= 0.75D
                && dz <= 0.75D
                && py <= block.getY() + 2.2D;
    }

    public NamespacedKey getAnvilKey() {
        return anvilKey;
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Anvil anvil) {
            this.evento = anvil;
        }
    }

    private Anvil getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
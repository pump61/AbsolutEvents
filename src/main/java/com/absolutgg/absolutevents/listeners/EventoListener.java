package com.absolutgg.absolutevents.listeners;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.commands.EventoCommand;
import com.absolutgg.absolutevents.eventos.BattleRoyale;
import com.absolutgg.absolutevents.eventos.Guerra;
import com.absolutgg.absolutevents.eventos.Nexus;
import com.absolutgg.absolutevents.eventos.SuperSmackers;
import com.absolutgg.absolutevents.hooks.BungeecordHook;
import com.absolutgg.absolutevents.manager.InventorySerializer;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.absolutgg.absolutevents.utils.QuitCache;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EventoListener implements Listener {

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("absolutevents.admin")) {
            return;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        if (!containsPlayer(evento, player.getUniqueId()) && !containsSpectator(evento, player.getUniqueId())) {
            return;
        }

        List<String> allowedCommands = AbsolutEventsPlugin.getInstance().getConfig().getStringList("Allowed commands");
        String command = event.getMessage().trim().toLowerCase().split(" ")[0];

        boolean allowed = allowedCommands.stream()
                .map(String::toLowerCase)
                .anyMatch(cmd -> cmd.equals(command));

        if (!allowed) {
            player.sendMessage(ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance()
                            .getConfig()
                            .getString("Messages.Blocked command", "&cVocê não pode usar este comando.")
            ));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            return;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean eliminateOnQuit = evento.getConfig().getBoolean("Evento.Eliminate on quit", evento.isElimination());

        if (containsPlayer(evento, uuid)) {
            if (!eliminateOnQuit) {
                return;
            }

            QuitCache.setQuit(uuid);
            removePlayerByUuid(evento, uuid);

            if (!evento.isOpen() && evento.isElimination() && evento.getPlayers().size() == 1) {
                evento.winner(evento.getPlayers().get(0));
                return;
            }

            if (!evento.isOpen() && evento.getPlayers().isEmpty()) {
                for (String message : evento.getConfig().getStringList("Messages.No winner")) {
                    Bukkit.broadcastMessage(ColorUtils.colorize(
                            message.replace("@name", evento.getConfig().getString("Evento.Title"))
                    ));
                }
                evento.stop();
            }
            return;
        }

        if (containsSpectator(evento, uuid)) {
            QuitCache.setQuit(uuid);
            removeSpectatorByUuid(evento, uuid);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean markedQuit = QuitCache.wasInEvent(uuid);

        if (markedQuit) {
            Bukkit.getScheduler().runTaskLater(
                    AbsolutEventsPlugin.getInstance(),
                    () -> forceRemoveAndRestore(player),
                    20L
            );
            return;
        }

        if (AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
            if (evento != null) {
                boolean eliminateOnQuit = evento.getConfig().getBoolean("Evento.Eliminate on quit", evento.isElimination());

                if (eliminateOnQuit) {
                    boolean stillInLists = containsPlayer(evento, uuid) || containsSpectator(evento, uuid);
                    boolean insideEventWorld = isInEventWorld(player, evento.getConfig());

                    if (stillInLists || insideEventWorld) {
                        Bukkit.getScheduler().runTaskLater(
                                AbsolutEventsPlugin.getInstance(),
                                () -> forceRemoveAndRestore(player),
                                20L
                        );
                        return;
                    }
                }
            }
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        if (BungeecordHook.getJoin().remove(player.getName())) {
            if (evento.isOpen()) {
                evento.join(player);
            }
        }

        if (BungeecordHook.getSpectate().remove(player.getName())) {
            if (evento.isSpectatorAllowed()) {
                evento.spectate(player);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (InventorySerializer.hasSnapshot(player.getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpectatorMove(PlayerMoveEvent event) {
        if (!AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            return;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!containsSpectator(evento, player.getUniqueId())) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SPECTATOR);
        }

        Location center = getSpectatorLocation(evento.getConfig());
        if (center == null) {
            return;
        }

        if (!center.getWorld().equals(event.getTo().getWorld())) {
            player.teleport(center);
            return;
        }

        double maxDistance = 500.0D;
        if (center.distanceSquared(event.getTo()) > (maxDistance * maxDistance)) {
            player.teleport(center);
        }
    }

    @EventHandler
    public void onInventoryInteract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            return;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (!containsPlayer(evento, uuid) && !containsSpectator(evento, uuid)) {
            return;
        }

        if (evento instanceof Nexus
                || evento instanceof BattleRoyale
                || evento instanceof Guerra
                || evento instanceof SuperSmackers) {
            return;
        }

        if (event.getSlotType() == InventoryType.SlotType.CRAFTING
                || event.getClickedInventory() == player.getInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Map<Player, YamlConfiguration> setup = EventoCommand.getSetupList();

        if (!setup.containsKey(player)) {
            return;
        }

        if (event.getItem() == null || !event.getItem().hasItemMeta()) {
            return;
        }

        if (event.getItem().getItemMeta().getDisplayName() == null) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        String displayName = event.getItem().getItemMeta().getDisplayName();

        if (!displayName.equals("§6Machado de Posições") && !displayName.equals("§6Enxada de Posições")) {
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        YamlConfiguration settings = setup.get(player);

        event.setCancelled(true);

        if (displayName.equals("§6Machado de Posições")) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                savePosition(settings, player, clicked, "Pos1");
            } else {
                savePosition(settings, player, clicked, "Pos2");
            }
            return;
        }

        if (displayName.equals("§6Enxada de Posições")) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                savePosition(settings, player, clicked, "Pos3");
            } else {
                savePosition(settings, player, clicked, "Pos4");
            }
        }
    }

    private void forceRemoveAndRestore(Player player) {
        if (!player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            var running = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
            if (running != null) {
                removePlayerByUuid(running, uuid);
                removeSpectatorByUuid(running, uuid);
            }
        }

        sendToSpawn(player);

        if (InventorySerializer.hasSnapshot(uuid)) {
            boolean restored = InventorySerializer.restoreSnapshot(player);
            if (!restored) {
                player.getInventory().clear();
                player.updateInventory();
            }
        } else {
            player.getInventory().clear();
            player.updateInventory();
        }

        player.setGameMode(GameMode.SURVIVAL);
        player.updateInventory();

        QuitCache.remove(uuid);
    }

    private void sendToSpawn(Player player) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
    }

    private boolean isInEventWorld(Player player, YamlConfiguration config) {
        String entranceWorld = config.getString("Locations.Entrance.world");
        String lobbyWorld = config.getString("Locations.Lobby.world");
        String spectatorWorld = config.getString("Locations.Spectator.world");

        String current = player.getWorld().getName();

        return (entranceWorld != null && entranceWorld.equalsIgnoreCase(current))
                || (lobbyWorld != null && lobbyWorld.equalsIgnoreCase(current))
                || (spectatorWorld != null && spectatorWorld.equalsIgnoreCase(current));
    }

    private boolean containsPlayer(com.absolutgg.absolutevents.api.Evento evento, UUID uuid) {
        return evento.getPlayers().stream().anyMatch(p -> p.getUniqueId().equals(uuid));
    }

    private boolean containsSpectator(com.absolutgg.absolutevents.api.Evento evento, UUID uuid) {
        return evento.getSpectators().stream().anyMatch(p -> p.getUniqueId().equals(uuid));
    }

    private void removePlayerByUuid(com.absolutgg.absolutevents.api.Evento evento, UUID uuid) {
        evento.getPlayers().removeIf(p -> p.getUniqueId().equals(uuid));
    }

    private void removeSpectatorByUuid(com.absolutgg.absolutevents.api.Evento evento, UUID uuid) {
        evento.getSpectators().removeIf(p -> p.getUniqueId().equals(uuid));
    }

    private void savePosition(YamlConfiguration settings, Player player, Block block, String pos) {
        settings.set("Locations." + pos + ".world", block.getWorld().getName());
        settings.set("Locations." + pos + ".x", block.getX());
        settings.set("Locations." + pos + ".y", block.getY());
        settings.set("Locations." + pos + ".z", block.getZ());
        settings.set("Locations." + pos + ".Yaw", player.getLocation().getYaw());
        settings.set("Locations." + pos + ".Pitch", player.getLocation().getPitch());

        try {
            EventoConfigFile.save(settings);
            EventoCommand.getSetupList().put(player, settings);

            player.sendMessage(ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance()
                            .getConfig()
                            .getString("Messages.Saved", "&aPosição salva.")
                            .replace("@pos", pos)
            ));
        } catch (IOException exception) {
            player.sendMessage(ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance()
                            .getConfig()
                            .getString("Messages.Error", "&cOcorreu um erro.")
            ));
            exception.printStackTrace();
        }
    }

    private Location getCurrentEventExitOrFallback() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()) {
            var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
            if (evento != null) {
                Location exit = getExitLocation(evento.getConfig());
                if (exit != null) {
                    return exit;
                }
            }
        }
        return getFallbackSpawn();
    }

    private Location getExitLocation(YamlConfiguration config) {
        if (config == null || !config.contains("Locations.Exit.world")) {
            return getFallbackSpawn();
        }

        World world = Bukkit.getWorld(config.getString("Locations.Exit.world"));
        if (world == null) {
            return getFallbackSpawn();
        }

        double x = config.getDouble("Locations.Exit.x");
        double y = config.getDouble("Locations.Exit.y");
        double z = config.getDouble("Locations.Exit.z");

        float yaw = 0.0F;
        float pitch = 0.0F;

        if (config.contains("Locations.Exit.Yaw")) {
            yaw = (float) config.getDouble("Locations.Exit.Yaw");
        } else if (config.contains("Locations.Exit.yaw")) {
            yaw = (float) config.getDouble("Locations.Exit.yaw");
        }

        if (config.contains("Locations.Exit.Pitch")) {
            pitch = (float) config.getDouble("Locations.Exit.Pitch");
        } else if (config.contains("Locations.Exit.pitch")) {
            pitch = (float) config.getDouble("Locations.Exit.pitch");
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location getSpectatorLocation(YamlConfiguration config) {
        if (config == null || !config.contains("Locations.Spectator.world")) {
            return null;
        }

        World world = Bukkit.getWorld(config.getString("Locations.Spectator.world"));
        if (world == null) {
            return null;
        }

        double x = config.getDouble("Locations.Spectator.x");
        double y = config.getDouble("Locations.Spectator.y");
        double z = config.getDouble("Locations.Spectator.z");

        float yaw = 0.0F;
        float pitch = 0.0F;

        if (config.contains("Locations.Spectator.Yaw")) {
            yaw = (float) config.getDouble("Locations.Spectator.Yaw");
        } else if (config.contains("Locations.Spectator.yaw")) {
            yaw = (float) config.getDouble("Locations.Spectator.yaw");
        }

        if (config.contains("Locations.Spectator.Pitch")) {
            pitch = (float) config.getDouble("Locations.Spectator.Pitch");
        } else if (config.contains("Locations.Spectator.pitch")) {
            pitch = (float) config.getDouble("Locations.Spectator.pitch");
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location getFallbackSpawn() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }
}
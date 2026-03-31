package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SpleggListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class Splegg extends Evento {

    private static final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final SpleggListener listener = new SpleggListener();

    private boolean started;
    private boolean ending;

    private final int delay;
    private final List<Material> breakableMaterials;

    public Splegg(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.started = false;
        this.ending = false;
        this.delay = config.getInt("Evento.Delay");

        this.breakableMaterials = config.getStringList("Evento.Breakable materials").stream()
                .map(XMaterial::matchXMaterial)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(XMaterial::parseMaterial)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void start() {
        started = false;
        ending = false;

        listener.clearLastMove();

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        rebuildArena();

        for (Player player : new ArrayList<>(getPlayers())) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItem(0, new ItemStack(Material.EGG, 16));
            player.updateInventory();
        }

        for (String message : config.getStringList("Messages.Enabling breaking")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@time", String.valueOf(delay))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) return;

            for (String message : config.getStringList("Messages.Breaking allowed")) {
                sendToEvent(ColorUtils.colorize(
                        message.replace("@name", config.getString("Evento.Title"))
                ));
            }

            this.started = true;
        }, Math.max(1L, delay * 20L));
    }

    @Override
    public void stop() {
        started = false;
        ending = false;

        rebuildArena();

        for (Player player : new ArrayList<>(getPlayers())) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.updateInventory();
        }

        listener.clearLastMove();

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    @Override
    public void winner(Player player) {
        if (ending) return;

        ending = true;

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        this.setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        String winnerName = player.getName();

        this.stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player onlineWinner = Bukkit.getPlayerExact(winnerName);
            if (onlineWinner == null || !onlineWinner.isOnline()) return;

            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(onlineWinner, command.replace("@winner", onlineWinner.getName()));
            }
        }, 5L);
    }

    public void noWinner() {
        if (ending) return;

        ending = true;

        for (String message : config.getStringList("Messages.No winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) return;

        player.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        remove(player);
        listener.removeLastMove(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        checkWinState();
    }

    private void checkWinState() {
        if (!isHappening() || ending) return;

        if (getPlayers().isEmpty()) {
            noWinner();
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    private void sendToEvent(String message) {
        String parsed = ColorUtils.colorize(message);

        for (Player player : getPlayers()) {
            player.sendMessage(parsed);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(parsed);
        }
    }

    private void rebuildArena() {
        fillArena();

        Bukkit.getScheduler().runTaskLater(plugin, this::fillArena, 2L);
    }

    private void fillArena() {
        World world = Bukkit.getWorld(config.getString("Locations.Pos1.world"));
        if (world == null) return;

        int x1 = (int) Math.floor(config.getDouble("Locations.Pos1.x"));
        int y1 = (int) Math.floor(config.getDouble("Locations.Pos1.y"));
        int z1 = (int) Math.floor(config.getDouble("Locations.Pos1.z"));

        int x2 = (int) Math.floor(config.getDouble("Locations.Pos2.x"));
        int y2 = (int) Math.floor(config.getDouble("Locations.Pos2.y"));
        int z2 = (int) Math.floor(config.getDouble("Locations.Pos2.z"));

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        Material fillMaterial = breakableMaterials.isEmpty() ? Material.SNOW_BLOCK : breakableMaterials.get(0);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(fillMaterial, false);
                }
            }
        }
    }

    public boolean isStarted() {
        return started;
    }

    public double getArenaMinY() {
        return Math.min(
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos2.y")
        );
    }

    public boolean isInsideArena(Block block) {
        if (block == null || block.getWorld() == null) return false;

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || !block.getWorld().getName().equals(worldName)) return false;

        int x1 = (int) Math.floor(config.getDouble("Locations.Pos1.x"));
        int y1 = (int) Math.floor(config.getDouble("Locations.Pos1.y"));
        int z1 = (int) Math.floor(config.getDouble("Locations.Pos1.z"));

        int x2 = (int) Math.floor(config.getDouble("Locations.Pos2.x"));
        int y2 = (int) Math.floor(config.getDouble("Locations.Pos2.y"));
        int z2 = (int) Math.floor(config.getDouble("Locations.Pos2.z"));

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);

        return block.getX() >= minX && block.getX() <= maxX
                && block.getY() >= minY && block.getY() <= maxY
                && block.getZ() >= minZ && block.getZ() <= maxZ;
    }

    public List<Material> getBreakableMaterials() {
        return breakableMaterials;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
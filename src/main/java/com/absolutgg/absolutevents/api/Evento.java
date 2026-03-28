package com.absolutgg.absolutevents.api;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.EventoStartedEvent;
import com.absolutgg.absolutevents.api.events.EventoStartingEvent;
import com.absolutgg.absolutevents.api.events.EventoStopEvent;
import com.absolutgg.absolutevents.api.events.PlayerJoinEvent;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.api.events.PlayerWinEvent;
import com.absolutgg.absolutevents.hooks.BungeecordHook;
import com.absolutgg.absolutevents.manager.InventoryManager;
import com.absolutgg.absolutevents.manager.InventorySerializer;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class Evento implements EventoInterface {

    private final List<Player> players = new ArrayList<>();
    private final List<Player> spectators = new ArrayList<>();
    private final List<Player> winners = new ArrayList<>();

    private final EventoType type;
    private boolean happening;
    private boolean open;

    private final boolean allowSpectator;
    private final boolean emptyInventory;
    private boolean elimination;
    private final boolean countParticipation;
    private final boolean countWin;
    private final boolean bungeecordEnabled;
    private final double money;
    private final String permission;
    private final String identifier;

    private final YamlConfiguration config;

    public Evento(YamlConfiguration config) {
        this.config = config;
        this.type = EventoType.getEventoType(config.getString("Evento.Type"));
        this.allowSpectator = config.getBoolean("Evento.Spectator mode");
        this.emptyInventory = config.getBoolean("Evento.Empty inventory");
        this.permission = config.getString("Evento.Permission");
        this.countParticipation = config.getBoolean("Evento.Count participation");
        this.countWin = config.getBoolean("Evento.Count victory");
        this.bungeecordEnabled = AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Enabled");
        this.elimination = false;

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        this.identifier = type + " " + dateFormat.format(Calendar.getInstance().getTime());

        switch (type) {
            case SPLEEF:
            case BATATA_QUENTE:
            case FIGHT:
            case KILLER:
            case SUMO:
            case QUIZ:
            case ANVIL:
            case THOR:
            case BATTLE_ROYALE:
                this.elimination = true;
                break;

            default:
                break;
        }

        if (config.isSet("custom_reward")) {
            this.money = config.getDouble("custom_reward");
        } else {
            this.money = config.getDouble("Rewards.Money");
        }
    }

    @Override
    public void startCall() {
        this.happening = true;
        this.open = true;

        Bukkit.getPluginManager().callEvent(new EventoStartingEvent(getFileNameWithoutExtension(), type));

        if (isUsingBungeecordServer()) {
            BungeecordHook.startEvento(type.toString(), getFileNameWithoutExtension());
        }

        new BukkitRunnable() {
            int calls = config.getInt("Evento.Calls");

            @Override
            public void run() {
                if (!isHappening() || !isOpen()) {
                    cancel();
                    return;
                }

                if (calls >= 0) {
                    List<String> broadcastLines = getBroadcastLines(players.size(), calls);

                    for (String raw : broadcastLines) {
                        String line = parseEventMessage(raw);
                        final String buttonToken = "@join_button";

                        if (line.contains(buttonToken)) {
                            String before = line.substring(0, line.indexOf(buttonToken));
                            String after = line.substring(line.indexOf(buttonToken) + buttonToken.length());

                            String text = AbsolutEventsPlugin.getInstance().getConfig()
                                    .getString("Messages.Join Button.Text", "&a[CLIQUE AQUI PARA ENTRAR]");

                            List<String> hoverLines = AbsolutEventsPlugin.getInstance().getConfig()
                                    .getStringList("Messages.Join Button.Hover");

                            if (hoverLines.isEmpty()) {
                                hoverLines = Collections.singletonList("&7Clique para participar do evento");
                            }

                            String command = AbsolutEventsPlugin.getInstance().getConfig()
                                    .getString("Messages.Join Button.Command", "/evento");

                            Component hoverComponent = Component.empty();
                            for (int i = 0; i < hoverLines.size(); i++) {
                                if (i > 0) {
                                    hoverComponent = hoverComponent.append(Component.newline());
                                }
                                hoverComponent = hoverComponent.append(MessageUtils.component(hoverLines.get(i)));
                            }

                            Component buttonComponent = MessageUtils.component(text)
                                    .clickEvent(ClickEvent.runCommand(command))
                                    .hoverEvent(HoverEvent.showText(hoverComponent));

                            Component finalMessage = MessageUtils.component(before)
                                    .append(buttonComponent)
                                    .append(MessageUtils.component(after));

                            broadcastComponent(finalMessage);
                            continue;
                        }

                        broadcastComponent(MessageUtils.component(line));
                    }

                    calls--;
                    return;
                }

                cancel();

                if (players.size() >= config.getInt("Evento.Mininum players")) {
                    open = false;

                    for (String message : config.getStringList("Messages.Start")) {
                        broadcastComponent(MessageUtils.component(parseEventMessage(message)));
                    }

                    for (Player player : new ArrayList<>(players)) {
                        teleport(player, "entrance");
                    }

                    if (isUsingBungeecordServer()) {
                        BungeecordHook.startingEvento(type.toString(), getFileNameWithoutExtension());
                    }

                    start();
                    Bukkit.getPluginManager().callEvent(new EventoStartedEvent(getFileNameWithoutExtension(), type));

                } else {
                    if (isUsingBungeecordServer()) {
                        BungeecordHook.stopEvento("noplayers");
                    }

                    for (String message : config.getStringList("Messages.No players")) {
                        broadcastComponent(MessageUtils.component(parseEventMessage(message)));
                    }

                    stop();
                }
            }
        }.runTaskTimer(AbsolutEventsPlugin.getInstance(), 0L, config.getInt("Evento.Calls interval") * 20L);
    }

    public void startBungeecord() {
        if (!open || !isUsingBungeecordServer()) {
            return;
        }

        if (players.size() >= config.getInt("Evento.Mininum players")) {
            open = false;

            for (String message : config.getStringList("Messages.Start")) {
                broadcastComponent(MessageUtils.component(parseEventMessage(message)));
            }

            for (Player player : new ArrayList<>(players)) {
                teleport(player, "entrance");
            }

            BungeecordHook.startingEvento(type.toString(), getFileNameWithoutExtension());
            start();

            Bukkit.getPluginManager().callEvent(new EventoStartedEvent(getFileNameWithoutExtension(), type));

        } else {
            for (String message : config.getStringList("Messages.No players")) {
                broadcastComponent(MessageUtils.component(parseEventMessage(message)));
            }

            stop();
        }
    }

    @Override
    public void start() {
    }

    @Override
    public void forceStart() {
        this.open = false;

        for (String message : config.getStringList("Messages.Start")) {
            broadcastComponent(MessageUtils.component(parseEventMessage(message)));
        }

        for (Player player : new ArrayList<>(players)) {
            teleport(player, "entrance");
        }

        if (isUsingBungeecordServer()) {
            BungeecordHook.startingEvento(type.toString(), getFileNameWithoutExtension());
        }

        start();
        Bukkit.getPluginManager().callEvent(new EventoStartedEvent(getFileNameWithoutExtension(), type));
    }

    @Override
    public void winner(Player player) {
    }

    @Override
    public void setWinner(Player player) {
        if (AbsolutEventsPlugin.getInstance().getEconomy() != null) {
            AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, this.money);
        }

        if (!this.countWin) {
            return;
        }

        List<String> winnerIds = new ArrayList<>();
        winnerIds.add(player.getUniqueId().toString());
        this.winners.add(player);

        AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
        AbsolutEventsPlugin.getInstance().getConnectionManager().addWin(getFileNameWithoutExtension(), player.getUniqueId());
        AbsolutEventsPlugin.getInstance().getConnectionManager().setEventoWinner(getFileNameWithoutExtension(), winnerIds);

        refreshCaches();
        Bukkit.getPluginManager().callEvent(new PlayerWinEvent(player, getFileNameWithoutExtension(), type));
    }

    @Override
    public void setWinners() {
        if (EventoType.isEventoGuild(type) || !this.countWin) {
            return;
        }

        List<String> winnerIds = new ArrayList<>();

        for (Player player : new ArrayList<>(players)) {
            this.winners.add(player);
            winnerIds.add(player.getUniqueId().toString());

            AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
            AbsolutEventsPlugin.getInstance().getConnectionManager().addWin(getFileNameWithoutExtension(), player.getUniqueId());

            if (AbsolutEventsPlugin.getInstance().getEconomy() != null) {
                AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, this.money);
            }

            Bukkit.getPluginManager().callEvent(new PlayerWinEvent(player, getFileNameWithoutExtension(), type));
        }

        AbsolutEventsPlugin.getInstance().getConnectionManager().setEventoWinner(getFileNameWithoutExtension(), winnerIds);
        refreshCaches();
    }

    public void setWinners(Set<Player> winnersList) {
        if (EventoType.isEventoGuild(type) || !this.countWin || this.elimination) {
            return;
        }

        List<String> winnerIds = new ArrayList<>();

        for (Player player : winnersList) {
            this.winners.add(player);
            winnerIds.add(player.getUniqueId().toString());

            AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
            AbsolutEventsPlugin.getInstance().getConnectionManager().addWin(getFileNameWithoutExtension(), player.getUniqueId());

            if (AbsolutEventsPlugin.getInstance().getEconomy() != null) {
                AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, this.money);
            }

            Bukkit.getPluginManager().callEvent(new PlayerWinEvent(player, getFileNameWithoutExtension(), type));
        }

        AbsolutEventsPlugin.getInstance().getConnectionManager().setEventoWinner(getFileNameWithoutExtension(), winnerIds);
        refreshCaches();
    }

    public void setWinners(String guildName, HashMap<OfflinePlayer, Integer> kills) {
        if (!EventoType.isEventoGuild(type) || !this.countWin) {
            return;
        }

        List<String> winnerIds = new ArrayList<>();

        for (Player player : new ArrayList<>(players)) {
            this.winners.add(player);
            winnerIds.add(player.getUniqueId().toString());

            AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
            AbsolutEventsPlugin.getInstance().getConnectionManager().addWin(getFileNameWithoutExtension(), player.getUniqueId());

            if (AbsolutEventsPlugin.getInstance().getEconomy() != null) {
                AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, this.money);
            }

            Bukkit.getPluginManager().callEvent(new PlayerWinEvent(player, getFileNameWithoutExtension(), type));
        }

        AbsolutEventsPlugin.getInstance().getConnectionManager().setEventoGuildWinner(
                getFileNameWithoutExtension(),
                guildName,
                kills,
                winnerIds
        );

        refreshCaches();
    }

    @Override
    public void stop() {
    }

    @Override
    public void removePlayers() {
        this.happening = false;
        this.open = false;

        if (isUsingBungeecordServer()) {
            BungeecordHook.stopEvento("ended");
        }

        for (Player player : new ArrayList<>(players)) {
            restorePlayerAfterEvent(player);
        }

        for (Player player : new ArrayList<>(spectators)) {
            restoreSpectatorAfterEvent(player);
        }

        players.clear();
        spectators.clear();
        winners.clear();

        Bukkit.getPluginManager().callEvent(new EventoStopEvent(getFileNameWithoutExtension(), type));
        AbsolutEventsPlugin.getInstance().getEventoManager().clearEvento();
    }

    public void joinBungeecord(Player player) {
        if (isUsingBungeecordServer()) {
            BungeecordHook.joinEvento(player.getName());
        } else {
            join(player);
        }
    }

    @Override
    public void join(Player player) {
        boolean saveInventory = AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Save inventory");

        if (saveInventory) {
            InventorySerializer.saveSnapshot(player, this.identifier);
        } else {
            player.getInventory().clear();
        }

        player.getInventory().clear();
        player.updateInventory();

        player.setFoodLevel(20);
        players.add(player);
        teleport(player, "lobby");

        for (PotionEffect potion : player.getActivePotionEffects()) {
            player.removePotionEffect(potion.getType());
        }

        for (Player online : players) {
            online.sendMessage(joinMessage(player));
        }

        for (Player online : spectators) {
            online.sendMessage(joinMessage(player));
        }

        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(player, getFileNameWithoutExtension(), type));
    }

    @Override
    public void leave(Player player) {
        if (players.contains(player)) {
            for (Player online : players) {
                online.sendMessage(leaveMessage(player));
            }

            for (Player online : spectators) {
                online.sendMessage(leaveMessage(player));
            }
        }

        if (spectators.contains(player)) {
            spectators.remove(player);
            restoreSpectatorAfterEvent(player);
            return;
        }

        Bukkit.getPluginManager().callEvent(new PlayerLoseEvent(player, getFileNameWithoutExtension(), type));
        remove(player, true);
    }

    public void leaveBungeecord(Player player) {
        if (!notifyLeave(player)) {
            leave(player);
        }
    }

    public boolean notifyLeave(Player player) {
        if (isUsingBungeecordServer()) {
            BungeecordHook.leaveEvento(player.getName());
            return true;
        }
        return false;
    }

    @Override
    public void remove(Player player) {
        removeInternal(player);
    }

    @Override
    public void remove(Player player, boolean leaved) {
        removeInternal(player);
    }

    @Override
    public void spectate(Player player) {
        player.getInventory().clear();
        player.updateInventory();
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SPECTATOR);

        spectators.add(player);
        teleport(player, "spectator");
    }

    public void spectateBungeecord(Player player) {
        if (isUsingBungeecordServer()) {
            BungeecordHook.spectateEvento(player.getName());
        } else {
            spectate(player);
        }
    }

    public void executeConsoleCommand(Player player, String command) {
        if (isUsingBungeecordServer()) {
            BungeecordHook.executeCommand(player.getName(), command, config.getString("Locations.Server"));
        } else {
            AbsolutEventsPlugin.getInstance().getServer().dispatchCommand(
                    AbsolutEventsPlugin.getInstance().getServer().getConsoleSender(),
                    command
            );
        }
    }

    @Override
    public YamlConfiguration getConfig() {
        return this.config;
    }

    @Override
    public List<Player> getPlayers() {
        return this.players;
    }

    @Override
    public List<Player> getSpectators() {
        return this.spectators;
    }

    @Override
    public String getPermission() {
        return this.permission;
    }

    @Override
    public EventoType getType() {
        return this.type;
    }

    public String getIdentifier() {
        return this.identifier;
    }

    @Override
    public boolean isElimination() {
        return this.elimination;
    }

    @Override
    public boolean isHappening() {
        return this.happening;
    }

    @Override
    public boolean isOpen() {
        return this.open;
    }

    @Override
    public boolean isSpectatorAllowed() {
        return this.allowSpectator;
    }

    @Override
    public boolean requireEmptyInventory() {
        return this.emptyInventory;
    }

    @Override
    public boolean countParticipation() {
        return this.countParticipation;
    }

    @Override
    public boolean countWin() {
        return this.countWin;
    }

    protected void teleport(Player player, String location) {
        World world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;

        switch (location) {
            case "lobby":
                world = AbsolutEventsPlugin.getInstance().getServer().getWorld(config.getString("Locations.Lobby.world"));
                x = config.getDouble("Locations.Lobby.x");
                y = config.getDouble("Locations.Lobby.y");
                z = config.getDouble("Locations.Lobby.z");
                yaw = getLocationFloat("Locations.Lobby", "Yaw", "yaw");
                pitch = getLocationFloat("Locations.Lobby", "Pitch", "pitch");
                player.teleport(new Location(world, x, y, z, yaw, pitch));
                break;

            case "entrance":
                world = AbsolutEventsPlugin.getInstance().getServer().getWorld(config.getString("Locations.Entrance.world"));
                x = config.getDouble("Locations.Entrance.x");
                y = config.getDouble("Locations.Entrance.y");
                z = config.getDouble("Locations.Entrance.z");
                yaw = getLocationFloat("Locations.Entrance", "Yaw", "yaw");
                pitch = getLocationFloat("Locations.Entrance", "Pitch", "pitch");
                player.teleport(new Location(world, x, y, z, yaw, pitch));
                break;

            case "exit":
                if (this.countParticipation && !this.open) {
                    AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
                    AbsolutEventsPlugin.getInstance().getConnectionManager().addParticipation(getFileNameWithoutExtension(), player.getUniqueId());
                }

                world = AbsolutEventsPlugin.getInstance().getServer().getWorld(config.getString("Locations.Exit.world"));
                x = config.getDouble("Locations.Exit.x");
                y = config.getDouble("Locations.Exit.y");
                z = config.getDouble("Locations.Exit.z");
                yaw = getLocationFloat("Locations.Exit", "Yaw", "yaw");
                pitch = getLocationFloat("Locations.Exit", "Pitch", "pitch");
                player.teleport(new Location(world, x, y, z, yaw, pitch));
                break;

            case "spectator":
                world = AbsolutEventsPlugin.getInstance().getServer().getWorld(config.getString("Locations.Spectator.world"));
                x = config.getDouble("Locations.Spectator.x");
                y = config.getDouble("Locations.Spectator.y");
                z = config.getDouble("Locations.Spectator.z");
                yaw = getLocationFloat("Locations.Spectator", "Yaw", "yaw");
                pitch = getLocationFloat("Locations.Spectator", "Pitch", "pitch");
                player.teleport(new Location(world, x, y, z, yaw, pitch));
                break;

            default:
                break;
        }
    }

    protected List<String> getBroadcastLines(int playersAmount, int broadcastsLeft) {
        String mode = AbsolutEventsPlugin.getInstance().getConfig().getString("Broadcast.Mode", "GLOBAL");

        List<String> lines;
        if (mode.equalsIgnoreCase("GLOBAL")) {
            lines = AbsolutEventsPlugin.getInstance().getConfig().getStringList("Broadcast.Global");
        } else {
            lines = config.getStringList("Messages.Broadcast");
        }

        List<String> parsed = new ArrayList<>();
        for (String line : lines) {
            parsed.add(parseBroadcastLine(line, playersAmount, broadcastsLeft));
        }
        return parsed;
    }

    protected String parseBroadcastLine(String message, int playersAmount, int broadcastsLeft) {
        if (message == null) {
            return "";
        }

        return parseEventMessage(message)
                .replace("@players", String.valueOf(playersAmount))
                .replace("@broadcasts", String.valueOf(broadcastsLeft))
                .replace("@money", String.valueOf(money));
    }

    protected String getRewardsBroadcast() {
        List<String> broadcast = config.getStringList("Rewards.Broadcast");

        if (broadcast == null || broadcast.isEmpty()) {
            return "";
        }

        return String.join(" ", broadcast);
    }

    protected String parseEventMessage(String message) {
        if (message == null) {
            return "";
        }

        return message
                .replace("@name", config.getString("Evento.Title", "Evento"))
                .replace("@rewards", getRewardsBroadcast())
                .replace("@reward", getRewardsBroadcast());
    }

    private void removeInternal(Player player) {
        if (spectators.contains(player)) {
            spectators.remove(player);
            restoreSpectatorAfterEvent(player);
            return;
        }

        players.remove(player);
        restorePlayerAfterEvent(player);

        if (!this.open && this.elimination && players.size() == 1) {
            winner(players.get(0));
            return;
        }

        if (!this.open && players.isEmpty()) {
            for (String message : config.getStringList("Messages.No winner")) {
                broadcastComponent(MessageUtils.component(parseEventMessage(message)));
            }
            stop();
        }
    }

    private void restorePlayerAfterEvent(Player player) {
        teleport(player, "exit");

        boolean saveInventory = AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Save inventory");

        if (!saveInventory) {
            player.getInventory().clear();
            player.updateInventory();

            for (PotionEffect potion : player.getActivePotionEffects()) {
                player.removePotionEffect(potion.getType());
            }
            return;
        }

        if (player.isDead()) {
            player.spigot().respawn();
        }

        boolean restored = InventorySerializer.restoreSnapshot(player, this.identifier);

        if (!restored) {
            player.getInventory().clear();
            player.updateInventory();

            Bukkit.getConsoleSender().sendMessage(
                    ColorUtils.colorize("&#ff69f0[AbsolutEvents] " + player.getName() + " saiu do evento sem snapshot válido. Itens do evento foram limpos.")
            );

            for (PotionEffect potion : player.getActivePotionEffects()) {
                player.removePotionEffect(potion.getType());
            }
            return;
        }

        InventorySerializer.deleteSnapshot(player.getUniqueId(), this.identifier);
        player.updateInventory();

        for (PotionEffect potion : player.getActivePotionEffects()) {
            player.removePotionEffect(potion.getType());
        }
    }

    private void restoreSpectatorAfterEvent(Player player) {
        player.getInventory().clear();
        player.updateInventory();
        player.setGameMode(GameMode.SURVIVAL);
        teleport(player, "exit");
    }

    private void refreshCaches() {
        AbsolutEventsPlugin.getInstance().getCacheManager().updateCache();
        InventoryManager.reload();
    }

    private boolean isUsingBungeecordServer() {
        return this.bungeecordEnabled
                && config.getString("Locations.Server") != null
                && !config.getString("Locations.Server").equalsIgnoreCase("null");
    }

    private String getFileNameWithoutExtension() {
        return config.getString("filename", "").replace(".yml", "");
    }

    private String joinMessage(Player player) {
        return ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance().getConfig()
                        .getString("Messages.Joined", "&a@player entrou no evento.")
                        .replace("@player", player.getName())
        );
    }

    private String leaveMessage(Player player) {
        return ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance().getConfig()
                        .getString("Messages.Leave", "&c@player saiu do evento.")
                        .replace("@player", player.getName())
        );
    }

    private float getLocationFloat(String basePath, String upperKey, String lowerKey) {
        if (config.contains(basePath + "." + upperKey)) {
            return (float) config.getDouble(basePath + "." + upperKey);
        }

        if (config.contains(basePath + "." + lowerKey)) {
            return (float) config.getDouble(basePath + "." + lowerKey);
        }

        return 0.0F;
    }

    private void broadcastComponent(Component component) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
        Bukkit.getConsoleSender().sendMessage(component);
    }
}
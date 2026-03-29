package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerJoinEvent;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.hooks.BungeecordHook;
import com.absolutgg.absolutevents.listeners.eventos.GuerraListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import com.cryptomorin.xseries.messages.ActionBar;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Guerra extends Evento {

    private final YamlConfiguration config;
    private final GuerraListener listener = new GuerraListener();

    private final int enablePvp;
    private final int pickupTime;
    private final int minGuilds;
    private final int maxPlayers;
    private final int borderDelay;
    private final int borderDamage;
    private final int borderTime;
    private final int borderSize;

    private boolean pvpEnabled;
    private boolean ended;

    private final boolean actionbarEnabled;
    private final boolean borderEnabled;
    private final boolean ffa;
    private final boolean definedItems;

    private final HashMap<ClanPlayer, Clan> simpleClansClanParticipants = new HashMap<>();
    private final HashMap<OfflinePlayer, Integer> kills = new HashMap<>();

    private final WorldBorder border;
    private double currentBorderSize;

    private BukkitTask enablingActionbarTask;
    private BukkitTask normalActionbarTask;
    private BukkitTask pickupActionbarTask;
    private BukkitTask enablePvpTask;
    private BukkitTask borderTask;
    private BukkitTask borderShrinkTask;
    private BukkitTask winnerDelayTask;

    public Guerra(YamlConfiguration config) {
        super(config);

        this.config = config;
        this.enablePvp = config.getInt("Evento.Time");
        this.pickupTime = config.getInt("Evento.Pickup time");
        this.actionbarEnabled = config.getBoolean("Actionbar.Enabled");
        this.borderEnabled = config.getBoolean("Border.Enabled");
        this.definedItems = config.getBoolean("Itens.Enabled");
        this.ffa = config.getBoolean("Evento.FFA");
        this.minGuilds = config.getInt("Evento.Minimum guilds");
        this.maxPlayers = config.getInt("Evento.Maximum per guild");
        this.borderSize = config.getInt("Border.Size");
        this.borderDelay = config.getInt("Border.Delay");
        this.borderTime = config.getInt("Border.Time");
        this.borderDamage = config.getInt("Border.Damage");

        World world = Bukkit.getWorld(config.getString("Locations.Entrance.world"));
        this.border = world != null ? world.getWorldBorder() : null;
        this.currentBorderSize = borderSize;
    }

    @Override
    public void start() {
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(
                listener,
                AbsolutEventsPlugin.getInstance()
        );
        listener.setEvento();

        this.pvpEnabled = false;
        this.ended = false;

        kills.clear();
        simpleClansClanParticipants.clear();

        if (AbsolutEventsPlugin.getInstance().getSimpleClans() != null) {
            for (Player player : new ArrayList<>(getPlayers())) {
                ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                        .getSimpleClans()
                        .getClanManager()
                        .getClanPlayer(player);

                if (clanPlayer != null && clanPlayer.getClan() != null) {
                    simpleClansClanParticipants.put(clanPlayer, clanPlayer.getClan());

                    if (ffa) {
                        clanPlayer.setFriendlyFire(true);
                    }
                }
            }
        }

        if (getTotalGuilds() < this.minGuilds) {
            if (AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Enabled")
                    && config.getString("Locations.Server") != null) {
                BungeecordHook.stopEvento("noguilds");
            }

            for (String line : config.getStringList("Messages.No guilds")) {
                Bukkit.broadcastMessage(color(line)
                        .replace("@name", config.getString("Evento.Title")));
            }

            stop();
            return;
        }

        if (definedItems) {
            giveConfiguredItems();
        }

        if (borderEnabled && border != null) {
            currentBorderSize = borderSize;
            border.setSize(borderSize);
        }

        for (String line : config.getStringList("Messages.Enabling")) {
            String parsed = color(line)
                    .replace("@time", String.valueOf(enablePvp))
                    .replace("@name", config.getString("Evento.Title"));

            for (Player player : getPlayers()) {
                player.sendMessage(parsed);
            }

            for (Player player : getSpectators()) {
                player.sendMessage(parsed);
            }
        }

        startEnablingActionbar();

        enablePvpTask = Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    this.pvpEnabled = true;

                    for (String line : config.getStringList("Messages.Enabled")) {
                        String parsed = color(line)
                                .replace("@name", config.getString("Evento.Title"));

                        for (Player player : getPlayers()) {
                            player.sendMessage(parsed);
                        }

                        for (Player player : getSpectators()) {
                            player.sendMessage(parsed);
                        }
                    }

                    startNormalActionbar();

                    borderTask = Bukkit.getScheduler().runTaskLater(
                            AbsolutEventsPlugin.getInstance(),
                            () -> {
                                if (!isHappening() || ended || !borderEnabled || border == null) {
                                    return;
                                }

                                border.setDamageAmount(borderDamage);
                                startBorderShrink();
                            },
                            borderDelay * 20L
                    );
                },
                enablePvp * 20L
        );
    }

    @Override
    public void join(Player player) {
        if (getTotalGuildPlayers(player) >= this.maxPlayers) {
            player.sendMessage(
                    color(config.getString("Messages.Maximum", "&cLimite de jogadores por guilda atingido."))
                            .replace("@name", config.getString("Evento.Title"))
            );
            return;
        }

        if (AbsolutEventsPlugin.getInstance().getSimpleClans() != null) {
            ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                    .getSimpleClans()
                    .getClanManager()
                    .getClanPlayer(player);

            if (clanPlayer == null || clanPlayer.getClan() == null) {
                player.sendMessage(
                        color(config.getString("Messages.No guild", "&cVocê precisa de um clã."))
                                .replace("@name", config.getString("Evento.Title"))
                );
                return;
            }
        }

        player.setFoodLevel(20);
        getPlayers().add(player);
        teleport(player, "lobby");

        for (PotionEffect potion : player.getActivePotionEffects()) {
            player.removePotionEffect(potion.getType());
        }

        String joined = joinMessage(player);

        for (Player online : getPlayers()) {
            online.sendMessage(joined);
        }

        for (Player online : getSpectators()) {
            online.sendMessage(joined);
        }

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(joinEvent);
    }

    @Override
    public void leave(Player player) {
        if (getPlayers().contains(player)) {
            String leave = leaveMessage(player);

            for (Player online : getPlayers()) {
                online.sendMessage(leave);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(leave);
            }
        }

        eliminate(player);
    }

    public void eliminate(Player player) {
        if (AbsolutEventsPlugin.getInstance().getSimpleClans() != null) {
            ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                    .getSimpleClans()
                    .getClanManager()
                    .getClanPlayer(player);

            if (clanPlayer != null && simpleClansClanParticipants.containsKey(clanPlayer)) {
                simpleClansClanParticipants.remove(clanPlayer);

                if (ffa) {
                    clanPlayer.setFriendlyFire(false);
                }
            }
        }

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(loseEvent);

        if (definedItems) {
            clearConfiguredInventory(player);
        }

        remove(player);

        if (getTotalGuilds() == 1) {
            win();
        }
    }

    public void win() {
        this.ended = true;

        cancelTask(borderShrinkTask);

        if (borderEnabled && border != null) {
            currentBorderSize = borderSize;
            border.setSize(borderSize);
        }

        List<Player> currentWinners = new ArrayList<>(getPlayers());
        List<String> winnerNames = new ArrayList<>();

        for (Player player : currentWinners) {
            winnerNames.add(player.getName());
        }

        String winnerGuild = null;

        if (!simpleClansClanParticipants.isEmpty()) {
            Clan clan = simpleClansClanParticipants.values().stream().findFirst().orElse(null);
            if (clan != null) {
                winnerGuild = clan.getTag();
                setWinners(winnerGuild, this.kills);
            }
        }

        for (String line : config.getStringList("Messages.Pickup")) {
            String parsed = color(line)
                    .replace("@time", String.valueOf(pickupTime))
                    .replace("@name", config.getString("Evento.Title"));

            for (Player player : currentWinners) {
                player.sendMessage(parsed);
            }
        }

        for (String line : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(
                    color(line)
                            .replace("@winner", String.join(", ", winnerNames))
                            .replace("@guild", winnerGuild == null ? "" : winnerGuild)
                            .replace("@name", config.getString("Evento.Title"))
            );
        }

        if (winnerGuild != null) {
            DiscordWebhookManager.sendClanWinner(
                    winnerGuild,
                    config.getString("Evento.Title"),
                    buildTopEntries()
            );
        }

        startPickupActionbar();

        final String finalWinnerGuild = winnerGuild;
        winnerDelayTask = Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    for (Player player : currentWinners) {
                        for (String command : config.getStringList("Rewards.Commands")) {
                            executeConsoleCommand(player, command.replace("@winner", player.getName()));
                        }
                    }

                    executeTopKillCommands(finalWinnerGuild);
                    stop();
                },
                pickupTime * 20L
        );
    }

    @Override
    public void stop() {
        cancelTask(enablingActionbarTask);
        cancelTask(normalActionbarTask);
        cancelTask(pickupActionbarTask);
        cancelTask(enablePvpTask);
        cancelTask(borderTask);
        cancelTask(borderShrinkTask);
        cancelTask(winnerDelayTask);

        if (borderEnabled && border != null) {
            currentBorderSize = borderSize;
            border.setSize(borderSize);
        }

        if (definedItems) {
            for (Player player : new ArrayList<>(getPlayers())) {
                clearConfiguredInventory(player);
            }
        }

        for (ClanPlayer clanPlayer : simpleClansClanParticipants.keySet()) {
            if (ffa) {
                clanPlayer.setFriendlyFire(false);
            }
        }

        simpleClansClanParticipants.clear();
        kills.clear();
        pvpEnabled = false;
        ended = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public boolean isPvPEnabled() {
        return this.pvpEnabled;
    }

    public HashMap<OfflinePlayer, Integer> getKills() {
        return this.kills;
    }

    private void giveConfiguredItems() {
        ConfigurationSection itensSection = config.getConfigurationSection("Itens");
        if (itensSection == null) {
            return;
        }

        for (Player player : getPlayers()) {
            clearConfiguredInventory(player);
            EventKitApplier.apply(player, itensSection);
        }
    }

    private void clearConfiguredInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void startEnablingActionbar() {
        if (!actionbarEnabled) {
            return;
        }

        final int[] run = {0};

        enablingActionbarTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || pvpEnabled || enablePvp - run[0] <= 0) {
                        cancelTask(enablingActionbarTask);
                        return;
                    }

                    String message = color(
                            config.getString("Actionbar.Enabling PvP", "&ePvP em @time")
                                    .replace("@time", String.valueOf(enablePvp - run[0]))
                    );

                    for (Player player : getPlayers()) {
                        ActionBar.sendActionBar(player, message);
                    }

                    for (Player player : getSpectators()) {
                        ActionBar.sendActionBar(player, message);
                    }

                    run[0]++;
                },
                0L,
                20L
        );
    }

    private void startNormalActionbar() {
        if (!actionbarEnabled) {
            return;
        }

        normalActionbarTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || !pvpEnabled || ended) {
                        cancelTask(normalActionbarTask);
                        return;
                    }

                    for (Player player : getPlayers()) {
                        ActionBar.sendActionBar(
                                player,
                                color(
                                        config.getString("Actionbar.Normal", "&fGuildas: @guilds &fInimigos: @enemies &fRestantes: @remeaning")
                                                .replace("@guilds", String.valueOf(getTotalGuilds()))
                                                .replace("@enemies", String.valueOf(getEnemiesTotal(player)))
                                                .replace("@remeaning", String.valueOf(getPlayers().size()))
                                )
                        );
                    }

                    for (Player player : getSpectators()) {
                        ActionBar.sendActionBar(
                                player,
                                color(
                                        config.getString("Actionbar.Spectator", "&fGuildas: @guilds &fInimigos: @enemies &fRestantes: @remeaning")
                                                .replace("@guilds", String.valueOf(getTotalGuilds()))
                                                .replace("@enemies", String.valueOf(getEnemiesTotal(player)))
                                                .replace("@remeaning", String.valueOf(getPlayers().size()))
                                )
                        );
                    }
                },
                0L,
                40L
        );
    }

    private void startPickupActionbar() {
        if (!actionbarEnabled) {
            return;
        }

        final int[] run = {0};

        pickupActionbarTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || pickupTime - run[0] <= 0) {
                        cancelTask(pickupActionbarTask);
                        return;
                    }

                    String message = color(
                            config.getString("Actionbar.Pickup", "&eColeta por @time")
                                    .replace("@time", String.valueOf(pickupTime - run[0]))
                    );

                    for (Player player : getPlayers()) {
                        ActionBar.sendActionBar(player, message);
                    }

                    run[0]++;
                },
                0L,
                20L
        );
    }

    private void startBorderShrink() {
        cancelTask(borderShrinkTask);

        if (border == null) {
            return;
        }

        currentBorderSize = border.getSize();

        final double targetSize = 1.0D;
        final int totalSeconds = Math.max(1, borderTime);

        if (currentBorderSize <= targetSize) {
            border.setSize(targetSize);
            return;
        }

        final double shrinkPerSecond = (currentBorderSize - targetSize) / totalSeconds;

        borderShrinkTask = new BukkitRunnable() {
            int elapsedSeconds = 0;

            @Override
            public void run() {
                if (!isHappening() || ended || !borderEnabled || border == null) {
                    cancel();
                    return;
                }

                if (elapsedSeconds >= totalSeconds) {
                    currentBorderSize = targetSize;
                    border.setSize(targetSize);
                    cancel();
                    return;
                }

                currentBorderSize = Math.max(targetSize, currentBorderSize - shrinkPerSecond);
                border.setSize(currentBorderSize);
                elapsedSeconds++;
            }
        }.runTaskTimer(AbsolutEventsPlugin.getInstance(), 0L, 20L);
    }

    private void executeTopKillCommands(String winnerGuild) {
        ConfigurationSection topKillsCommands = config.getConfigurationSection("Rewards.Top kills");
        if (topKillsCommands == null || kills.isEmpty()) {
            return;
        }

        LinkedHashMap<OfflinePlayer, Integer> sorted = kills.entrySet()
                .stream()
                .sorted(Map.Entry.<OfflinePlayer, Integer>comparingByValue(Comparator.reverseOrder()))
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll
                );

        if (topKillsCommands.getBoolean("Winner guild") && winnerGuild != null) {
            sorted.entrySet().removeIf(entry -> !belongsToWinnerGuild(entry.getKey(), winnerGuild));
        }

        int index = 1;
        for (Map.Entry<OfflinePlayer, Integer> entry : sorted.entrySet()) {
            List<String> commands = topKillsCommands.getStringList(String.valueOf(index));
            if (commands == null || commands.isEmpty()) {
                index++;
                continue;
            }

            if (!(entry.getKey() instanceof Player onlinePlayer)) {
                index++;
                continue;
            }

            for (String command : commands) {
                executeConsoleCommand(onlinePlayer, command.replace("@topkiller", entry.getKey().getName()));
            }

            index++;
        }
    }

    private boolean belongsToWinnerGuild(OfflinePlayer player, String winnerGuild) {
        if (!(player instanceof Player onlinePlayer)) {
            return false;
        }

        if (AbsolutEventsPlugin.getInstance().getSimpleClans() == null) {
            return false;
        }

        ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                .getSimpleClans()
                .getClanManager()
                .getClanPlayer(onlinePlayer);

        return clanPlayer != null
                && clanPlayer.getClan() != null
                && winnerGuild.equals(clanPlayer.getClan().getTag());
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private int getEnemiesTotal(Player player) {
        if (AbsolutEventsPlugin.getInstance().getSimpleClans() == null) {
            return 0;
        }

        ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                .getSimpleClans()
                .getClanManager()
                .getClanPlayer(player);

        if (clanPlayer == null || clanPlayer.getClan() == null) {
            return 0;
        }

        return (int) simpleClansClanParticipants.keySet()
                .stream()
                .filter(map -> map.getClan() != clanPlayer.getClan())
                .count();
    }

    private int getTotalGuilds() {
        return (int) simpleClansClanParticipants.values().stream().distinct().count();
    }

    private int getTotalGuildPlayers(Player player) {
        if (AbsolutEventsPlugin.getInstance().getSimpleClans() == null) {
            return 0;
        }

        ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                .getSimpleClans()
                .getClanManager()
                .getClanPlayer(player);

        if (clanPlayer == null || clanPlayer.getClan() == null) {
            return 0;
        }

        return (int) simpleClansClanParticipants.keySet()
                .stream()
                .filter(map -> map.getClan() == clanPlayer.getClan())
                .count();
    }

    private String color(String text) {
        return ColorUtils.colorize(text == null ? "" : text);
    }

    private String joinMessage(Player player) {
        return color(
                AbsolutEventsPlugin.getInstance().getConfig()
                        .getString("Messages.Joined", "&a@player entrou no evento.")
                        .replace("@player", player.getName())
        );
    }

    private String leaveMessage(Player player) {
        return color(
                AbsolutEventsPlugin.getInstance().getConfig()
                        .getString("Messages.Leave", "&c@player saiu do evento.")
                        .replace("@player", player.getName())
        );
    }

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        List<DiscordWebhookManager.TopEntry> entries = new ArrayList<>();
        List<Map.Entry<OfflinePlayer, Integer>> ranking = new ArrayList<>(kills.entrySet());

        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Map.Entry<OfflinePlayer, Integer> entry = ranking.get(i);
            String name = entry.getKey().getName() == null ? "Desconhecido" : entry.getKey().getName();

            entries.add(new DiscordWebhookManager.TopEntry(
                    name,
                    String.valueOf(entry.getValue())
            ));
        }

        return entries;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
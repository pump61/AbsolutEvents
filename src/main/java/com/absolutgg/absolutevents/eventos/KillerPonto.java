package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.KillerPontoListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class KillerPonto extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final KillerPontoListener listener = new KillerPontoListener();
    private final Random random = new Random();

    private final Map<Player, Integer> points = new HashMap<>();
    private final Map<Player, Integer> kills = new HashMap<>();
    private final Set<Player> deadPlayers = new HashSet<>();
    private final Set<Player> invinciblePlayers = new HashSet<>();
    private final List<Location> respawnLocations = new ArrayList<>();

    private final int startTime;
    private final int hearts;
    private final int pointReward;
    private final int deadTime;
    private final int invincibilityTime;

    private boolean pvpEnabled;
    private int maxPoints;

    private BukkitTask actionbarTask;
    private BukkitTask enablePvpTask;

    public KillerPonto(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.startTime = config.getInt("Evento.Time");
        this.hearts = config.getInt("Evento.Hearts added");
        this.pointReward = config.getInt("Evento.Points");
        this.deadTime = config.getInt("Evento.Dead time");
        this.invincibilityTime = config.getInt("Evento.Invincibility");

        loadRespawns();
    }

    private void loadRespawns() {
        respawnLocations.clear();

        for (int i = 1; i <= 4; i++) {
            String path = "Locations.Pos" + i;

            if (!config.contains(path + ".world")) {
                continue;
            }

            World world = plugin.getServer().getWorld(config.getString(path + ".world"));
            if (world == null) {
                continue;
            }

            respawnLocations.add(new Location(
                    world,
                    config.getDouble(path + ".x"),
                    config.getDouble(path + ".y"),
                    config.getDouble(path + ".z"),
                    (float) getAngle(path, "Yaw"),
                    (float) getAngle(path, "Pitch")
            ));
        }
    }

    private double getAngle(String path, String key) {
        if (config.contains(path + "." + key)) {
            return config.getDouble(path + "." + key);
        }
        String lower = Character.toLowerCase(key.charAt(0)) + key.substring(1);
        return config.getDouble(path + "." + lower);
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        points.clear();
        kills.clear();
        deadPlayers.clear();
        invinciblePlayers.clear();

        pvpEnabled = false;

        for (Player player : getPlayers()) {
            points.put(player, 0);
            kills.put(player, 0);
            clearDeathState(player);
            applyConfiguredItems(player);
        }

        int configuredMax = config.getInt("Evento.Max points");
        this.maxPoints = configuredMax <= 0 ? Math.max(10, getPlayers().size() * 5) : configuredMax;

        for (String message : config.getStringList("Messages.Start")) {
            sendToEvent(
                    message.replace("@name", config.getString("Evento.Title"))
            );
        }

        for (String message : config.getStringList("Messages.Enabling")) {
            sendToEvent(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@time", String.valueOf(startTime))
            );
        }

        enablePvpTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening()) {
                return;
            }

            pvpEnabled = true;

            for (String message : config.getStringList("Messages.Enabled")) {
                sendToEvent(
                        message.replace("@name", config.getString("Evento.Title"))
                );
            }

            startActionbar();
        }, startTime * 20L);
    }

    @Override
    public void stop() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        if (enablePvpTask != null) {
            enablePvpTask.cancel();
            enablePvpTask = null;
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            clearDeathState(player);
            clearConfiguredInventory(player);
        }

        points.clear();
        kills.clear();
        deadPlayers.clear();
        invinciblePlayers.clear();
        pvpEnabled = false;
        maxPoints = 0;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    @Override
    public void leave(Player player) {
        clearDeathState(player);
        clearConfiguredInventory(player);

        points.remove(player);
        kills.remove(player);
        deadPlayers.remove(player);
        invinciblePlayers.remove(player);

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(loseEvent);

        super.remove(player);

        if (isHappening() && getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        } else if (isHappening() && getPlayers().isEmpty()) {
            stop();
        }
    }

    @Override
    public void winner(Player player) {
        setWinner(player);

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title", "KillerPonto"))
                            .replace("@winner", player.getName())
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title", "KillerPonto"),
                buildTopEntries()
        );

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        List<Player> losers = new ArrayList<>(getPlayers());
        losers.remove(player);

        if (plugin.getLeagueManager() != null) {
            plugin.getLeagueManager().handleSoloWin(
                    player,
                    losers,
                    "killerponto"
            );
        }

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        sendTopKills();
        stop();
    }

    public void eliminate(Player victim, Player killer) {
        if (!isHappening() || victim == null) {
            return;
        }

        deadPlayers.add(victim);

        if (killer != null && !victim.equals(killer)) {
            int killerPoints = points.getOrDefault(killer, 0) + pointReward;
            points.put(killer, killerPoints);
            kills.put(killer, kills.getOrDefault(killer, 0) + 1);

            for (String message : config.getStringList("Messages.Killed")) {
                sendToEvent(
                        message
                                .replace("@name", config.getString("Evento.Title"))
                                .replace("@victim", victim.getName())
                                .replace("@killer", killer.getName())
                                .replace("@points", String.valueOf(killerPoints))
                );
            }

            if (killerPoints >= maxPoints) {
                winner(killer);
                return;
            }
        }

        setDeadState(victim);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || !getPlayers().contains(victim)) {
                return;
            }

            deadPlayers.remove(victim);
            clearDeathState(victim);
            clearConfiguredInventory(victim);

            Location respawn = getRandomRespawn();
            if (respawn != null) {
                victim.teleport(respawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            applyConfiguredItems(victim);

            invinciblePlayers.add(victim);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isHappening()) {
                    return;
                }
                invinciblePlayers.remove(victim);
            }, invincibilityTime * 20L);

        }, deadTime * 20L);
    }

    private void startActionbar() {
        if (!config.getBoolean("Actionbar.Enabled", true)) {
            return;
        }

        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                return;
            }

            Player leader = getLeader();
            String leaderName = leader == null ? "Ninguém" : leader.getName();
            int leaderPoints = leader == null ? 0 : points.getOrDefault(leader, 0);
            int alive = getAliveCount();

            String playerFormat = config.getString(
                    "Actionbar.Message",
                    "&aVivos: &f@alive &8| &6Líder: &f@leader &7(@leaderpoints) &8| &ePontos: &f@points/@maxpoints"
            );

            String spectatorFormat = config.getString(
                    "Actionbar.Message spectator",
                    "&aVivos: &f@alive &8| &6Líder: &f@leader &7(@leaderpoints) &8| &eMeta: &f@maxpoints"
            );

            for (Player player : getPlayers()) {
                String parsed = ColorUtils.colorize(
                        playerFormat
                                .replace("@leaderpoints", String.valueOf(leaderPoints))
                                .replace("@points", String.valueOf(points.getOrDefault(player, 0)))
                                .replace("@maxpoints", String.valueOf(maxPoints))
                                .replace("@alive", String.valueOf(alive))
                                .replace("@leader", leaderName)
                );

                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(parsed));
            }

            for (Player spectator : getSpectators()) {
                String parsed = ColorUtils.colorize(
                        spectatorFormat
                                .replace("@leaderpoints", String.valueOf(leaderPoints))
                                .replace("@maxpoints", String.valueOf(maxPoints))
                                .replace("@alive", String.valueOf(alive))
                                .replace("@leader", leaderName)
                );

                spectator.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(parsed));
            }

        }, 0L, 20L);
    }

    private void applyConfiguredItems(Player player) {
        ConfigurationSection itensSection = config.getConfigurationSection("Itens");
        if (itensSection == null || !config.getBoolean("Itens.Enabled", false)) {
            return;
        }

        EventKitApplier.apply(player, itensSection);
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

    private Player getLeader() {
        Player leader = null;
        int best = -1;

        for (Map.Entry<Player, Integer> entry : points.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                leader = entry.getKey();
            }
        }

        return leader;
    }

    private void sendTopKills() {
        if (!config.getBoolean("TopKills.Enabled", true)) {
            return;
        }

        List<Map.Entry<Player, Integer>> top = new ArrayList<>(kills.entrySet());
        top.sort(Comparator.comparingInt((Map.Entry<Player, Integer> e) -> e.getValue()).reversed());

        String top1 = top.size() > 0 ? top.get(0).getKey().getName() : "Ninguém";
        String top2 = top.size() > 1 ? top.get(1).getKey().getName() : "Ninguém";
        String top3 = top.size() > 2 ? top.get(2).getKey().getName() : "Ninguém";

        String kills1 = top.size() > 0 ? String.valueOf(top.get(0).getValue()) : "0";
        String kills2 = top.size() > 1 ? String.valueOf(top.get(1).getValue()) : "0";
        String kills3 = top.size() > 2 ? String.valueOf(top.get(2).getValue()) : "0";

        List<String> format = config.getStringList("TopKills.Format");
        if (format.isEmpty()) {
            format = List.of(
                    "",
                    "&eTop abates do evento:",
                    "&f1. @top1 &7- &f@kills1",
                    "&f2. @top2 &7- &f@kills2",
                    "&f3. @top3 &7- &f@kills3",
                    ""
            );
        }

        for (String line : format) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    line
                            .replace("@top1", top1)
                            .replace("@top2", top2)
                            .replace("@top3", top3)
                            .replace("@kills1", kills1)
                            .replace("@kills2", kills2)
                            .replace("@kills3", kills3)
            ));
        }
    }

    private void setDeadState(Player player) {
        clearConfiguredInventory(player);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, deadTime * 20 + 10, 10, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, deadTime * 20 + 10, 1, false, false, false));

        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        player.getInventory().setChestplate(createBlackArmor(Material.LEATHER_CHESTPLATE));
        player.getInventory().setLeggings(createBlackArmor(Material.LEATHER_LEGGINGS));
        player.getInventory().setBoots(createBlackArmor(Material.LEATHER_BOOTS));

        player.updateInventory();
    }

    private void clearDeathState(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.updateInventory();
    }

    private ItemStack createBlackArmor(Material material) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(Color.BLACK);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Location getRandomRespawn() {
        if (respawnLocations.isEmpty()) {
            return null;
        }
        return respawnLocations.get(random.nextInt(respawnLocations.size()));
    }

    private void sendToEvent(String message) {
        String parsed = ColorUtils.colorize(message);

        for (Player player : getPlayers()) {
            player.sendMessage(parsed);
        }

        for (Player spectator : getSpectators()) {
            spectator.sendMessage(parsed);
        }
    }

    private int getAliveCount() {
        return Math.max(0, getPlayers().size() - deadPlayers.size());
    }

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        List<DiscordWebhookManager.TopEntry> entries = new ArrayList<>();
        List<Map.Entry<Player, Integer>> ranking = new ArrayList<>(kills.entrySet());

        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Map.Entry<Player, Integer> entry = ranking.get(i);
            entries.add(new DiscordWebhookManager.TopEntry(
                    entry.getKey().getName(),
                    String.valueOf(entry.getValue())
            ));
        }

        return entries;
    }

    public int getHearts() {
        return hearts;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public Map<Player, Integer> getPoints() {
        return points;
    }

    public Map<Player, Integer> getKills() {
        return kills;
    }

    public Set<Player> getDeadPlayers() {
        return deadPlayers;
    }

    public Set<Player> getInvinciblePlayers() {
        return invinciblePlayers;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
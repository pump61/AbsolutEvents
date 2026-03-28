package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.listeners.eventos.HunterListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XEnchantment;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Hunter extends Evento {

    private static final String BLUE_GLOW_TEAM = "ae_hunter_blue_glow";
    private static final String RED_GLOW_TEAM = "ae_hunter_red_glow";

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final HunterListener listener = new HunterListener();

    private final String blueName;
    private final String redName;

    private final Location blueSpawn;
    private final Location redSpawn;

    private final Map<Player, Integer> blueTeam = new HashMap<>();
    private final Map<Player, Integer> redTeam = new HashMap<>();
    private final Map<Player, Integer> kills = new HashMap<>();

    private final Set<Player> capturedPlayers = new HashSet<>();
    private final Set<Player> invinciblePlayers = new HashSet<>();
    private final Set<Player> antiCampMarked = new HashSet<>();
    private final Map<Player, Integer> enemyBaseCampTicks = new HashMap<>();

    private final List<ClanPlayer> simpleclansClans = new ArrayList<>();

    private final int startTime;
    private final int captureTime;
    private final int invincibilityTime;
    private final int pointsPerCapture;
    private final int configuredMaxPoints;

    private final boolean antiCamperEnabled;
    private final double antiCamperRadius;
    private final int antiCamperTimeSeconds;

    private boolean pvpEnabled;
    private boolean teamSelected;

    private int bluePoints;
    private int redPoints;
    private int maxPoints;

    private BukkitTask actionbarTask;
    private BukkitTask antiCampTask;

    private static final ItemStack BOW = new ItemStack(Material.BOW);
    private static final ItemStack ARROW = new ItemStack(Material.ARROW, 1);

    static {
        ItemMeta meta = BOW.getItemMeta();
        if (meta != null) {
            if (XEnchantment.INFINITY.getEnchant() != null) {
                meta.addEnchant(XEnchantment.INFINITY.getEnchant(), 1, true);
            }
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                meta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            BOW.setItemMeta(meta);
        }
    }

    public Hunter(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.blueName = config.getString("Evento.Blue", "Time Azul");
        this.redName = config.getString("Evento.Red", "Time Vermelho");

        this.startTime = config.getInt("Evento.Enable PvP");
        this.captureTime = config.getInt("Evento.Capture time");
        this.invincibilityTime = config.getInt("Evento.Invincibility");
        this.pointsPerCapture = config.getInt("Evento.Points");
        this.configuredMaxPoints = config.getInt("Evento.Max points");

        this.antiCamperEnabled = config.getBoolean("Anti camper.Enabled", true);
        this.antiCamperRadius = config.getDouble("Anti camper.Radius", 20.0D);
        this.antiCamperTimeSeconds = config.getInt("Anti camper.Time", 5);

        World world = plugin.getServer().getWorld(config.getString("Locations.Pos1.world"));

        this.blueSpawn = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"),
                (float) config.getDouble("Locations.Pos1.yaw"),
                (float) config.getDouble("Locations.Pos1.pitch")
        );

        this.redSpawn = new Location(
                world,
                config.getDouble("Locations.Pos2.x"),
                config.getDouble("Locations.Pos2.y"),
                config.getDouble("Locations.Pos2.z"),
                (float) config.getDouble("Locations.Pos2.yaw"),
                (float) config.getDouble("Locations.Pos2.pitch")
        );
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        ensureGlowTeams();

        blueTeam.clear();
        redTeam.clear();
        kills.clear();
        capturedPlayers.clear();
        invinciblePlayers.clear();
        antiCampMarked.clear();
        enemyBaseCampTicks.clear();

        bluePoints = 0;
        redPoints = 0;
        pvpEnabled = false;
        teamSelected = false;

        List<Player> shuffled = new ArrayList<>(getPlayers());
        java.util.Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            Player player = shuffled.get(i);

            if (i % 2 == 0) {
                blueTeam.put(player, 0);
            } else {
                redTeam.put(player, 0);
            }

            kills.put(player, 0);
            enemyBaseCampTicks.put(player, 0);
        }

        teamSelected = true;
        maxPoints = configuredMaxPoints <= 0 ? Math.max(10, getPlayers().size() * 5) : configuredMaxPoints;

        for (Player player : getPlayers()) {
            setTeamGear(player);

            if (blueTeam.containsKey(player)) {
                player.teleport(blueSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
                sendTeamMessage(player, blueName);
            } else {
                player.teleport(redSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
                sendTeamMessage(player, redName);
            }
        }

        if (plugin.getSimpleClans() != null) {
            for (Player player : getPlayers()) {
                ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
                if (clanPlayer != null) {
                    simpleclansClans.add(clanPlayer);
                    clanPlayer.setFriendlyFire(true);
                }
            }
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
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
            startAntiCamper();

        }, startTime * 20L);
    }

    @Override
    public void stop() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        if (antiCampTask != null) {
            antiCampTask.cancel();
            antiCampTask = null;
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            clearAntiCamp(player);
            clearCapturedState(player);
            clearPlayer(player);
        }

        for (ClanPlayer clanPlayer : simpleclansClans) {
            clanPlayer.setFriendlyFire(false);
        }

        simpleclansClans.clear();
        blueTeam.clear();
        redTeam.clear();
        kills.clear();
        capturedPlayers.clear();
        invinciblePlayers.clear();
        antiCampMarked.clear();
        enemyBaseCampTicks.clear();

        bluePoints = 0;
        redPoints = 0;
        maxPoints = 0;
        pvpEnabled = false;
        teamSelected = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    @Override
    public void leave(Player player) {
        if (getPlayers().contains(player)) {
            String leaveMessage = ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            );

            for (Player online : getPlayers()) {
                online.sendMessage(leaveMessage);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(leaveMessage);
            }
        }

        disableFriendlyFire(player);
        clearAntiCamp(player);
        clearCapturedState(player);

        blueTeam.remove(player);
        redTeam.remove(player);
        kills.remove(player);
        capturedPlayers.remove(player);
        invinciblePlayers.remove(player);
        enemyBaseCampTicks.remove(player);

        clearPlayer(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        remove(player);
        leaveBungeecord(player);

        if (!isHappening()) {
            return;
        }

        if (teamSelected) {
            if (blueTeam.isEmpty() && !redTeam.isEmpty()) {
                win("red");
                return;
            }

            if (redTeam.isEmpty() && !blueTeam.isEmpty()) {
                win("blue");
                return;
            }

            if (blueTeam.isEmpty() && redTeam.isEmpty()) {
                stop();
            }
        }
    }

    public void eliminate(Player victim, Player killer) {
        if (!isHappening()) {
            return;
        }

        clearAntiCamp(victim);
        capturedPlayers.add(victim);

        if (blueTeam.containsKey(killer)) {
            bluePoints += pointsPerCapture;
        } else if (redTeam.containsKey(killer)) {
            redPoints += pointsPerCapture;
        }

        kills.put(killer, kills.getOrDefault(killer, 0) + 1);

        setCapturedState(victim);

        for (String message : config.getStringList("Messages.Eliminated")) {
            sendToEvent(
                    message.replace("@name", config.getString("Evento.Title"))
                            .replace("@player", victim.getName())
                            .replace("@killer", killer.getName())
                            .replace("@blueteam", "§9" + bluePoints)
                            .replace("@redteam", "§c" + redPoints)
            );
        }

        for (String message : config.getStringList("Messages.Capturated")) {
            victim.sendMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
                            .replace("@time", String.valueOf(captureTime))
            ));
        }

        if (blueTeam.isEmpty() && !redTeam.isEmpty()) {
            win("red");
            return;
        }

        if (redTeam.isEmpty() && !blueTeam.isEmpty()) {
            win("blue");
            return;
        }

        if (bluePoints >= maxPoints) {
            win("blue");
            return;
        }

        if (redPoints >= maxPoints) {
            win("red");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || !getPlayers().contains(victim)) {
                return;
            }

            capturedPlayers.remove(victim);
            clearCapturedState(victim);
            enemyBaseCampTicks.put(victim, 0);

            if (blueTeam.containsKey(victim)) {
                victim.teleport(blueSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                victim.teleport(redSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            setTeamGear(victim);

            invinciblePlayers.add(victim);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isHappening()) {
                    return;
                }
                invinciblePlayers.remove(victim);
            }, invincibilityTime * 20L);

        }, captureTime * 20L);
    }

    public void win(String team) {
        String winnerTeam = team.equalsIgnoreCase("blue") ? blueName : redName;
        int winnerPoints = team.equalsIgnoreCase("blue") ? bluePoints : redPoints;

        for (String message : config.getStringList("Messages.Win")) {
            sendToEvent(
                    message.replace("@name", config.getString("Evento.Title"))
                            .replace("@team", winnerTeam)
                            .replace("@points", String.valueOf(winnerPoints))
            );
        }

        Set<Player> winners = new HashSet<>(
                team.equalsIgnoreCase("blue") ? blueTeam.keySet() : redTeam.keySet()
        );

        setWinners(winners);

        for (Player winner : winners) {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(winner, command.replace("@winner", winner.getName()));
            }
        }

        List<String> winnerNames = winners.stream().map(Player::getName).toList();

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
                            .replace("@winner", String.join(", ", winnerNames))
            ));
        }

        sendTopKills();
        stop();
    }

    private void startActionbar() {
        if (!config.getBoolean("Actionbar.Enabled", true)) {
            return;
        }

        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                return;
            }

            String format = config.getString(
                    "Actionbar.Message",
                    "&9Azul: &f@blueteam/@maxpoints &8| &cVermelho: &f@redteam/@maxpoints"
            );

            String parsed = ColorUtils.colorize(
                    format.replace("@blueteam", String.valueOf(bluePoints))
                            .replace("@redteam", String.valueOf(redPoints))
                            .replace("@maxpoints", String.valueOf(maxPoints))
            );

            for (Player player : getPlayers()) {
                player.sendActionBar(parsed);
            }

            for (Player spectator : getSpectators()) {
                spectator.sendActionBar(parsed);
            }

        }, 0L, 20L);
    }

    private void startAntiCamper() {
        if (!antiCamperEnabled) {
            return;
        }

        antiCampTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                return;
            }

            for (Player player : new ArrayList<>(getPlayers())) {
                if (capturedPlayers.contains(player)) {
                    clearAntiCamp(player);
                    continue;
                }

                if (isNearEnemyBase(player)) {
                    int ticks = enemyBaseCampTicks.getOrDefault(player, 0) + 1;
                    enemyBaseCampTicks.put(player, ticks);

                    if (ticks >= antiCamperTimeSeconds) {
                        applyAntiCampGlow(player);
                    }
                } else {
                    clearAntiCamp(player);
                }
            }
        }, 20L, 20L);
    }

    private boolean isNearEnemyBase(Player player) {
        Location location = player.getLocation();

        if (blueTeam.containsKey(player)) {
            return isWithinRadius(location, redSpawn, antiCamperRadius);
        }

        if (redTeam.containsKey(player)) {
            return isWithinRadius(location, blueSpawn, antiCamperRadius);
        }

        return false;
    }

    private boolean isWithinRadius(Location a, Location b, double radius) {
        if (a.getWorld() == null || b.getWorld() == null) {
            return false;
        }

        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }

        return a.distanceSquared(b) <= radius * radius;
    }

    private void ensureGlowTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team blueGlow = scoreboard.getTeam(BLUE_GLOW_TEAM);
        if (blueGlow == null) {
            blueGlow = scoreboard.registerNewTeam(BLUE_GLOW_TEAM);
        }
        blueGlow.setColor(ChatColor.BLUE);
        blueGlow.setAllowFriendlyFire(true);
        blueGlow.setCanSeeFriendlyInvisibles(false);

        Team redGlow = scoreboard.getTeam(RED_GLOW_TEAM);
        if (redGlow == null) {
            redGlow = scoreboard.registerNewTeam(RED_GLOW_TEAM);
        }
        redGlow.setColor(ChatColor.RED);
        redGlow.setAllowFriendlyFire(true);
        redGlow.setCanSeeFriendlyInvisibles(false);
    }

    private void applyAntiCampGlow(Player player) {
        if (antiCampMarked.contains(player)) {
            return;
        }

        antiCampMarked.add(player);
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60 * 60, 0, false, false, true));

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team blueGlow = scoreboard.getTeam(BLUE_GLOW_TEAM);
        Team redGlow = scoreboard.getTeam(RED_GLOW_TEAM);

        if (blueGlow != null) {
            blueGlow.removeEntry(player.getName());
        }
        if (redGlow != null) {
            redGlow.removeEntry(player.getName());
        }

        if (blueTeam.containsKey(player) && blueGlow != null) {
            blueGlow.addEntry(player.getName());
        } else if (redTeam.containsKey(player) && redGlow != null) {
            redGlow.addEntry(player.getName());
        }
    }

    private void clearAntiCamp(Player player) {
        enemyBaseCampTicks.put(player, 0);

        if (!antiCampMarked.remove(player)) {
            return;
        }

        player.removePotionEffect(PotionEffectType.GLOWING);

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team blueGlow = scoreboard.getTeam(BLUE_GLOW_TEAM);
        Team redGlow = scoreboard.getTeam(RED_GLOW_TEAM);

        if (blueGlow != null) {
            blueGlow.removeEntry(player.getName());
        }
        if (redGlow != null) {
            redGlow.removeEntry(player.getName());
        }
    }

    private void sendTopKills() {
        if (!config.getBoolean("Top kills.Enabled", true)) {
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

        List<String> format = config.getStringList("Top kills.Format");
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
                    line.replace("@top1", top1)
                            .replace("@top2", top2)
                            .replace("@top3", top3)
                            .replace("@kills1", kills1)
                            .replace("@kills2", kills2)
                            .replace("@kills3", kills3)
            ));
        }
    }

    private void sendTeamMessage(Player player, String teamName) {
        for (String message : config.getStringList("Messages.Team")) {
            player.sendMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
                            .replace("@team", teamName)
                            .replace("@time", String.valueOf(startTime))
            ));
        }
    }

    private void setTeamGear(Player player) {
        if (!isHappening() || !teamSelected) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setItem(0, BOW.clone());
        player.getInventory().setItem(8, ARROW.clone());

        Color color = redTeam.containsKey(player) ? Color.RED : Color.BLUE;

        player.getInventory().setHelmet(createLeatherPiece(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(createLeatherPiece(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(createLeatherPiece(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(createLeatherPiece(Material.LEATHER_BOOTS, color));
        player.updateInventory();
    }

    private void setCapturedState(Player player) {
        player.getInventory().clear();
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, captureTime * 20 + 10, 10, false, false, false));

        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        player.getInventory().setChestplate(createLeatherPiece(Material.LEATHER_CHESTPLATE, Color.BLACK));
        player.getInventory().setLeggings(createLeatherPiece(Material.LEATHER_LEGGINGS, Color.BLACK));
        player.getInventory().setBoots(createLeatherPiece(Material.LEATHER_BOOTS, Color.BLACK));
        player.updateInventory();
    }

    private void clearCapturedState(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    private ItemStack createLeatherPiece(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setColor(color);

            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                meta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void disableFriendlyFire(Player player) {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
        simpleclansClans.remove(clanPlayer);

        if (clanPlayer != null) {
            clanPlayer.setFriendlyFire(false);
        }
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

    public Set<Player> getCapturedPlayers() {
        return capturedPlayers;
    }

    public Set<Player> getInvinciblePlayers() {
        return invinciblePlayers;
    }

    public Map<Player, Integer> getBlueTeam() {
        return blueTeam;
    }

    public Map<Player, Integer> getRedTeam() {
        return redTeam;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.TDMListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TeamDeathmatch extends Evento {

    private static final String BLUE_GLOW_TEAM = "ae_tdm_blue_glow";
    private static final String RED_GLOW_TEAM = "ae_tdm_red_glow";

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final TDMListener listener = new TDMListener();

    private final String blueName;
    private final String redName;

    private final Location blueSpawn;
    private final Location redSpawn;

    private final Map<Player, Integer> blueTeam = new HashMap<>();
    private final Map<Player, Integer> redTeam = new HashMap<>();
    private final Map<Player, Integer> kills = new HashMap<>();

    private final Set<Player> deadPlayers = new HashSet<>();
    private final Set<Player> invinciblePlayers = new HashSet<>();
    private final Set<Player> antiCampPlayers = new HashSet<>();
    private final Map<Player, Integer> enemyBaseTicks = new HashMap<>();

    private final int startTime;
    private final int killedWaitTime;
    private final int invincibilityTime;
    private final int pointsPerKill;
    private final int configuredMaxPoints;
    private final double hearts;

    private final boolean antiCamperEnabled;
    private final double antiCamperRadius;
    private final int antiCamperTime;

    private int bluePoints;
    private int redPoints;
    private int maxPoints;

    private boolean pvpEnabled;
    private boolean teamSelected;
    private boolean ending;

    private BukkitTask startTask;
    private BukkitTask actionbarTask;
    private BukkitTask antiCampTask;

    private static final ItemStack SWORD = new ItemStack(Material.STONE_SWORD);
    private static final ItemStack FOOD = new ItemStack(XMaterial.COOKED_BEEF.parseMaterial(), 32);

    static {
        ItemMeta swordMeta = SWORD.getItemMeta();
        if (swordMeta != null) {
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                swordMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            swordMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            SWORD.setItemMeta(swordMeta);
        }
    }

    public TeamDeathmatch(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.blueName = config.getString("Evento.Blue", "Azul");
        this.redName = config.getString("Evento.Red", "Vermelho");
        this.startTime = config.getInt("Evento.Enable PvP");
        this.killedWaitTime = config.getInt("Evento.Dead time");
        this.invincibilityTime = config.getInt("Evento.Invincibility");
        this.pointsPerKill = config.getInt("Evento.Points");
        this.configuredMaxPoints = config.getInt("Evento.Max points");
        this.hearts = config.getDouble("Evento.Hearts added");

        this.antiCamperEnabled = config.getBoolean("Anti camper.Enabled", true);
        this.antiCamperRadius = config.getDouble("Anti camper.Radius", 18.0D);
        this.antiCamperTime = config.getInt("Anti camper.Time", 5);

        World world = plugin.getServer().getWorld(config.getString("Locations.Pos1.world"));

        this.blueSpawn = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"),
                (float) config.getDouble("Locations.Pos1.Yaw"),
                (float) config.getDouble("Locations.Pos1.Pitch")
        );

        this.redSpawn = new Location(
                world,
                config.getDouble("Locations.Pos2.x"),
                config.getDouble("Locations.Pos2.y"),
                config.getDouble("Locations.Pos2.z"),
                (float) config.getDouble("Locations.Pos2.Yaw"),
                (float) config.getDouble("Locations.Pos2.Pitch")
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
        deadPlayers.clear();
        invinciblePlayers.clear();
        antiCampPlayers.clear();
        enemyBaseTicks.clear();

        bluePoints = 0;
        redPoints = 0;
        pvpEnabled = false;
        teamSelected = false;
        ending = false;

        List<Player> shuffled = new ArrayList<>(getPlayers());
        java.util.Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            Player player = shuffled.get(i);

            resetPlayerState(player);

            if (i % 2 == 0) {
                blueTeam.put(player, 0);
            } else {
                redTeam.put(player, 0);
            }

            kills.put(player, 0);
            enemyBaseTicks.put(player, 0);
        }

        teamSelected = true;
        maxPoints = configuredMaxPoints <= 0 ? Math.max(10, getPlayers().size() * 5) : configuredMaxPoints;

        for (Player player : getPlayers()) {
            setGear(player);

            if (blueTeam.containsKey(player)) {
                player.teleport(blueSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
                sendTeamMessage(player, blueName);
            } else {
                player.teleport(redSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
                sendTeamMessage(player, redName);
            }
        }

        startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            pvpEnabled = true;

            for (String message : config.getStringList("Messages.Enabled")) {
                sendToEvent(
                        ColorUtils.colorize(
                                message.replace("@name", config.getString("Evento.Title"))
                        )
                );
            }

            for (Player player : getPlayers()) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.1F);
                player.sendTitle(
                        ColorUtils.colorize("&a&lPvP LIBERADO"),
                        ColorUtils.colorize("&fBoa sorte!"),
                        5, 30, 10
                );
            }

            startActionbar();
            startAntiCamper();

        }, startTime * 20L);
    }

    @Override
    public void leave(Player player) {
        if (ending) {
            return;
        }

        if (getPlayers().contains(player)) {
            String message = ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            );

            for (Player online : getPlayers()) {
                online.sendMessage(message);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(message);
            }
        }

        clearGlow(player);

        blueTeam.remove(player);
        redTeam.remove(player);
        kills.remove(player);
        deadPlayers.remove(player);
        invinciblePlayers.remove(player);
        antiCampPlayers.remove(player);
        enemyBaseTicks.remove(player);

        clearPlayer(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        remove(player);

        if (!isHappening()) {
            return;
        }

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

    public void handleKill(Player victim, Player killer) {
        if (!isHappening() || ending || !teamSelected) {
            return;
        }

        if (!getPlayers().contains(victim) || !getPlayers().contains(killer)) {
            return;
        }

        if (deadPlayers.contains(victim)) {
            return;
        }

        deadPlayers.add(victim);
        clearGlow(victim);

        if (blueTeam.containsKey(killer)) {
            blueTeam.put(killer, blueTeam.get(killer) + pointsPerKill);
            bluePoints += pointsPerKill;
        } else if (redTeam.containsKey(killer)) {
            redTeam.put(killer, redTeam.get(killer) + pointsPerKill);
            redPoints += pointsPerKill;
        }

        kills.put(killer, kills.getOrDefault(killer, 0) + 1);

        double maxHealth = killer.getMaxHealth();
        killer.setHealth(Math.min(maxHealth, killer.getHealth() + hearts));

        victim.setHealth(victim.getMaxHealth());
        victim.setFoodLevel(20);
        victim.setFireTicks(0);
        victim.setFallDistance(0.0F);
        victim.setVelocity(new Vector(0, 0, 0));
        victim.setGameMode(GameMode.SURVIVAL);
        victim.getInventory().clear();
        setDeadGear(victim);

        if (blueTeam.containsKey(victim)) {
            victim.teleport(blueSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } else {
            victim.teleport(redSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, killedWaitTime * 20 + 10, 10, false, false, false));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, killedWaitTime * 20 + 10, 1, false, false, false));

        for (String message : config.getStringList("Messages.Eliminated")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@player", victim.getName())
                            .replace("@killer", killer.getName())
                            .replace("@blueteam", "§9" + bluePoints)
                            .replace("@redteam", "§c" + redPoints)
            ));
        }

        for (String message : config.getStringList("Messages.Killed")) {
            victim.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@time", String.valueOf(killedWaitTime))
                            .replace("@blueteam", "§9" + bluePoints)
                            .replace("@redteam", "§c" + redPoints)
            ));
        }

        victim.playSound(victim.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.9F);
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.15F);

        if (maxPoints > 0) {
            if (bluePoints >= maxPoints) {
                win("blue");
                return;
            }

            if (redPoints >= maxPoints) {
                win("red");
                return;
            }
        }

        startRespawnCountdown(victim);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending || !getPlayers().contains(victim)) {
                return;
            }

            deadPlayers.remove(victim);
            victim.removePotionEffect(PotionEffectType.BLINDNESS);
            victim.removePotionEffect(PotionEffectType.SLOWNESS);

            setGear(victim);

            if (blueTeam.containsKey(victim)) {
                victim.teleport(blueSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            } else {
                victim.teleport(redSpawn, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            invinciblePlayers.add(victim);
            victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
            victim.sendTitle(
                    ColorUtils.colorize("&a&lRESPAWN"),
                    ColorUtils.colorize("&fInvencível por " + invincibilityTime + "s"),
                    5, 30, 10
            );

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isHappening()) {
                    return;
                }

                invinciblePlayers.remove(victim);
            }, invincibilityTime * 20L);

        }, killedWaitTime * 20L);
    }

    public void win(String team) {
        if (!teamSelected || ending) {
            return;
        }

        ending = true;

        Set<Player> winnersPlayers = new HashSet<>();
        String teamName;
        int teamPoints;

        if (team.equalsIgnoreCase("blue")) {
            teamName = blueName;
            teamPoints = bluePoints;
            winnersPlayers.addAll(blueTeam.keySet());
        } else {
            teamName = redName;
            teamPoints = redPoints;
            winnersPlayers.addAll(redTeam.keySet());
        }

        for (String message : config.getStringList("Messages.Win")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@team", teamName)
                            .replace("@points", String.valueOf(teamPoints))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        setWinners(winnersPlayers);

        // 🔥 REGISTRA VITÓRIA PARA TODOS DO TIME
        for (Player player : winnersPlayers) {
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        List<String> rewardNames = new ArrayList<>();
        for (Player player : winnersPlayers) {
            rewardNames.add(player.getName());
        }

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", String.join(", ", rewardNames))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        sendTopKills();

        DiscordWebhookManager.sendTeamWinner(teamName, config.getString("Evento.Title"), buildTopEntries());

        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String winnerName : rewardNames) {
                Player online = Bukkit.getPlayerExact(winnerName);
                if (online == null || !online.isOnline()) {
                    continue;
                }

                for (String command : config.getStringList("Rewards.Commands")) {
                    executeConsoleCommand(online, command.replace("@winner", online.getName()));
                }
            }
        }, 5L);
    }

    @Override
    public void stop() {
        if (startTask != null) {
            startTask.cancel();
            startTask = null;
        }

        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        if (antiCampTask != null) {
            antiCampTask.cancel();
            antiCampTask = null;
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            clearGlow(player);
            clearPlayer(player);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.GLOWING);
        }

        blueTeam.clear();
        redTeam.clear();
        kills.clear();
        deadPlayers.clear();
        invinciblePlayers.clear();
        antiCampPlayers.clear();
        enemyBaseTicks.clear();
        bluePoints = 0;
        redPoints = 0;
        maxPoints = 0;
        pvpEnabled = false;
        teamSelected = false;
        ending = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
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
                    format
                            .replace("@blueteam", String.valueOf(bluePoints))
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
            if (!isHappening() || ending) {
                return;
            }

            for (Player player : new ArrayList<>(getPlayers())) {
                if (deadPlayers.contains(player)) {
                    clearAntiCamp(player);
                    continue;
                }

                if (isNearEnemyBase(player)) {
                    int ticks = enemyBaseTicks.getOrDefault(player, 0) + 1;
                    enemyBaseTicks.put(player, ticks);

                    if (ticks >= antiCamperTime) {
                        applyAntiCamp(player);
                    }
                } else {
                    clearAntiCamp(player);
                }
            }

        }, 20L, 20L);
    }

    private boolean isNearEnemyBase(Player player) {
        if (blueTeam.containsKey(player)) {
            return isInsideRadius(player.getLocation(), redSpawn, antiCamperRadius);
        }

        if (redTeam.containsKey(player)) {
            return isInsideRadius(player.getLocation(), blueSpawn, antiCamperRadius);
        }

        return false;
    }

    private boolean isInsideRadius(Location a, Location b, double radius) {
        if (a.getWorld() == null || b.getWorld() == null) {
            return false;
        }

        if (!a.getWorld().equals(b.getWorld())) {
            return false;
        }

        return a.distanceSquared(b) <= radius * radius;
    }

    private void applyAntiCamp(Player player) {
        if (!antiCampPlayers.add(player)) {
            return;
        }

        applyGlow(player);
    }

    private void clearAntiCamp(Player player) {
        enemyBaseTicks.put(player, 0);

        if (!antiCampPlayers.remove(player)) {
            return;
        }

        clearGlow(player);
    }

    private void startRespawnCountdown(Player victim) {
        final int[] timeLeft = {killedWaitTime};
        final BukkitTask[] taskHolder = new BukkitTask[1];

        taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || !getPlayers().contains(victim) || ending || !deadPlayers.contains(victim)) {
                if (taskHolder[0] != null) {
                    taskHolder[0].cancel();
                }
                return;
            }

            if (timeLeft[0] <= 0) {
                if (taskHolder[0] != null) {
                    taskHolder[0].cancel();
                }
                return;
            }

            victim.sendTitle(
                    ColorUtils.colorize("&c&lVOCÊ MORREU"),
                    ColorUtils.colorize("&fRespawn em &e" + timeLeft[0] + "s"),
                    0, 20, 0
            );

            timeLeft[0]--;
        }, 0L, 20L);
    }

    private void ensureGlowTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team blueGlow = scoreboard.getTeam(BLUE_GLOW_TEAM);
        if (blueGlow == null) {
            blueGlow = scoreboard.registerNewTeam(BLUE_GLOW_TEAM);
        }
        blueGlow.setColor(ChatColor.BLUE);

        Team redGlow = scoreboard.getTeam(RED_GLOW_TEAM);
        if (redGlow == null) {
            redGlow = scoreboard.registerNewTeam(RED_GLOW_TEAM);
        }
        redGlow.setColor(ChatColor.RED);
    }

    private void applyGlow(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team blueGlow = scoreboard.getTeam(BLUE_GLOW_TEAM);
        Team redGlow = scoreboard.getTeam(RED_GLOW_TEAM);

        if (blueGlow != null) {
            blueGlow.removeEntry(player.getName());
        }
        if (redGlow != null) {
            redGlow.removeEntry(player.getName());
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60 * 60, 0, false, false, true));

        if (blueTeam.containsKey(player) && blueGlow != null) {
            blueGlow.addEntry(player.getName());
        } else if (redTeam.containsKey(player) && redGlow != null) {
            redGlow.addEntry(player.getName());
        }
    }

    private void clearGlow(Player player) {
        if (antiCampPlayers.contains(player)) {
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

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        if (!config.getBoolean("Top kills.Enabled", true)) {
            return List.of();
        }

        List<Map.Entry<Player, Integer>> top = new ArrayList<>(kills.entrySet());
        top.sort(Comparator.comparingInt((Map.Entry<Player, Integer> e) -> e.getValue()).reversed());

        List<DiscordWebhookManager.TopEntry> entries = new ArrayList<>();

        for (int i = 0; i < Math.min(3, top.size()); i++) {
            Map.Entry<Player, Integer> entry = top.get(i);
            entries.add(new DiscordWebhookManager.TopEntry(entry.getKey().getName(), String.valueOf(entry.getValue())));
        }

        return entries;
    }

    private void sendTeamMessage(Player player, String teamName) {
        for (String message : config.getStringList("Messages.Team")) {
            player.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@team", teamName)
                            .replace("@time", String.valueOf(startTime))
            ));
        }
    }

    private void setGear(Player player) {
        if (!isHappening() || !teamSelected) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setItem(0, SWORD.clone());
        player.getInventory().setItem(1, FOOD.clone());

        Color color = redTeam.containsKey(player) ? Color.RED : Color.BLUE;

        player.getInventory().setHelmet(createLeatherPiece(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(createLeatherPiece(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(createLeatherPiece(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(createLeatherPiece(Material.LEATHER_BOOTS, color));
        player.updateInventory();
    }

    private void setDeadGear(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
        player.getInventory().setChestplate(createLeatherPiece(Material.LEATHER_CHESTPLATE, Color.BLACK));
        player.getInventory().setLeggings(createLeatherPiece(Material.LEATHER_LEGGINGS, Color.BLACK));
        player.getInventory().setBoots(createLeatherPiece(Material.LEATHER_BOOTS, Color.BLACK));
        player.updateInventory();
    }

    private ItemStack createLeatherPiece(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setColor(color);

            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                meta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }

            if (XEnchantment.PROTECTION.getEnchant() != null) {
                meta.addEnchant(XEnchantment.PROTECTION.getEnchant(), 1, true);
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

    private void resetPlayerState(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setFoodLevel(20);
        player.setHealth(player.getMaxHealth());
        player.setVelocity(new Vector(0, 0, 0));
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.GLOWING);
        clearGlow(player);
        clearPlayer(player);
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

    public Set<Player> getDeadPlayers() {
        return deadPlayers;
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

    public double getHearts() {
        return hearts;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
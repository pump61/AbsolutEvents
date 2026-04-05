package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.KothListener;
import com.absolutgg.absolutevents.manager.LeagueManager;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Koth extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final Cuboid dominationArea;
    private final KothListener listener = new KothListener();

    private final List<Location> respawnLocations;
    private final List<ClanPlayer> simpleclansClans = new ArrayList<>();
    private final Map<Player, Integer> playersDeaths = new HashMap<>();
    private final Map<Player, Integer> playersPoints = new HashMap<>();
    private final Map<Player, Integer> playersLevels = new HashMap<>();
    private final List<Player> deadPlayers = new ArrayList<>();
    private final List<Player> invinciblePlayers = new ArrayList<>();
    private final Map<Player, BukkitTask> awayTasks = new HashMap<>();
    private final Map<Player, Boolean> awayWarningSent = new HashMap<>();
    private final Set<UUID> playersInsideZone = new HashSet<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    private BukkitTask monitorTask;

    private final int hearts;
    private final int startTime;
    private final int addDeadTime;
    private final int deadTime;
    private final int dominationTime;
    private final int invincibilityTime;
    private final int timeAway;

    private boolean pvpEnabled;

    private ZoneState zoneState = ZoneState.NONE;
    private Player zonePlayer = null;

    private static final ItemStack SWORD;
    private static final ItemStack FOOD;

    static {
        SWORD = new ItemStack(Material.STONE_SWORD);
        FOOD = new ItemStack(XMaterial.COOKED_PORKCHOP.parseMaterial(), 5);

        ItemMeta swordMeta = SWORD.getItemMeta();
        if (swordMeta != null) {
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                swordMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            swordMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            SWORD.setItemMeta(swordMeta);
        }
    }

    private enum ZoneState {
        NONE,
        CAPTURING,
        CONTESTED
    }

    public Koth(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.startTime = config.getInt("Evento.Start time");
        this.addDeadTime = config.getInt("Evento.Increment dead time");
        this.deadTime = config.getInt("Evento.Dead time");
        this.hearts = config.getInt("Evento.Hearts added");
        this.dominationTime = config.getInt("Evento.Domination time");
        this.invincibilityTime = config.getInt("Evento.Invincibility time");
        this.timeAway = config.getInt("Evento.Time away");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Koth: Locations.Pos1.world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Koth: mundo '" + worldName + "' não está carregado.");
        }

        Location pos1 = new Location(world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"));

        Location pos2 = new Location(world,
                config.getDouble("Locations.Pos2.x"),
                config.getDouble("Locations.Pos2.y"),
                config.getDouble("Locations.Pos2.z"));

        this.dominationArea = new Cuboid(pos1, pos2);

        Location pos3 = new Location(world,
                config.getDouble("Locations.Pos3.x"),
                config.getDouble("Locations.Pos3.y"),
                config.getDouble("Locations.Pos3.z"),
                (float) config.getDouble("Locations.Pos3.Yaw"),
                (float) config.getDouble("Locations.Pos3.Pitch"));

        Location pos4 = new Location(world,
                config.getDouble("Locations.Pos4.x"),
                config.getDouble("Locations.Pos4.y"),
                config.getDouble("Locations.Pos4.z"),
                (float) config.getDouble("Locations.Pos4.Yaw"),
                (float) config.getDouble("Locations.Pos4.Pitch"));

        this.respawnLocations = new ArrayList<>(Arrays.asList(pos3, pos4));
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        pvpEnabled = false;
        zoneState = ZoneState.NONE;
        zonePlayer = null;
        playersInsideZone.clear();
        clearAllBossBars();

        if (plugin.getSimpleClans() != null) {
            for (Player player : getPlayers()) {
                ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
                if (clanPlayer != null) {
                    simpleclansClans.add(clanPlayer);
                    clanPlayer.setFriendlyFire(true);
                }
            }
        }

        Random random = new Random();
        Collections.shuffle(getPlayers());

        for (Player player : getPlayers()) {
            playersLevels.putIfAbsent(player, player.getLevel());
            playersDeaths.put(player, 0);
            playersPoints.put(player, 0);
            playersLevels.putIfAbsent(player, player.getLevel());
            awayWarningSent.put(player, false);

            player.setExp(0F);
            player.setLevel(0);
            clearPlayer(player);
            setGear(player);
            createBossBar(player);

            if (!respawnLocations.isEmpty()) {
                player.teleport(
                        respawnLocations.get(random.nextInt(respawnLocations.size())),
                        PlayerTeleportEvent.TeleportCause.PLUGIN
                );
            }
        }

        updateAllBossBarsIdle();

        for (String message : config.getStringList("Messages.Enabling")) {
            sendToEvent(
                    message
                            .replace("@time", String.valueOf(startTime))
                            .replace("@name", config.getString("Evento.Title"))
            );
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening()) {
                return;
            }

            pvpEnabled = true;

            for (String message : config.getStringList("Messages.Enabled")) {
                sendToEvent(
                        message
                                .replace("@name", config.getString("Evento.Title"))
                                .replace("@time", String.valueOf(dominationTime))
                );
            }

            monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!isHappening()) {
                    cancelTask(monitorTask);
                    return;
                }

                List<Player> playersIn = new ArrayList<>();

                for (Player player : getPlayers()) {
                    boolean inside = dominationArea.isIn(player);

                    if (inside) {
                        playersIn.add(player);

                        if (playersInsideZone.add(player.getUniqueId())) {
                            for (String message : config.getStringList("Messages.In the zone")) {
                                player.sendMessage(ColorUtils.colorize(
                                        message.replace("@name", config.getString("Evento.Title"))
                                ));
                            }
                        }

                        cancelAwayTask(player);
                        awayWarningSent.put(player, false);
                        continue;
                    }

                    playersInsideZone.remove(player.getUniqueId());

                    if (playersPoints.getOrDefault(player, 0) > 0 && !awayWarningSent.getOrDefault(player, false)) {
                        for (String message : config.getStringList("Messages.Away from the zone")) {
                            player.sendMessage(ColorUtils.colorize(
                                    message
                                            .replace("@name", config.getString("Evento.Title"))
                                            .replace("@time", String.valueOf(timeAway))
                            ));
                        }

                        awayWarningSent.put(player, true);

                        AtomicInteger counter = new AtomicInteger();
                        BukkitTask awayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                            if (!isHappening() || dominationArea.isIn(player) || !getPlayers().contains(player)) {
                                cancelAwayTask(player);
                                awayWarningSent.put(player, false);
                                return;
                            }

                            if (counter.get() >= timeAway) {
                                playersPoints.put(player, 0);
                                cancelAwayTask(player);
                                awayWarningSent.put(player, false);
                                updateAllBossBarsIdle();
                                return;
                            }

                            counter.incrementAndGet();
                        }, 0L, 20L);

                        awayTasks.put(player, awayTask);
                    }
                }

                if (playersIn.size() == 1) {
                    Player player = playersIn.get(0);

                    if (zoneState != ZoneState.CAPTURING || zonePlayer != player) {
                        zoneState = ZoneState.CAPTURING;
                        zonePlayer = player;

                        for (String message : config.getStringList("Messages.Capturing")) {
                            sendToEvent(
                                    message
                                            .replace("@name", config.getString("Evento.Title"))
                                            .replace("@player", player.getName())
                            );
                        }

                        sendConfiguredTitleToOthers(
                                player,
                                null,
                                "Title.Capturing",
                                Map.of(
                                        "@name", config.getString("Evento.Title", "Koth"),
                                        "@player", player.getName()
                                )
                        );
                    }

                    if (getPlayers().contains(player) && playersPoints.containsKey(player)) {
                        int newPoints = playersPoints.get(player) + 1;
                        playersPoints.put(player, newPoints);
                        updateAllBossBarsCapturing(player, newPoints);

                        if (newPoints >= dominationTime) {
                            winner(player);
                        }
                    }

                } else if (playersIn.size() >= 2) {
                    if (zoneState != ZoneState.CONTESTED) {
                        zoneState = ZoneState.CONTESTED;
                        zonePlayer = null;

                        for (String message : config.getStringList("Messages.Contested")) {
                            sendToEvent(
                                    message.replace("@name", config.getString("Evento.Title"))
                            );
                        }

                        sendConfiguredTitleToOthers(
                                playersIn.get(0),
                                playersIn.size() > 1 ? playersIn.get(1) : null,
                                "Title.Contested",
                                Map.of("@name", config.getString("Evento.Title", "Koth"))
                        );
                    }

                    updateAllBossBarsContested();

                } else {
                    zoneState = ZoneState.NONE;
                    zonePlayer = null;
                    updateAllBossBarsIdle();
                }

            }, 0L, 20L);
        }, startTime * 20L);
    }

    @Override
    public void stop() {
        cancelTask(monitorTask);

        for (BukkitTask task : awayTasks.values()) {
            cancelTask(task);
        }
        awayTasks.clear();

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayer(player);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);

            Integer oldLevel = playersLevels.get(player);
            if (oldLevel != null) {
                player.setLevel(oldLevel);
            }
        }

        for (ClanPlayer clanPlayer : simpleclansClans) {
            clanPlayer.setFriendlyFire(false);
        }

        clearAllBossBars();

        playersDeaths.clear();
        playersPoints.clear();
        playersLevels.clear();
        deadPlayers.clear();
        invinciblePlayers.clear();
        awayWarningSent.clear();
        playersInsideZone.clear();
        simpleclansClans.clear();
        pvpEnabled = false;
        zoneState = ZoneState.NONE;
        zonePlayer = null;

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    @Override
    public void leave(Player player) {
        disableFriendlyFire(player);
        cancelAwayTask(player);

        for (PotionEffect potion : player.getActivePotionEffects()) {
            player.removePotionEffect(potion.getType());
        }

        clearPlayer(player);

        Integer oldLevel = playersLevels.get(player);
        if (oldLevel != null) {
            player.setLevel(oldLevel);
        }

        removeBossBar(player);

        playersDeaths.remove(player);
        playersPoints.remove(player);
        playersLevels.remove(player);
        deadPlayers.remove(player);
        invinciblePlayers.remove(player);
        awayWarningSent.remove(player);
        playersInsideZone.remove(player.getUniqueId());

        super.leave(player);

        if (getPlayers().size() == 1) {
            this.winner(getPlayers().get(0));
        }
    }

    @Override
    public void join(Player player) {
        super.join(player);
        playersLevels.putIfAbsent(player, player.getLevel());
        createBossBar(player);
        updateBossBarIdle(player);
    }

    @Override
    public void winner(Player player) {
        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title")
        );

        this.setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        List<Player> losers = new ArrayList<>(getPlayers());
        losers.remove(player);

        if (plugin.getLeagueManager() != null) {
            plugin.getLeagueManager().handleSoloWin(
                    player,
                    losers,
                    "koth"
            );
        }

        this.stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }, 2L);
    }

    public void eliminate(Player player) {
        player.setExp(0F);
        player.setLevel(0);
        deadPlayers.add(player);
        player.getInventory().clear();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20F);
        playersPoints.put(player, 0);

        Random random = new Random();
        if (!respawnLocations.isEmpty()) {
            player.teleport(
                    respawnLocations.get(random.nextInt(respawnLocations.size())),
                    PlayerTeleportEvent.TeleportCause.PLUGIN
            );
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 500000, 10));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 500000, 1));

        ItemStack helmet = new ItemStack(Material.JACK_O_LANTERN, 1);

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(Color.BLACK);
            chestplate.setItemMeta(chestMeta);
        }

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        LeatherArmorMeta leggingsMeta = (LeatherArmorMeta) leggings.getItemMeta();
        if (leggingsMeta != null) {
            leggingsMeta.setColor(Color.BLACK);
            leggings.setItemMeta(leggingsMeta);
        }

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.setColor(Color.BLACK);
            boots.setItemMeta(bootsMeta);
        }

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
        player.updateInventory();

        int deathCount = playersDeaths.getOrDefault(player, 0);
        int totalDeadTime = deadTime + (deathCount * addDeadTime);

        for (String message : config.getStringList("Messages.Time dead")) {
            player.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@time", String.valueOf(totalDeadTime))
            ));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || !getPlayers().contains(player)) {
                return;
            }

            deadPlayers.remove(player);
            clearPlayer(player);
            setGear(player);

            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);

            invinciblePlayers.add(player);
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> invinciblePlayers.remove(player),
                    invincibilityTime * 20L
            );

        }, totalDeadTime * 20L);

        playersDeaths.put(player, deathCount + 1);
    }

    private void createBossBar(Player player) {
        removeBossBar(player);

        BossBar bossBar = Bukkit.createBossBar(
                ColorUtils.colorize("&6KOTH"),
                BarColor.YELLOW,
                BarStyle.SOLID
        );
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        bossBar.setProgress(0.0D);

        playerBossBars.put(player.getUniqueId(), bossBar);
    }

    private void removeBossBar(Player player) {
        BossBar bossBar = playerBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }

    private void clearAllBossBars() {
        for (BossBar bossBar : playerBossBars.values()) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
        playerBossBars.clear();
    }

    private void updateBossBarIdle(Player player) {
        BossBar bossBar = playerBossBars.get(player.getUniqueId());
        if (bossBar == null) {
            return;
        }

        String title = config.getString("BossBar.Idle", "&6KOTH &7- Aguardando captura");
        bossBar.setTitle(ColorUtils.colorize(title));
        bossBar.setColor(BarColor.YELLOW);
        bossBar.setProgress(0.0D);
    }

    private void updateAllBossBarsIdle() {
        for (Player player : getPlayers()) {
            updateBossBarIdle(player);
        }
    }

    private void updateAllBossBarsCapturing(Player capturer, int points) {
        double progress = Math.min(1.0D, (double) points / Math.max(1, dominationTime));

        for (Player player : getPlayers()) {
            BossBar bossBar = playerBossBars.get(player.getUniqueId());
            if (bossBar == null) {
                continue;
            }

            String title = config.getString("BossBar.Capturing", "&6KOTH &7- &e@player &fcapturando")
                    .replace("@player", capturer.getName());

            bossBar.setTitle(ColorUtils.colorize(title));
            bossBar.setColor(player.equals(capturer) ? BarColor.BLUE : BarColor.GREEN);
            bossBar.setProgress(progress);
        }
    }

    private void updateAllBossBarsContested() {
        for (Player player : getPlayers()) {
            BossBar bossBar = playerBossBars.get(player.getUniqueId());
            if (bossBar == null) {
                continue;
            }

            String title = config.getString("BossBar.Contested", "&6KOTH &7- &cÁrea em disputa");
            bossBar.setTitle(ColorUtils.colorize(title));
            bossBar.setColor(BarColor.RED);
            bossBar.setProgress(1.0D);
        }
    }

    private void setGear(Player player) {
        if (!isHappening()) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setItemInOffHand(null);

        ConfigurationSection itensSection = config.getConfigurationSection("Itens");
        boolean customItemsEnabled = itensSection != null && config.getBoolean("Itens.Enabled", false);

        if (customItemsEnabled) {
            EventKitApplier.apply(player, itensSection);
            return;
        }

        player.getInventory().setItem(0, SWORD.clone());
        player.getInventory().setItem(1, FOOD.clone());

        Color color = getRandomColor();

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET, 1);
        LeatherArmorMeta helmetMeta = (LeatherArmorMeta) helmet.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setColor(color);
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                helmetMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            if (XEnchantment.PROTECTION.getEnchant() != null) {
                helmetMeta.addEnchant(XEnchantment.PROTECTION.getEnchant(), 1, true);
            }
            helmetMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            helmet.setItemMeta(helmetMeta);
        }

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(color);
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                chestMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            if (XEnchantment.PROTECTION.getEnchant() != null) {
                chestMeta.addEnchant(XEnchantment.PROTECTION.getEnchant(), 1, true);
            }
            chestMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            chestplate.setItemMeta(chestMeta);
        }

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        LeatherArmorMeta leggingsMeta = (LeatherArmorMeta) leggings.getItemMeta();
        if (leggingsMeta != null) {
            leggingsMeta.setColor(color);
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                leggingsMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            if (XEnchantment.PROTECTION.getEnchant() != null) {
                leggingsMeta.addEnchant(XEnchantment.PROTECTION.getEnchant(), 1, true);
            }
            leggingsMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            leggings.setItemMeta(leggingsMeta);
        }

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.setColor(color);
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                bootsMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }
            if (XEnchantment.PROTECTION.getEnchant() != null) {
                bootsMeta.addEnchant(XEnchantment.PROTECTION.getEnchant(), 1, true);
            }
            bootsMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            boots.setItemMeta(bootsMeta);
        }

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
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

    private void cancelAwayTask(Player player) {
        BukkitTask task = awayTasks.remove(player);
        cancelTask(task);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
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

    private Color getRandomColor() {
        Color[] colors = new Color[]{
                Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
                Color.ORANGE, Color.PURPLE, Color.AQUA, Color.FUCHSIA,
                Color.LIME, Color.WHITE
        };
        return colors[new Random().nextInt(colors.length)];
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

    private void sendConfiguredTitleToOthers(Player exclude1, Player exclude2, String basePath, Map<String, String> placeholders) {
        if (!config.getBoolean("Title.Enabled", false)) {
            return;
        }

        String title = config.getString(basePath + ".Title", "");
        String subtitle = config.getString(basePath + ".Subtitle", "");

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            title = title.replace(entry.getKey(), entry.getValue());
            subtitle = subtitle.replace(entry.getKey(), entry.getValue());
        }

        title = ColorUtils.colorize(title);
        subtitle = ColorUtils.colorize(subtitle);

        int fadeIn = config.getInt("Title.FadeIn", 5);
        int stay = config.getInt("Title.Stay", 30);
        int fadeOut = config.getInt("Title.FadeOut", 5);

        for (Player player : getPlayers()) {
            if (player == exclude1 || player == exclude2) {
                continue;
            }
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }

        for (Player player : getSpectators()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public List<Location> getRespawnLocations() {
        return respawnLocations;
    }

    public List<Player> getDeadPlayers() {
        return deadPlayers;
    }

    public List<Player> getInvinciblePlayers() {
        return invinciblePlayers;
    }

    public int getHearts() {
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
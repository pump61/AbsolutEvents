package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.api.events.PlayerJoinEvent;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.BattleRoyaleListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.cryptomorin.xseries.XItemStack;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import com.absolutgg.absolutevents.utils.ArenaRestorer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class BattleRoyale extends Evento {

    private static final Pattern POS_PATTERN = Pattern.compile("Pos\\d+");

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final BattleRoyaleListener listener = new BattleRoyaleListener();

    private final int enablePvp;
    private final int maxPlayers;

    private boolean pvpEnabled;
    private boolean ended;
    private boolean borderShrinking;

    private final boolean definedItems;
    private final boolean removeBlocks;
    private final boolean multipleSpawns;
    private final boolean refillChests;
    private final boolean borderEnabled;

    private final ArrayList<ClanPlayer> simpleClansClans = new ArrayList<>();

    private final double borderStartRadius;
    private final double borderFinalRadius;
    private final int borderDelay;
    private final double borderDamage;
    private final int borderTime;

    private final List<Block> blocksToRemove = new ArrayList<>();
    private final Map<Block, Map<Integer, ItemStack>> chestSnapshots = new HashMap<>();
    private final Map<Player, Integer> kills = new HashMap<>();

    private final Map<String, Integer> tierWeights = new LinkedHashMap<>();
    private final Map<String, List<LootItem>> lootByTier = new HashMap<>();
    private final int minItemsPerChest;
    private final int maxItemsPerChest;

    private final Map<Player, WorldBorder> personalBorders = new HashMap<>();

    private int positionIndex;

    private final World eventWorld;
    private final Location centerLocation;

    private double currentRadius;
    private int pvpCountdown;
    private int borderDelayCountdown;
    private int borderShrinkCountdown;

    private BukkitTask enablePvpTask;
    private BukkitTask borderDelayTask;
    private BukkitTask borderShrinkTask;
    private BukkitTask actionbarTask;

    private static final class LootItem {
        private final Material material;
        private final int chance;
        private final int minAmount;
        private final int maxAmount;

        private LootItem(Material material, int chance, int minAmount, int maxAmount) {
            this.material = material;
            this.chance = chance;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
    }

    public BattleRoyale(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.enablePvp = Math.max(0, config.getInt("Evento.Time"));
        this.removeBlocks = config.getBoolean("Evento.Remove blocks");
        this.multipleSpawns = config.getBoolean("Evento.Multiple spawns");
        this.refillChests = config.getBoolean("Evento.Refill chests");
        this.definedItems = config.getBoolean("Itens.Enabled");
        this.borderEnabled = config.getBoolean("Border.Enabled");

        int fallbackSize = Math.max(2, config.getInt("Border.Size", 20000));
        this.borderStartRadius = Math.max(1.0D, config.getDouble("Border.Radius", fallbackSize / 2.0D));
        this.borderFinalRadius = Math.max(1.0D, config.getDouble("Border.Final radius", 10.0D));
        this.borderDelay = Math.max(0, config.getInt("Border.Delay"));
        this.borderTime = Math.max(1, config.getInt("Border.Time"));
        this.borderDamage = Math.max(0.0D, config.getDouble("Border.Damage"));

        this.positionIndex = refillChests ? 2 : 0;
        this.maxPlayers = calculateMaxPlayers();

        this.centerLocation = loadCenterLocation();
        this.eventWorld = centerLocation != null ? centerLocation.getWorld() : null;

        this.minItemsPerChest = Math.max(1, config.getInt("Loot.Min items per chest", 3));
        this.maxItemsPerChest = Math.max(this.minItemsPerChest, config.getInt("Loot.Max items per chest", 7));

        loadLootSystem();

        if (refillChests) {
            loadChestSnapshots();
        }
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        this.ended = false;
        this.pvpEnabled = enablePvp <= 0;
        this.borderShrinking = false;
        this.currentRadius = borderStartRadius;
        this.pvpCountdown = enablePvp;
        this.borderDelayCountdown = borderDelay;
        this.borderShrinkCountdown = borderTime;

        this.kills.clear();
        this.personalBorders.clear();

        for (Player player : getPlayers()) {
            kills.put(player, 0);
        }

        if (refillChests) {
            refillEventChests();
        }

        if (multipleSpawns) {
            teleportPlayersToMultipleSpawns();
        }

        if (definedItems) {
            giveConfiguredItems();
        }

        setupFriendlyFireHooks();
        applyBorderToParticipants();

        if (!pvpEnabled) {
            sendPvPStartingMessages();

            enablePvpTask = Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        if (!isHappening() || ended) {
                            return;
                        }

                        this.pvpEnabled = true;
                        this.pvpCountdown = 0;
                        sendPvPEnabledMessages();
                        startBorderDelay();
                    },
                    enablePvp * 20L
            );
        } else {
            sendPvPEnabledMessages();
            startBorderDelay();
        }

        startActionbarTask();
    }

    @Override
    public void join(Player player) {
        if (getPlayers().size() >= maxPlayers && this.multipleSpawns) {
            player.sendMessage(ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Max players", "&cMáximo de jogadores atingido.")
                            .replace("@name", config.getString("Evento.Title"))
            ));
            return;
        }

        if (requireEmptyInventory()) {
            player.getInventory().clear();
        }

        player.setFoodLevel(20);
        getPlayers().add(player);
        kills.put(player, 0);

        this.teleport(player, "lobby");

        for (PotionEffect potion : player.getActivePotionEffects()) {
            player.removePotionEffect(potion.getType());
        }

        applyBorderToPlayer(player);

        for (Player online : getPlayers()) {
            online.sendMessage(ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Joined", "&a@player entrou no evento.")
                            .replace("@player", player.getName())
            ));
        }

        for (Player online : getSpectators()) {
            online.sendMessage(ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Joined", "&a@player entrou no evento.")
                            .replace("@player", player.getName())
            ));
        }

        PlayerJoinEvent join = new PlayerJoinEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                EventoType.BATTLE_ROYALE
        );
        Bukkit.getPluginManager().callEvent(join);
    }

    @Override
    public void leave(Player player) {
        if (getPlayers().contains(player)) {
            for (Player online : getPlayers()) {
                online.sendMessage(ColorUtils.colorize(
                        plugin.getConfig()
                                .getString("Messages.Leave", "&c@player saiu do evento.")
                                .replace("@player", player.getName())
                ));
            }

            for (Player online : getSpectators()) {
                online.sendMessage(ColorUtils.colorize(
                        plugin.getConfig()
                                .getString("Messages.Leave", "&c@player saiu do evento.")
                                .replace("@player", player.getName())
                ));
            }
        }

        disablePlayerFriendlyFireHooks(player);
        clearBorderFromPlayer(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        if (definedItems) {
            clearConfiguredInventory(player);
        }

        kills.remove(player);
        this.remove(player);

        if (!ended) {
            if (getPlayers().isEmpty()) {
                noWinners();
            } else if (getPlayers().size() == 1) {
                winner(getPlayers().get(0));
            }
        }
    }

    @Override
    public void winner(Player player) {
        if (this.ended) {
            return;
        }

        this.ended = true;

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title"),
                buildTopEntries()
        );

        this.setWinner(player);
        this.stop();

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }
    }

    public void noWinners() {
        if (this.ended) {
            return;
        }

        this.ended = true;

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    @Override
    public void stop() {
        cancelTask(enablePvpTask);
        cancelTask(borderDelayTask);
        cancelTask(borderShrinkTask);
        cancelTask(actionbarTask);

        clearBorderFromParticipants();

        if (config.contains("arena.world")) {
            World world = Bukkit.getWorld(config.getString("arena.world"));

            if (world != null) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getType() == EntityType.ITEM
                            || entity.getType() == EntityType.ARROW
                            || entity.getType() == EntityType.FIREBALL
                            || entity.getType() == EntityType.SMALL_FIREBALL
                            || entity.getType() == EntityType.SNOWBALL
                            || entity.getType() == EntityType.EGG
                            || entity.getType() == EntityType.TRIDENT) {
                        entity.remove();
                    }
                }
            }
        }

        if (config.contains("arena")) {
            plugin.getLogger().info("Chamando ArenaRestorer...");
            ArenaRestorer.restore(plugin, config.getConfigurationSection("arena"));
        }

        blocksToRemove.clear();
        chestSnapshots.clear();

        for (ClanPlayer clanPlayer : simpleClansClans) {
            clanPlayer.setFriendlyFire(false);
        }
        simpleClansClans.clear();

        if (definedItems) {
            for (Player player : new ArrayList<>(getPlayers())) {
                clearConfiguredInventory(player);
            }
        }

        kills.clear();
        personalBorders.clear();

        HandlerList.unregisterAll(listener);

        this.removePlayers();
    }

        public void eliminate(Player player) {
        eliminate(player, null);
    }

    public void eliminate(Player player, Player killer) {
        if (!getPlayers().contains(player) || ended) {
            return;
        }

        if (killer != null && killer != player && getPlayers().contains(killer)) {
            registerKill(killer, player);
        }

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        clearBorderFromPlayer(player);

        remove(player);
        notifyLeave(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                getConfig().getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        kills.remove(player);

        if (getPlayers().isEmpty()) {
            noWinners();
        } else if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    private void registerKill(Player killer, Player victim) {
        int newKills = kills.getOrDefault(killer, 0) + 1;
        kills.put(killer, newKills);

        for (String message : config.getStringList("Messages.Kill")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@killer", killer.getName())
                            .replace("@victim", victim.getName())
                            .replace("@kills", String.valueOf(newKills))
            ));
        }

        if (config.contains("Messages.Killstreak." + newKills)) {
            for (String message : config.getStringList("Messages.Killstreak." + newKills)) {
                sendToEvent(ColorUtils.colorize(
                        message
                                .replace("@name", config.getString("Evento.Title"))
                                .replace("@player", killer.getName())
                                .replace("@kills", String.valueOf(newKills))
                ));
            }
        }
    }

    public boolean isPvPEnabled() {
        return this.pvpEnabled;
    }

    public List<Block> getBlocksToRemove() {
        return this.blocksToRemove;
    }

    public boolean isInsideBorder(Location location) {
        if (!borderEnabled || centerLocation == null || location == null || location.getWorld() == null) {
            return true;
        }

        if (!location.getWorld().equals(centerLocation.getWorld())) {
            return false;
        }

        double dx = Math.abs(location.getX() - centerLocation.getX());
        double dz = Math.abs(location.getZ() - centerLocation.getZ());

        return dx <= currentRadius && dz <= currentRadius;
    }

    public double getCurrentRadius() {
        return currentRadius;
    }

    public boolean isBorderEnabled() {
        return borderEnabled;
    }

    public Location getCenterLocation() {
        return centerLocation;
    }

    private void teleportPlayersToMultipleSpawns() {
        List<Player> shuffled = new ArrayList<>(getPlayers());
        Collections.shuffle(shuffled);

        int currentPositionIndex = positionIndex + 1;

        for (Player player : shuffled) {
            ConfigurationSection spawnSection = config.getConfigurationSection("Locations.Pos" + currentPositionIndex);
            if (spawnSection == null) {
                break;
            }

            World spawnWorld = Bukkit.getWorld(config.getString("Locations.Pos" + currentPositionIndex + ".world"));
            if (spawnWorld == null) {
                spawnWorld = player.getWorld();
            }

            Location teleport = new Location(
                    spawnWorld,
                    config.getDouble("Locations.Pos" + currentPositionIndex + ".x"),
                    config.getDouble("Locations.Pos" + currentPositionIndex + ".y"),
                    config.getDouble("Locations.Pos" + currentPositionIndex + ".z"),
                    (float) config.getDouble("Locations.Pos" + currentPositionIndex + ".Yaw"),
                    (float) config.getDouble("Locations.Pos" + currentPositionIndex + ".Pitch")
            );

            player.teleport(teleport);
            applyBorderToPlayer(player);
            currentPositionIndex++;
        }
    }

    private void giveConfiguredItems() {
        for (Player player : getPlayers()) {
            if (config.isConfigurationSection("Itens.Helmet")) {
                player.getInventory().setHelmet(XItemStack.deserialize(config.getConfigurationSection("Itens.Helmet")));
            }

            if (config.isConfigurationSection("Itens.Chestplate")) {
                player.getInventory().setChestplate(XItemStack.deserialize(config.getConfigurationSection("Itens.Chestplate")));
            }

            if (config.isConfigurationSection("Itens.Leggings")) {
                player.getInventory().setLeggings(XItemStack.deserialize(config.getConfigurationSection("Itens.Leggings")));
            }

            if (config.isConfigurationSection("Itens.Boots")) {
                player.getInventory().setBoots(XItemStack.deserialize(config.getConfigurationSection("Itens.Boots")));
            }

            if (config.isConfigurationSection("Itens.Offhand")) {
                player.getInventory().setItemInOffHand(XItemStack.deserialize(config.getConfigurationSection("Itens.Offhand")));
            }

            ConfigurationSection inventorySection = config.getConfigurationSection("Itens.Inventory");
            if (inventorySection != null) {
                for (String item : inventorySection.getKeys(false)) {
                    player.getInventory().setItem(
                            Integer.parseInt(item),
                            XItemStack.deserialize(config.getConfigurationSection("Itens.Inventory." + item))
                    );
                }
            }

            player.updateInventory();
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

    private void setupFriendlyFireHooks() {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        for (Player player : getPlayers()) {
            ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
            if (clanPlayer != null) {
                simpleClansClans.add(clanPlayer);
                clanPlayer.setFriendlyFire(true);
            }
        }
    }

    private void disablePlayerFriendlyFireHooks(Player player) {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
        simpleClansClans.remove(clanPlayer);
        if (clanPlayer != null) {
            clanPlayer.setFriendlyFire(false);
        }
    }

    private void sendPvPStartingMessages() {
        for (String message : config.getStringList("Messages.Enabling")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@time", String.valueOf(enablePvp))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void sendPvPEnabledMessages() {
        for (String message : config.getStringList("Messages.Enabled")) {
            sendToEvent(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void startBorderDelay() {
        if (!borderEnabled || centerLocation == null) {
            return;
        }

        borderDelayTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening() || ended) {
                        return;
                    }

                    borderShrinking = true;
                    startBorderShrink();
                },
                borderDelay * 20L
        );
    }

    private void startBorderShrink() {
        if (!borderEnabled || centerLocation == null) {
            return;
        }

        final double start = borderStartRadius;
        final double end = Math.min(borderFinalRadius, borderStartRadius);
        final double totalShrink = Math.max(0.0D, start - end);
        final double perSecond = totalShrink / borderTime;

        borderShrinkTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening() || ended) {
                        cancelTask(borderShrinkTask);
                        return;
                    }

                    if (borderShrinkCountdown > 0) {
                        borderShrinkCountdown--;
                    }

                    if (currentRadius > end) {
                        currentRadius = Math.max(end, currentRadius - perSecond);
                    }

                    applyBorderToParticipants();
                    applyBorderDamage();

                    if (borderShrinkCountdown <= 0 || currentRadius <= end) {
                        currentRadius = end;
                        applyBorderToParticipants();
                        cancelTask(borderShrinkTask);
                    }
                },
                20L,
                20L
        );
    }

    private void applyBorderDamage() {
        if (!borderEnabled || centerLocation == null || borderDamage <= 0.0D) {
            return;
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }

            if (!player.getWorld().equals(centerLocation.getWorld())) {
                continue;
            }

            if (!isInsideBorder(player.getLocation())) {
                if ((player.getHealth() - borderDamage) <= 0.0D) {
                    for (String message : config.getStringList("Messages.Border kill")) {
                        sendToEvent(ColorUtils.colorize(
                                message
                                        .replace("@name", config.getString("Evento.Title"))
                                        .replace("@player", player.getName())
                        ));
                    }

                    eliminate(player);
                    continue;
                }

                double newHealth = Math.max(0.5D, player.getHealth() - borderDamage);
                player.setHealth(newHealth);

                player.playHurtAnimation(0.0F);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0F, 1.0F);
            }
        }
    }

    private void startActionbarTask() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening() || ended) {
                        cancelTask(actionbarTask);
                        return;
                    }

                    if (borderEnabled) {
                        applyBorderDamage();
                    }

                    if (!pvpEnabled && pvpCountdown > 0) {
                        pvpCountdown--;
                    } else if (pvpEnabled && borderEnabled && !borderShrinking && borderDelayCountdown > 0) {
                        borderDelayCountdown--;
                    }

                    for (Player player : getPlayers()) {
                        int enemies = Math.max(0, getPlayers().size() - 1);
                        String timeValue;

                        if (!pvpEnabled) {
                            timeValue = pvpCountdown + "s";
                        } else if (borderEnabled && !borderShrinking) {
                            timeValue = borderDelayCountdown + "s";
                        } else if (borderEnabled) {
                            timeValue = borderShrinkCountdown + "s";
                        } else {
                            timeValue = "0s";
                        }

                        String message = config.getString(
                                "Messages.Actionbar",
                                "&cZona: &f@radius &8| &eTempo: &f@time &8| &aInimigos: &f@enemies"
                        );

                        player.sendActionBar(ColorUtils.colorize(
                                message
                                        .replace("@radius", String.valueOf((int) Math.round(currentRadius)))
                                        .replace("@time", timeValue)
                                        .replace("@enemies", String.valueOf(enemies))
                        ));
                    }
                },
                0L,
                20L
        );
    }

    private Location loadCenterLocation() {
        String path = null;

        if (config.isSet("Locations.Center.world")) {
            path = "Locations.Center";
        } else if (config.isSet("Locations.Entrance.world")) {
            path = "Locations.Entrance";
        } else if (config.isSet("Locations.Lobby.world")) {
            path = "Locations.Lobby";
        }

        if (path == null) {
            return null;
        }

        String worldName = config.getString(path + ".world");
        if (worldName == null) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(
                world,
                config.getDouble(path + ".x"),
                config.getDouble(path + ".y"),
                config.getDouble(path + ".z"),
                (float) config.getDouble(path + ".Yaw"),
                (float) config.getDouble(path + ".Pitch")
        );
    }

    private int calculateMaxPlayers() {
        ConfigurationSection locations = config.getConfigurationSection("Locations");
        if (locations == null) {
            return 0;
        }

        int posCount = 0;
        for (String key : locations.getKeys(false)) {
            if (POS_PATTERN.matcher(key).matches()) {
                posCount++;
            }
        }

        return Math.max(0, posCount - positionIndex);
    }

    private void loadChestSnapshots() {
        Cuboid cuboid = getChestCuboid();
        if (cuboid == null) {
            return;
        }

        for (Block block : cuboid.getBlocks()) {
            if (block.getType() != Material.CHEST) {
                continue;
            }

            Chest chest = (Chest) block.getState();
            Map<Integer, ItemStack> original = new HashMap<>();

            for (int i = 0; i < chest.getInventory().getSize(); i++) {
                ItemStack item = chest.getInventory().getItem(i);
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                original.put(i, item.clone());
            }

            chestSnapshots.put(block, original);
        }
    }

    private void restoreChests() {
        for (Map.Entry<Block, Map<Integer, ItemStack>> entry : chestSnapshots.entrySet()) {
            Block block = entry.getKey();
            if (block.getType() != Material.CHEST) {
                continue;
            }

            Chest chest = (Chest) block.getState();
            chest.getInventory().clear();

            for (Map.Entry<Integer, ItemStack> itemEntry : entry.getValue().entrySet()) {
                chest.getInventory().setItem(itemEntry.getKey(), itemEntry.getValue());
            }
        }
    }

    private Cuboid getChestCuboid() {
        if (!refillChests) {
            return null;
        }

        String worldName = config.getString("Locations.Pos1.world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        Location pos1 = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z")
        );

        Location pos2 = new Location(
                world,
                config.getDouble("Locations.Pos2.x"),
                config.getDouble("Locations.Pos2.y"),
                config.getDouble("Locations.Pos2.z")
        );

        return new Cuboid(pos1, pos2);
    }

    private void refillEventChests() {
        Cuboid cuboid = getChestCuboid();
        if (cuboid == null) {
            return;
        }

        for (Block block : cuboid.getBlocks()) {
            if (block.getType() != Material.CHEST) {
                continue;
            }

            Chest chest = (Chest) block.getState();
            fillChestRandom(chest);
        }
    }

    private void loadLootSystem() {
        if (!config.isConfigurationSection("Loot")) {
            return;
        }

        ConfigurationSection tiersSection = config.getConfigurationSection("Loot.Tiers");
        if (tiersSection != null) {
            for (String tier : tiersSection.getKeys(false)) {
                tierWeights.put(tier.toLowerCase(), Math.max(0, tiersSection.getInt(tier + ".Weight")));
            }
        }

        ConfigurationSection itemsSection = config.getConfigurationSection("Loot.Items");
        if (itemsSection == null) {
            return;
        }

        for (String key : itemsSection.getKeys(false)) {
            String tier = itemsSection.getString(key + ".Tier", "common").toLowerCase();
            int chance = Math.max(0, Math.min(100, itemsSection.getInt(key + ".Chance", 100)));
            int min = Math.max(1, itemsSection.getInt(key + ".Min amount", 1));
            int max = Math.max(min, itemsSection.getInt(key + ".Max amount", min));

            Material material = Material.matchMaterial(key);
            if (material == null) {
                continue;
            }

            lootByTier.computeIfAbsent(tier, ignored -> new ArrayList<>())
                    .add(new LootItem(material, chance, min, max));
        }
    }

    private void fillChestRandom(Chest chest) {
        chest.getInventory().clear();

        if (lootByTier.isEmpty() || tierWeights.isEmpty()) {
            return;
        }

        int itemsToGenerate = ThreadLocalRandom.current().nextInt(minItemsPerChest, maxItemsPerChest + 1);
        int attempts = 0;
        List<Integer> freeSlots = new ArrayList<>();

        for (int i = 0; i < chest.getInventory().getSize(); i++) {
            freeSlots.add(i);
        }

        Collections.shuffle(freeSlots);

        while (itemsToGenerate > 0 && attempts < 100 && !freeSlots.isEmpty()) {
            attempts++;

            String tier = getRandomTier();
            List<LootItem> tierItems = lootByTier.get(tier);
            if (tierItems == null || tierItems.isEmpty()) {
                continue;
            }

            LootItem chosen = tierItems.get(ThreadLocalRandom.current().nextInt(tierItems.size()));
            if (ThreadLocalRandom.current().nextInt(100) >= chosen.chance) {
                continue;
            }

            int amount = ThreadLocalRandom.current().nextInt(chosen.minAmount, chosen.maxAmount + 1);
            ItemStack stack = new ItemStack(chosen.material, amount);

            int slot = freeSlots.remove(0);
            chest.getInventory().setItem(slot, stack);
            itemsToGenerate--;
        }
    }

    private String getRandomTier() {
        int totalWeight = tierWeights.values().stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) {
            return "common";
        }

        int random = ThreadLocalRandom.current().nextInt(totalWeight);
        int current = 0;

        for (Map.Entry<String, Integer> entry : tierWeights.entrySet()) {
            current += entry.getValue();
            if (random < current) {
                return entry.getKey();
            }
        }

        return "common";
    }

    private void applyBorderToPlayer(Player player) {
        if (!borderEnabled || centerLocation == null || player == null || !player.isOnline()) {
            return;
        }

        if (!player.getWorld().equals(centerLocation.getWorld())) {
            return;
        }

        WorldBorder border = personalBorders.get(player);
        if (border == null) {
            border = Bukkit.createWorldBorder();
            personalBorders.put(player, border);
        }

        border.setCenter(centerLocation);
        border.setDamageAmount(borderDamage);
        border.setSize(Math.max(2.0D, currentRadius * 2.0D));

        player.setWorldBorder(border);
    }

    private void applyBorderToParticipants() {
        if (!borderEnabled || centerLocation == null) {
            return;
        }

        for (Player player : getPlayers()) {
            applyBorderToPlayer(player);
        }

        for (Player player : getSpectators()) {
            applyBorderToPlayer(player);
        }
    }

    private void clearBorderFromPlayer(Player player) {
        if (player == null) {
            return;
        }

        player.setWorldBorder(null);
        personalBorders.remove(player);
    }

    private void clearBorderFromParticipants() {
        for (Player player : new ArrayList<>(getPlayers())) {
            clearBorderFromPlayer(player);
        }

        for (Player player : new ArrayList<>(getSpectators())) {
            clearBorderFromPlayer(player);
        }
    }

    private void sendToEvent(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(message);
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        List<DiscordWebhookManager.TopEntry> entries = new ArrayList<>();
        List<Map.Entry<Player, Integer>> ranking = new ArrayList<>(kills.entrySet());

        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Map.Entry<Player, Integer> entry = ranking.get(i);
            if (entry.getKey() == null) {
                continue;
            }

            entries.add(new DiscordWebhookManager.TopEntry(
                    Objects.requireNonNullElse(entry.getKey().getName(), "Desconhecido"),
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
package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.MontariaListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Montaria extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final MontariaListener listener = new MontariaListener();

    private final Map<java.util.UUID, Horse> horses = new HashMap<>();
    private final Map<java.util.UUID, Integer> checkpointProgress = new HashMap<>();
    private final Map<java.util.UUID, Long> boostCooldown = new HashMap<>();
    private final Map<java.util.UUID, BukkitTask> activeBoostTasks = new HashMap<>();
    private final Map<java.util.UUID, Location> lockedStartPositions = new HashMap<>();
    private final List<Cuboid> checkpoints = new ArrayList<>();

    private Cuboid finishRegion;
    private Location startPos1;
    private Location startPos2;

    private BukkitTask countdownTask;
    private BukkitTask raceTask;

    private boolean raceStarted = false;
    private int countdown;

    private double horseJump;
    private double horseSpeed;

    private Material boostBlock;
    private int boostAmplifier;
    private int boostCooldownSeconds;
    private int boostDurationTicks;

    private boolean requireCheckpoints;

    public Montaria(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.countdown = config.getInt("Evento.Countdown", 5);
        this.horseJump = config.getDouble("Evento.Jump", 0.9D);
        this.horseSpeed = config.getDouble("Evento.Speed", 0.3D);

        String boostMaterialName = config.getString("Boost.Block", "MAGENTA_GLAZED_TERRACOTTA");
        Material parsed = Material.matchMaterial(boostMaterialName);
        this.boostBlock = parsed != null ? parsed : Material.MAGENTA_GLAZED_TERRACOTTA;

        this.boostAmplifier = config.getInt("Boost.Amplifier", 2);
        this.boostCooldownSeconds = config.getInt("Boost.Cooldown", 2);
        this.boostDurationTicks = config.getInt("Boost.Duration", 40);
        this.requireCheckpoints = config.getBoolean("Race.Require checkpoints", false);

        this.startPos1 = readLocation("Race.StartPos1", true);
        this.startPos2 = readLocation("Race.StartPos2", true);

        Location finish1 = readLocation("Race.FinishPos1", false);
        Location finish2 = readLocation("Race.FinishPos2", false);
        this.finishRegion = new Cuboid(finish1, finish2);

        loadCheckpoints();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        raceStarted = false;
        checkpointProgress.clear();
        boostCooldown.clear();
        lockedStartPositions.clear();

        cancelTask(countdownTask);
        cancelTask(raceTask);

        for (BukkitTask task : activeBoostTasks.values()) {
            cancelTask(task);
        }
        activeBoostTasks.clear();

        for (Horse horse : horses.values()) {
            try {
                if (horse != null && horse.isValid()) {
                    horse.remove();
                }
            } catch (Exception ignored) {
            }
        }
        horses.clear();

        countdown = config.getInt("Evento.Countdown", 5);

        if (getPlayers().isEmpty()) {
            stop();
            return;
        }

        spawnPlayersAndHorses();
        startCountdown();
    }

    @Override
    public void leave(Player player) {
        removeHorse(player);
        checkpointProgress.remove(player.getUniqueId());
        boostCooldown.remove(player.getUniqueId());
        lockedStartPositions.remove(player.getUniqueId());

        BukkitTask boostTask = activeBoostTasks.remove(player.getUniqueId());
        cancelTask(boostTask);

        super.leave(player);

        if (raceStarted && getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
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

        setWinner(player);
        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }, 2L);
    }

    @Override
    public void stop() {
        cancelTask(countdownTask);
        cancelTask(raceTask);

        for (BukkitTask task : activeBoostTasks.values()) {
            cancelTask(task);
        }
        activeBoostTasks.clear();

        for (Player player : new ArrayList<>(getPlayers())) {
            removeHorse(player);
        }

        horses.clear();
        checkpointProgress.clear();
        boostCooldown.clear();
        lockedStartPositions.clear();
        raceStarted = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    private void spawnPlayersAndHorses() {
        List<Player> players = new ArrayList<>(getPlayers());
        List<Location> slots = generateStartSlots(players.size());

        float yaw = startPos1.getYaw();
        float pitch = startPos1.getPitch();

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            Location slot = slots.get(i).clone();
            slot.setYaw(yaw);
            slot.setPitch(pitch);

            checkpointProgress.put(player.getUniqueId(), 0);
            lockedStartPositions.put(player.getUniqueId(), slot.clone());

            Horse horse = slot.getWorld().spawn(slot.clone(), Horse.class, spawned -> {
                spawned.setAdult();
                spawned.setTamed(true);
                spawned.setOwner(player);
                spawned.setDomestication(spawned.getMaxDomestication());
                spawned.getInventory().setSaddle(new ItemStack(Material.SADDLE));
                spawned.setJumpStrength(0.0D);
                spawned.setMaxHealth(20.0D);
                spawned.setHealth(20.0D);
                spawned.setInvulnerable(true);
                spawned.setPersistent(true);
                spawned.setRemoveWhenFarAway(false);
                spawned.setAI(false);
                spawned.setCollidable(false);
                spawned.setSilent(false);

                if (spawned.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    spawned.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0D);
                }
            });

            horse.teleport(slot.clone());
            horse.setRotation(yaw, pitch);
            horses.put(player.getUniqueId(), horse);

            player.teleport(slot.clone().add(0.0, 0.6, 0.0), PlayerTeleportEvent.TeleportCause.PLUGIN);
            startMountRoutine(player, horse);
        }
    }

    private void startMountRoutine(Player player, Horse horse) {
        final BukkitTask[] holder = new BukkitTask[1];
        final int[] tries = {0};

        holder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || !player.isOnline() || !getPlayers().contains(player)) {
                cancelTask(holder[0]);
                return;
            }

            if (horse == null || !horse.isValid() || horse.isDead()) {
                cancelTask(holder[0]);
                return;
            }

            if (player.getVehicle() == horse || horse.getPassengers().contains(player)) {
                player.setVelocity(new Vector(0, 0, 0));
                horse.setVelocity(new Vector(0, 0, 0));
                cancelTask(holder[0]);
                return;
            }

            Location base = lockedStartPositions.getOrDefault(player.getUniqueId(), horse.getLocation()).clone();
            base.setYaw(startPos1.getYaw());
            base.setPitch(startPos1.getPitch());

            horse.teleport(base);
            player.teleport(base.clone().add(0.0, 0.6, 0.0), PlayerTeleportEvent.TeleportCause.PLUGIN);

            try {
                if (player.getVehicle() != null) {
                    player.leaveVehicle();
                }
            } catch (Exception ignored) {
            }

            try {
                horse.addPassenger(player);
            } catch (Exception ignored) {
            }

            player.setVelocity(new Vector(0, 0, 0));
            horse.setVelocity(new Vector(0, 0, 0));

            tries[0]++;
            if (tries[0] >= 40) {
                cancelTask(holder[0]);
            }
        }, 1L, 1L);
    }

    private void forceMount(Player player, Horse horse) {
        if (!isHappening() || !player.isOnline() || !getPlayers().contains(player)) {
            return;
        }

        if (horse == null || !horse.isValid() || horse.isDead()) {
            return;
        }

        if (player.getVehicle() == horse || horse.getPassengers().contains(player)) {
            player.setVelocity(new Vector(0, 0, 0));
            horse.setVelocity(new Vector(0, 0, 0));
            return;
        }

        Location base = lockedStartPositions.getOrDefault(player.getUniqueId(), horse.getLocation()).clone();
        base.setYaw(startPos1.getYaw());
        base.setPitch(startPos1.getPitch());

        horse.teleport(base);
        player.teleport(base.clone().add(0.0, 0.6, 0.0), PlayerTeleportEvent.TeleportCause.PLUGIN);

        try {
            if (player.getVehicle() != null) {
                player.leaveVehicle();
            }
        } catch (Exception ignored) {
        }

        try {
            horse.addPassenger(player);
        } catch (Exception ignored) {
        }

        player.setVelocity(new Vector(0, 0, 0));
        horse.setVelocity(new Vector(0, 0, 0));
    }

    private void startCountdown() {
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                cancelTask(countdownTask);
                return;
            }

            if (countdown > 0) {
                for (Player player : getPlayers()) {
                    Horse horse = horses.get(player.getUniqueId());
                    if (horse != null && horse.isValid() && !horse.isDead()) {
                        lockPlayerAtStart(player);
                    }
                    player.sendTitle(ColorUtils.colorize("&e&l" + countdown), "");
                }
                countdown--;
                return;
            }

            for (Player player : getPlayers()) {
                player.sendTitle(ColorUtils.colorize("&a&lVAI!"), "");
            }

            raceStarted = true;

            for (Horse horse : horses.values()) {
                if (horse == null || !horse.isValid() || horse.isDead()) {
                    continue;
                }

                horse.setAI(true);
                horse.setCollidable(true);

                if (horse.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(horseSpeed);
                }

                horse.setJumpStrength(horseJump);
                horse.setVelocity(new Vector(0, 0, 0));
            }

            for (Player player : getPlayers()) {
                player.setVelocity(new Vector(0, 0, 0));
            }

            cancelTask(countdownTask);
            startRaceMonitorTask();
        }, 0L, 20L);
    }

    private void startRaceMonitorTask() {
        raceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || !raceStarted) {
                cancelTask(raceTask);
                return;
            }

            for (Player player : new ArrayList<>(getPlayers())) {
                Horse horse = horses.get(player.getUniqueId());
                if (horse == null || !horse.isValid() || horse.isDead()) {
                    continue;
                }

                checkBoost(player, horse);
                checkCheckpoint(player, horse);
                checkFinish(player, horse);
            }
        }, 1L, 1L);
    }

    public void lockPlayerAtStart(Player player) {
        Horse horse = horses.get(player.getUniqueId());
        if (horse == null || !horse.isValid() || horse.isDead()) {
            return;
        }

        Location locked = lockedStartPositions.get(player.getUniqueId());
        if (locked != null) {
            Location base = locked.clone();
            base.setYaw(startPos1.getYaw());
            base.setPitch(startPos1.getPitch());

            horse.teleport(base);
            horse.setJumpStrength(0.0D);

            if (horse.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0D);
            }
        }

        if (player.getVehicle() != horse) {
            forceMount(player, horse);
        }

        player.setVelocity(new Vector(0, 0, 0));
        horse.setVelocity(new Vector(0, 0, 0));
    }

    private void checkBoost(Player player, Horse horse) {
        Location loc = horse.getLocation();
        Material current = loc.getBlock().getType();
        Material below = loc.clone().subtract(0, 1, 0).getBlock().getType();

        if (current != boostBlock && below != boostBlock) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = boostCooldown.getOrDefault(player.getUniqueId(), 0L);

        if ((now - last) < (boostCooldownSeconds * 1000L)) {
            return;
        }

        boostCooldown.put(player.getUniqueId(), now);

        if (horse.getAttribute(Attribute.MOVEMENT_SPEED) == null) {
            return;
        }

        double normalSpeed = horseSpeed;
        double boostedSpeed = horseSpeed * (1.0D + (0.35D * boostAmplifier));

        horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(boostedSpeed);

        horse.getWorld().spawnParticle(
                Particle.CLOUD,
                horse.getLocation().add(0, 0.5, 0),
                18,
                0.35, 0.2, 0.35,
                0.02
        );

        horse.getWorld().spawnParticle(
                Particle.CRIT,
                horse.getLocation().add(0, 0.5, 0),
                10,
                0.25, 0.15, 0.25,
                0.01
        );

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_HORSE_GALLOP,
                1.0F,
                1.2F
        );

        BukkitTask oldTask = activeBoostTasks.remove(player.getUniqueId());
        cancelTask(oldTask);

        BukkitTask resetTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (horse.isValid() && !horse.isDead() && horse.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                horse.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(normalSpeed);
            }
            activeBoostTasks.remove(player.getUniqueId());
        }, boostDurationTicks);

        activeBoostTasks.put(player.getUniqueId(), resetTask);

        for (String message : config.getStringList("Messages.Boost")) {
            player.sendMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void checkCheckpoint(Player player, Horse horse) {
        if (checkpoints.isEmpty()) {
            return;
        }

        int progress = checkpointProgress.getOrDefault(player.getUniqueId(), 0);
        if (progress >= checkpoints.size()) {
            return;
        }

        Cuboid checkpoint = checkpoints.get(progress);
        Location horseLoc = horse.getLocation();
        Location horseBlock = horseLoc.getBlock().getLocation();
        Location belowHorse = horseLoc.clone().subtract(0, 1, 0).getBlock().getLocation();

        boolean inside = checkpoint.isIn(horseLoc)
                || checkpoint.isIn(horseBlock)
                || checkpoint.isIn(belowHorse);

        if (!inside) {
            return;
        }

        checkpointProgress.put(player.getUniqueId(), progress + 1);

        for (String message : config.getStringList("Messages.Checkpoint")) {
            player.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@checkpoint", String.valueOf(progress + 1))
            ));
        }
    }

    private void checkFinish(Player player, Horse horse) {
        Location horseLoc = horse.getLocation();
        Location horseBlock = horseLoc.getBlock().getLocation();
        Location belowHorse = horseLoc.clone().subtract(0, 1, 0).getBlock().getLocation();

        boolean inside = finishRegion.isIn(horseLoc)
                || finishRegion.isIn(horseBlock)
                || finishRegion.isIn(belowHorse);

        if (inside && canFinish(player)) {
            winner(player);
        }
    }

    private boolean canFinish(Player player) {
        if (!requireCheckpoints) {
            return true;
        }
        return checkpointProgress.getOrDefault(player.getUniqueId(), 0) >= checkpoints.size();
    }

    private void loadCheckpoints() {
        checkpoints.clear();

        ConfigurationSection section = config.getConfigurationSection("Checkpoints");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            if (!config.isConfigurationSection("Checkpoints." + key + ".Pos1")
                    || !config.isConfigurationSection("Checkpoints." + key + ".Pos2")) {
                continue;
            }

            Location pos1 = readLocation("Checkpoints." + key + ".Pos1", false);
            Location pos2 = readLocation("Checkpoints." + key + ".Pos2", false);
            checkpoints.add(new Cuboid(pos1, pos2));
        }
    }

    private List<Location> generateStartSlots(int amount) {
        List<Location> slots = new ArrayList<>();

        Vector start = startPos1.toVector();
        Vector end = startPos2.toVector();
        Vector line = end.clone().subtract(start);

        double lineLength = line.length();
        if (lineLength <= 0.001D) {
            slots.add(startPos1.clone());
            return slots;
        }

        Vector direction = line.clone().normalize();
        Vector center = start.clone().add(end).multiply(0.5D);

        if (amount == 1) {
            slots.add(new Location(
                    startPos1.getWorld(),
                    center.getX(),
                    center.getY(),
                    center.getZ(),
                    startPos1.getYaw(),
                    startPos1.getPitch()
            ));
            return slots;
        }

        double usableLength = lineLength * 0.60D;
        double startOffset = -usableLength / 2.0D;
        double spacing = usableLength / Math.max(1, amount - 1);

        for (int i = 0; i < amount; i++) {
            double offset = startOffset + (spacing * i);
            Vector point = center.clone().add(direction.clone().multiply(offset));

            slots.add(new Location(
                    startPos1.getWorld(),
                    point.getX(),
                    point.getY(),
                    point.getZ(),
                    startPos1.getYaw(),
                    startPos1.getPitch()
            ));
        }

        return slots;
    }

    private Location readLocation(String path, boolean withYawPitch) {
        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Montaria: " + path + ".world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Montaria: mundo '" + worldName + "' não está carregado.");
        }

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");

        if (withYawPitch) {
            float yaw = (float) config.getDouble(path + ".Yaw");
            float pitch = (float) config.getDouble(path + ".Pitch");
            return new Location(world, x, y, z, yaw, pitch);
        }

        return new Location(world, x, y, z);
    }

    public boolean isRaceStarted() {
        return raceStarted;
    }

    public boolean isEventPlayer(Player player) {
        return getPlayers().contains(player);
    }

    public Horse getHorse(Player player) {
        return horses.get(player.getUniqueId());
    }

    public void remount(Player player) {
        Horse horse = getHorse(player);
        if (horse == null || !horse.isValid() || horse.isDead()) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || !getPlayers().contains(player) || !player.isOnline()) {
                return;
            }

            if (!raceStarted) {
                lockPlayerAtStart(player);
                return;
            }

            if (player.getVehicle() == null) {
                forceMount(player, horse);
            }
        }, 1L);
    }

    private void removeHorse(Player player) {
        Horse horse = horses.remove(player.getUniqueId());

        if (horse == null) {
            return;
        }

        try {
            if (player.getVehicle() == horse) {
                player.leaveVehicle();
            }
        } catch (Exception ignored) {
        }

        try {
            horse.remove();
        } catch (Exception ignored) {
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
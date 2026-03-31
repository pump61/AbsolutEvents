package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.NexusListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Nexus extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final NexusListener listener = new NexusListener();

    private final World world;

    private final Location blueSpawn;
    private final Location redSpawn;
    private final Location blueNexusLocation;
    private final Location redNexusLocation;
    private final Location blueTower1Location;
    private final Location blueTower2Location;
    private final Location redTower1Location;
    private final Location redTower2Location;

    private EnderCrystal blueNexus;
    private EnderCrystal redNexus;
    private EnderCrystal blueTower1;
    private EnderCrystal blueTower2;
    private EnderCrystal redTower1;
    private EnderCrystal redTower2;

    private final HashMap<Player, Integer> blueTeam = new HashMap<>();
    private final HashMap<Player, Integer> redTeam = new HashMap<>();
    private final List<Player> deadPlayers = new ArrayList<>();
    private final List<Player> invinciblePlayers = new ArrayList<>();
    private final ArrayList<ClanPlayer> simpleClansClans = new ArrayList<>();

    private final Map<Player, Integer> kills = new HashMap<>();
    private final Map<String, UUID> towerTargets = new HashMap<>();

    private final int enablePvp;
    private final int respawnInterval;
    private final int nexusBaseDamage;
    private final int invincibilityTime;
    private final int nexusHealthMax;
    private final String nexusName;

    private final int towerHealthMax;
    private final int towerDamage;
    private final double towerRange;
    private final int towerAttackInterval;
    private final int towerProjectileDamage;
    private final int towerMeleeDamage;
    private final String towerName;

    private int blueNexusHealth;
    private int redNexusHealth;
    private int blueTower1Health;
    private int blueTower2Health;
    private int redTower1Health;
    private int redTower2Health;

    private final String blueName;
    private final String redName;

    private boolean pvpEnabled;
    private boolean teamSelected;
    private boolean ending;

    private BukkitTask enablePvpTask;
    private BukkitTask towerAttackTask;

    public Nexus(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.blueName = config.getString("Evento.Blue", "Time Azul");
        this.redName = config.getString("Evento.Red", "Time Vermelho");
        this.enablePvp = config.getInt("Evento.Enable PvP");
        this.respawnInterval = config.getInt("Evento.Respawn time");
        this.nexusBaseDamage = config.getInt("Evento.Damage");
        this.nexusHealthMax = config.getInt("Evento.Health");
        this.nexusName = config.getString("Evento.Nexus name", "&fNexus");
        this.invincibilityTime = config.getInt("Evento.Invincibility");

        this.towerHealthMax = config.getInt("Towers.Health", 250);
        this.towerDamage = config.getInt("Towers.Damage", 4);
        this.towerRange = config.getDouble("Towers.Range", 18.0D);
        this.towerAttackInterval = config.getInt("Towers.Attack interval", 20);
        this.towerProjectileDamage = config.getInt("Towers.Projectile damage", 15);
        this.towerMeleeDamage = config.getInt("Towers.Melee damage", 5);
        this.towerName = config.getString("Towers.Name", "&6&lTORRE &f- @team_color@team &f#@id &f(&cHP: &f@health&f)");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Nexus: Locations.Pos1.world não encontrado na config.");
        }

        this.world = plugin.getServer().getWorld(worldName);
        if (this.world == null) {
            throw new IllegalStateException("Nexus: mundo '" + worldName + "' não está carregado.");
        }

        this.blueSpawn = readLocation("Locations.Pos1", true);
        this.redSpawn = readLocation("Locations.Pos2", true);
        this.blueNexusLocation = readLocation("Locations.Pos3", false);
        this.redNexusLocation = readLocation("Locations.Pos4", false);

        this.blueTower1Location = readLocation("Locations.Tower1", false);
        this.blueTower2Location = readLocation("Locations.Tower2", false);
        this.redTower1Location = readLocation("Locations.Tower3", false);
        this.redTower2Location = readLocation("Locations.Tower4", false);

        resetHealth();
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        pvpEnabled = false;
        teamSelected = false;
        ending = false;

        resetHealth();
        clearTasks();
        removeTrackedStructures();
        removeAllExistingStructures();
        removeAllStructureLocations();

        blueTeam.clear();
        redTeam.clear();
        deadPlayers.clear();
        invinciblePlayers.clear();
        simpleClansClans.clear();
        kills.clear();
        towerTargets.clear();

        spawnStructures();
        assignTeams();
        preparePlayers();
        setupFriendlyFireHooks();
        schedulePvpEnable();
        startTowerTask();
    }

    @Override
    public void leave(Player player) {
        if (!getPlayers().contains(player)) {
            super.leave(player);
            return;
        }

        sendLeaveMessages(player);

        disablePlayerFriendlyFireHooks(player);

        blueTeam.remove(player);
        redTeam.remove(player);
        deadPlayers.remove(player);
        invinciblePlayers.remove(player);
        kills.remove(player);
        towerTargets.values().removeIf(uuid -> uuid.equals(player.getUniqueId()));

        clearPlayerInventory(player);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        super.remove(player);
        checkWinConditionByTeamEmpty();
    }

    @Override
    public void stop() {
        clearTasks();

        removeTrackedStructures();
        removeAllExistingStructures();
        removeAllStructureLocations();

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayerInventory(player);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        for (ClanPlayer clanPlayer : simpleClansClans) {
            clanPlayer.setFriendlyFire(false);
        }

        simpleClansClans.clear();
        deadPlayers.clear();
        invinciblePlayers.clear();
        blueTeam.clear();
        redTeam.clear();
        kills.clear();
        towerTargets.clear();
        pvpEnabled = false;
        teamSelected = false;
        ending = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public void eliminate(Player captured, Player killer) {
        if (!isHappening() || ending || !getPlayers().contains(captured)) {
            return;
        }

        if (killer != null && killer != captured) {
            addKill(killer);
        }

        clearPlayerInventory(captured);
        captured.setFoodLevel(20);
        captured.setHealth(captured.getMaxHealth());

        if (blueTeam.containsKey(captured)) {
            captured.teleport(blueSpawn);
        } else {
            captured.teleport(redSpawn);
        }

        deadPlayers.add(captured);

        captured.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 500000, 10, false, false));
        captured.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 500000, 1, false, false));

        equipDeadArmor(captured);

        for (String message : config.getStringList("Messages.Died")) {
            captured.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@time", String.valueOf(respawnInterval))
            ));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending || !getPlayers().contains(captured)) {
                return;
            }

            deadPlayers.remove(captured);

            captured.removePotionEffect(PotionEffectType.BLINDNESS);
            captured.removePotionEffect(PotionEffectType.SLOWNESS);

            if (blueTeam.containsKey(captured)) {
                equipScaledArmor(captured, Color.BLUE);
                captured.teleport(blueSpawn);
            } else if (redTeam.containsKey(captured)) {
                equipScaledArmor(captured, Color.RED);
                captured.teleport(redSpawn);
            }

            applyConfiguredItems(captured);
            applyUpgradeKit(captured);

            invinciblePlayers.add(captured);
            Bukkit.getScheduler().runTaskLater(plugin, () -> invinciblePlayers.remove(captured), invincibilityTime * 20L);
        }, respawnInterval * 20L);
    }

    public void handleCrystalDamage(Player attacker, EnderCrystal crystal, boolean projectile) {
        if (!isHappening() || ending || attacker == null || crystal == null) {
            return;
        }

        if (!getPlayers().contains(attacker) || !pvpEnabled) {
            return;
        }

        if (isTower(crystal)) {
            handleTowerDamage(attacker, crystal, projectile);
            return;
        }

        if (isNexus(crystal)) {
            handleNexusDamage(attacker, crystal);
        }
    }

    private void handleTowerDamage(Player attacker, EnderCrystal crystal, boolean projectile) {
        String team = getCrystalTeam(crystal);
        if (team == null) {
            return;
        }

        if ((team.equals("blue") && blueTeam.containsKey(attacker))
                || (team.equals("red") && redTeam.containsKey(attacker))) {
            sendSameTeamMessage(attacker);
            return;
        }

        int damage = projectile ? towerProjectileDamage : towerMeleeDamage;

        if (crystal == blueTower1) {
            blueTower1Health -= damage;
            updateTowerName(blueTower1, "blue", 1, Math.max(blueTower1Health, 0));
            if (blueTower1Health <= 0) {
                destroyTower("blue", 1, attacker);
            }
            return;
        }

        if (crystal == blueTower2) {
            blueTower2Health -= damage;
            updateTowerName(blueTower2, "blue", 2, Math.max(blueTower2Health, 0));
            if (blueTower2Health <= 0) {
                destroyTower("blue", 2, attacker);
            }
            return;
        }

        if (crystal == redTower1) {
            redTower1Health -= damage;
            updateTowerName(redTower1, "red", 1, Math.max(redTower1Health, 0));
            if (redTower1Health <= 0) {
                destroyTower("red", 1, attacker);
            }
            return;
        }

        if (crystal == redTower2) {
            redTower2Health -= damage;
            updateTowerName(redTower2, "red", 2, Math.max(redTower2Health, 0));
            if (redTower2Health <= 0) {
                destroyTower("red", 2, attacker);
            }
        }
    }

    private void handleNexusDamage(Player attacker, EnderCrystal crystal) {
        String team = getCrystalTeam(crystal);
        if (team == null) {
            return;
        }

        if ((team.equals("blue") && blueTeam.containsKey(attacker))
                || (team.equals("red") && redTeam.containsKey(attacker))) {
            sendSameTeamMessage(attacker);
            return;
        }

        if (team.equals("blue") && !isBlueNexusUnlocked()) {
            sendProtectedMessage(attacker, blueName);
            return;
        }

        if (team.equals("red") && !isRedNexusUnlocked()) {
            sendProtectedMessage(attacker, redName);
            return;
        }

        int damage = calculateWeaponDamage(attacker);

        if (crystal == blueNexus) {
            blueNexusHealth -= damage;
            updateNexusName(blueNexus, "blue", Math.max(blueNexusHealth, 0));

            if (blueNexusHealth <= 0) {
                crystal.remove();
                blueNexus = null;
                announceDestroyed(redName, blueName);
                win("red");
            }
            return;
        }

        if (crystal == redNexus) {
            redNexusHealth -= damage;
            updateNexusName(redNexus, "red", Math.max(redNexusHealth, 0));

            if (redNexusHealth <= 0) {
                crystal.remove();
                redNexus = null;
                announceDestroyed(blueName, redName);
                win("blue");
            }
        }
    }

    public void win(String team) {
        if (!teamSelected || ending) {
            return;
        }

        ending = true;

        List<Player> winnersTeam = new ArrayList<>();
        if (team.equalsIgnoreCase("blue")) {
            winnersTeam.addAll(blueTeam.keySet());
        } else {
            winnersTeam.addAll(redTeam.keySet());
        }

        List<String> winnersNames = new ArrayList<>();
        for (Player player : winnersTeam) {
            winnersNames.add(player.getName());
        }

        setWinners(new HashSet<>(winnersTeam));

        for (Player player : winnersTeam) {
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        List<Player> rewardTargets = new ArrayList<>(winnersTeam);

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", String.join(", ", winnersNames))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendTeamWinner(
                team.equalsIgnoreCase("blue") ? blueName : redName,
                config.getString("Evento.Title"),
                buildTopEntries()
        );

        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : rewardTargets) {
                if (!player.isOnline()) {
                    continue;
                }

                for (String command : config.getStringList("Rewards.Commands")) {
                    executeConsoleCommand(player, command.replace("@winner", player.getName()));
                }
            }
        }, 5L);
    }

    public void sendKillMessage(Player killer, Player victim) {
        for (String msg : config.getStringList("Messages.Kill")) {
            sendToEvent(msg
                    .replace("@name", config.getString("Evento.Title"))
                    .replace("@killer", killer.getName())
                    .replace("@victim", victim.getName()));
        }
    }

    private void assignTeams() {
        List<Player> shuffled = new ArrayList<>(getPlayers());
        Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            Player player = shuffled.get(i);
            kills.put(player, 0);

            if (i % 2 == 0) {
                blueTeam.put(player, 0);
            } else {
                redTeam.put(player, 0);
            }
        }

        teamSelected = true;
    }

    private void preparePlayers() {
        List<String> teamMessages = config.getStringList("Messages.Team");

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayerInventory(player);
            applyConfiguredItems(player);
            applyUpgradeKit(player);

            if (blueTeam.containsKey(player)) {
                sendTeamMessages(player, teamMessages, "§9" + blueName);
                equipScaledArmor(player, Color.BLUE);
                player.teleport(blueSpawn);
            } else if (redTeam.containsKey(player)) {
                sendTeamMessages(player, teamMessages, "§c" + redName);
                equipScaledArmor(player, Color.RED);
                player.teleport(redSpawn);
            }

            sendConfiguredTitle(player, "Title.Welcome",
                    "@name", config.getString("Evento.Title"),
                    "@map", config.getString("Evento.Map name", "Howling Abyss"));
        }
    }

    private void spawnStructures() {
        blueNexus = spawnStructureCrystal(blueNexusLocation, "BlueNexus");
        updateNexusName(blueNexus, "blue", blueNexusHealth);

        redNexus = spawnStructureCrystal(redNexusLocation, "RedNexus");
        updateNexusName(redNexus, "red", redNexusHealth);

        if (config.getBoolean("Towers.Enabled", true)) {
            blueTower1 = spawnStructureCrystal(blueTower1Location, "BlueTower1");
            updateTowerName(blueTower1, "blue", 1, blueTower1Health);

            blueTower2 = spawnStructureCrystal(blueTower2Location, "BlueTower2");
            updateTowerName(blueTower2, "blue", 2, blueTower2Health);

            redTower1 = spawnStructureCrystal(redTower1Location, "RedTower1");
            updateTowerName(redTower1, "red", 1, redTower1Health);

            redTower2 = spawnStructureCrystal(redTower2Location, "RedTower2");
            updateTowerName(redTower2, "red", 2, redTower2Health);
        }
    }

    private EnderCrystal spawnStructureCrystal(Location location, String metadataKey) {
        if (location == null || location.getWorld() == null) {
            Bukkit.getLogger().warning("[AbsolutEvents] Nexus: localização inválida para " + metadataKey);
            return null;
        }

        location.getChunk().load();
        removeNearbyStructureCrystals(location);

        return location.getWorld().spawn(location, EnderCrystal.class, spawned -> {
            spawned.setShowingBottom(false);
            spawned.setCustomNameVisible(true);
            spawned.setInvulnerable(false);
            spawned.setMetadata("NexusStructure", new FixedMetadataValue(plugin, true));
            spawned.setMetadata(metadataKey, new FixedMetadataValue(plugin, true));
        });
    }

    private void destroyTower(String team, int towerId, Player attacker) {
        EnderCrystal target = getTower(team, towerId);
        if (target != null) {
            target.remove();
        }
        setTowerReference(team, towerId, null);
        towerTargets.remove(team + "-" + towerId);

        String teamDisplay = team.equals("blue") ? blueName : redName;

        for (String message : config.getStringList("Messages.Tower destroyed")) {
            sendToEvent(message
                    .replace("@name", config.getString("Evento.Title"))
                    .replace("@team", teamDisplay)
                    .replace("@tower", String.valueOf(towerId))
                    .replace("@player", attacker.getName()));
        }

        sendConfiguredTitleToEvent("Title.Tower destroyed",
                "@name", config.getString("Evento.Title"),
                "@team", teamDisplay,
                "@tower", String.valueOf(towerId),
                "@player", attacker.getName());

        if ((team.equals("blue") && isBlueNexusUnlocked())
                || (team.equals("red") && isRedNexusUnlocked())) {
            for (String message : config.getStringList("Messages.Nexus unlocked")) {
                sendToEvent(message
                        .replace("@name", config.getString("Evento.Title"))
                        .replace("@team", teamDisplay));
            }

            sendConfiguredTitleToEvent("Title.Nexus unlocked",
                    "@name", config.getString("Evento.Title"),
                    "@team", teamDisplay);
        }
    }

    private void announceDestroyed(String team1, String team2) {
        for (String message : config.getStringList("Messages.Destroyed")) {
            sendToEvent(message
                    .replace("@name", config.getString("Evento.Title"))
                    .replace("@team1", team1)
                    .replace("@team2", team2));
        }
    }

    private void startTowerTask() {
        towerAttackTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending || !pvpEnabled) {
                return;
            }

            attackWithTower(blueTower1, "blue", 1);
            attackWithTower(blueTower2, "blue", 2);
            attackWithTower(redTower1, "red", 1);
            attackWithTower(redTower2, "red", 2);
        }, 20L, Math.max(10L, towerAttackInterval));
    }

    private void attackWithTower(EnderCrystal tower, String towerTeam, int towerId) {
        if (tower == null || !tower.isValid() || tower.isDead()) {
            towerTargets.remove(towerTeam + "-" + towerId);
            return;
        }

        String key = towerTeam + "-" + towerId;
        Player target = getTrackedTarget(key, tower.getLocation(), towerTeam);

        if (target == null) {
            target = findNearestEnemy(tower.getLocation(), towerTeam);
            if (target != null) {
                towerTargets.put(key, target.getUniqueId());
            } else {
                towerTargets.remove(key);
                return;
            }
        }

        if (!isValidTowerTarget(target, tower.getLocation(), towerTeam)) {
            towerTargets.remove(key);
            return;
        }

        tower.getWorld().playSound(tower.getLocation(), Sound.ENTITY_GUARDIAN_ATTACK, 1.0F, 1.0F);
        drawTowerBeam(tower.getLocation().add(0, 1.0, 0), target.getLocation().add(0, 1.1, 0));

        double finalDamage = towerDamage;
        if ((target.getHealth() - finalDamage) <= 0.0D) {
            target.getWorld().spawnParticle(Particle.FIREWORK, target.getLocation().add(0, 1, 0), 20, 0.25, 0.45, 0.25, 0.02);

            for (String msg : config.getStringList("Messages.Tower kill")) {
                sendToEvent(msg
                        .replace("@name", config.getString("Evento.Title"))
                        .replace("@player", target.getName()));
            }

            eliminate(target, null);
            return;
        }

        target.damage(finalDamage);

        Vector knock = target.getLocation().toVector().subtract(tower.getLocation().toVector()).normalize().multiply(0.25D);
        target.setVelocity(target.getVelocity().add(knock));
    }

    private void drawTowerBeam(Location from, Location to) {
        World w = from.getWorld();
        if (w == null || !w.equals(to.getWorld())) {
            return;
        }

        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        if (length <= 0.01D) {
            return;
        }

        direction.normalize();

        for (double d = 0; d <= length; d += 0.5D) {
            Location point = from.clone().add(direction.clone().multiply(d));
            w.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0, 0, 0, 0);
        }
    }

    private Player getTrackedTarget(String key, Location towerLocation, String towerTeam) {
        UUID uuid = towerTargets.get(key);
        if (uuid == null) {
            return null;
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return null;
        }

        return isValidTowerTarget(player, towerLocation, towerTeam) ? player : null;
    }

    private boolean isValidTowerTarget(Player player, Location towerLocation, String towerTeam) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        if (!getPlayers().contains(player)) {
            return false;
        }

        if (deadPlayers.contains(player) || invinciblePlayers.contains(player)) {
            return false;
        }

        boolean sameTeam = towerTeam.equals("blue") ? blueTeam.containsKey(player) : redTeam.containsKey(player);
        if (sameTeam) {
            return false;
        }

        if (!player.getWorld().equals(towerLocation.getWorld())) {
            return false;
        }

        return player.getLocation().distanceSquared(towerLocation) <= (towerRange * towerRange);
    }

    private Player findNearestEnemy(Location from, String towerTeam) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : getPlayers()) {
            if (!isValidTowerTarget(player, from, towerTeam)) {
                continue;
            }

            double distance = player.getLocation().distanceSquared(from);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    private void schedulePvpEnable() {
        enablePvpTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            pvpEnabled = true;

            List<String> enabledMessages = config.getStringList("Messages.Enabled");
            for (Player player : getPlayers()) {
                for (String message : enabledMessages) {
                    player.sendMessage(ColorUtils.colorize(
                            message.replace("@name", config.getString("Evento.Title"))
                    ));
                }
            }

            for (Player player : getSpectators()) {
                for (String message : enabledMessages) {
                    player.sendMessage(ColorUtils.colorize(
                            message.replace("@name", config.getString("Evento.Title"))
                    ));
                }
            }
        }, enablePvp * 20L);
    }

    private void addKill(Player killer) {
        int newKills = kills.getOrDefault(killer, 0) + 1;
        kills.put(killer, newKills);

        clearPlayerInventory(killer);
        applyConfiguredItems(killer);
        applyUpgradeKit(killer);

        if (blueTeam.containsKey(killer)) {
            equipScaledArmor(killer, Color.BLUE);
        } else {
            equipScaledArmor(killer, Color.RED);
        }

        killer.updateInventory();

        if (config.contains("Messages.Killstreak." + newKills)) {
            for (String msg : config.getStringList("Messages.Killstreak." + newKills)) {
                sendToEvent(msg
                        .replace("@name", config.getString("Evento.Title"))
                        .replace("@player", killer.getName()));
            }
        }

        if (newKills % 3 == 0) {
            for (String message : config.getStringList("Messages.Upgrade")) {
                killer.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@name", config.getString("Evento.Title"))
                                .replace("@kills", String.valueOf(newKills))
                                .replace("@level", String.valueOf(getUpgradeTier(killer)))
                ));
            }
        }
    }

    private void applyUpgradeKit(Player player) {
        int tier = getUpgradeTier(player);

        Material swordMat = switch (tier) {
            case 1 -> Material.STONE_SWORD;
            case 2 -> Material.IRON_SWORD;
            case 3, 4 -> Material.DIAMOND_SWORD;
            default -> Material.WOODEN_SWORD;
        };

        Material axeMat = switch (tier) {
            case 1 -> Material.STONE_AXE;
            case 2 -> Material.IRON_AXE;
            case 3, 4 -> Material.DIAMOND_AXE;
            default -> Material.WOODEN_AXE;
        };

        ItemStack slot0 = player.getInventory().getItem(0);
        if (slot0 == null || slot0.getType() == Material.AIR) {
            player.getInventory().setItem(0, new ItemStack(swordMat));
        }

        ItemStack slot1 = player.getInventory().getItem(1);
        if (slot1 == null || slot1.getType() == Material.AIR) {
            player.getInventory().setItem(1, new ItemStack(axeMat));
        }
    }

    private int getUpgradeTier(Player player) {
        int playerKills = kills.getOrDefault(player, 0);
        if (playerKills >= 12) return 4;
        if (playerKills >= 9) return 3;
        if (playerKills >= 6) return 2;
        if (playerKills >= 3) return 1;
        return 0;
    }

    private int getArmorProtectionLevel(Player player) {
        return getUpgradeTier(player);
    }

    private int calculateWeaponDamage(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null) {
            return Math.max(1, nexusBaseDamage);
        }

        String name = hand.getType().name();

        if (name.contains("NETHERITE")) return 10;
        if (name.contains("DIAMOND")) return 8;
        if (name.contains("IRON")) return 6;
        if (name.contains("STONE")) return 5;
        if (name.contains("WOOD")) return 4;
        if (name.contains("GOLD")) return 4;

        return Math.max(2, nexusBaseDamage);
    }

    private void applyConfiguredItems(Player player) {
        ConfigurationSection itens = config.getConfigurationSection("Itens");
        if (itens == null) {
            return;
        }

        EventKitApplier.apply(player, itens);
    }

    private void sendTeamMessages(Player player, List<String> messages, String teamDisplay) {
        for (String message : messages) {
            player.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@team", teamDisplay)
                            .replace("@time", String.valueOf(enablePvp))
            ));
        }
    }

    private void equipScaledArmor(Player player, Color color) {
        int protLevel = getArmorProtectionLevel(player);

        player.getInventory().setHelmet(createArmorPiece(Material.LEATHER_HELMET, color, protLevel));
        player.getInventory().setChestplate(createArmorPiece(Material.LEATHER_CHESTPLATE, color, protLevel));
        player.getInventory().setLeggings(createArmorPiece(Material.LEATHER_LEGGINGS, color, protLevel));
        player.getInventory().setBoots(createArmorPiece(Material.LEATHER_BOOTS, color, protLevel));
    }

    private ItemStack createArmorPiece(Material material, Color color, int protLevel) {
        ItemStack item = new ItemStack(material, 1);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setColor(color);
            if (protLevel > 0) {
                meta.addEnchant(Enchantment.PROTECTION, protLevel, true);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    private void equipDeadArmor(Player player) {
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
    }

    private void clearPlayerInventory(Player player) {
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

    private void sendLeaveMessages(Player player) {
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

    private void sendToEvent(String message) {
        String parsed = ColorUtils.colorize(message);

        for (Player player : getPlayers()) {
            player.sendMessage(parsed);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(parsed);
        }
    }

    private void sendConfiguredTitle(Player player, String sectionPath, String... replacements) {
        if (!config.getBoolean("Title.Enabled", false)) {
            return;
        }

        String title = config.getString(sectionPath + ".Title");
        String subtitle = config.getString(sectionPath + ".Subtitle");

        if (title == null && subtitle == null) {
            return;
        }

        if (title == null) title = "";
        if (subtitle == null) subtitle = "";

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            title = title.replace(replacements[i], replacements[i + 1]);
            subtitle = subtitle.replace(replacements[i], replacements[i + 1]);
        }

        player.sendTitle(
                ColorUtils.colorize(title),
                ColorUtils.colorize(subtitle),
                config.getInt("Title.FadeIn", 10),
                config.getInt("Title.Stay", 50),
                config.getInt("Title.FadeOut", 10)
        );
    }

    private void sendConfiguredTitleToEvent(String sectionPath, String... replacements) {
        for (Player player : getPlayers()) {
            sendConfiguredTitle(player, sectionPath, replacements);
        }

        for (Player player : getSpectators()) {
            sendConfiguredTitle(player, sectionPath, replacements);
        }
    }

    private void sendProtectedMessage(Player player, String teamName) {
        for (String message : config.getStringList("Messages.Nexus protected")) {
            player.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@team", teamName)
            ));
        }
    }

    private void sendSameTeamMessage(Player player) {
        for (String message : config.getStringList("Messages.Same hit")) {
            player.sendMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void updateNexusName(EnderCrystal crystal, String team, int health) {
        if (crystal == null) {
            return;
        }

        String teamColor = team.equals("blue") ? "§9" : "§c";
        String teamName = team.equals("blue") ? blueName : redName;

        crystal.setCustomName(ColorUtils.colorize(
                nexusName
                        .replace("@team_color", teamColor)
                        .replace("@team_uppercase", teamName.toUpperCase())
                        .replace("@team", teamName)
                        .replace("@health", String.valueOf(health))
        ));
    }

    private void updateTowerName(EnderCrystal crystal, String team, int id, int health) {
        if (crystal == null) {
            return;
        }

        String teamColor = team.equals("blue") ? "§9" : "§c";
        String teamName = team.equals("blue") ? blueName : redName;

        crystal.setCustomName(ColorUtils.colorize(
                towerName
                        .replace("@team_color", teamColor)
                        .replace("@team", teamName)
                        .replace("@id", String.valueOf(id))
                        .replace("@health", String.valueOf(health))
        ));
    }

    private void resetHealth() {
        blueNexusHealth = nexusHealthMax;
        redNexusHealth = nexusHealthMax;
        blueTower1Health = towerHealthMax;
        blueTower2Health = towerHealthMax;
        redTower1Health = towerHealthMax;
        redTower2Health = towerHealthMax;
    }

    private void removeTrackedStructures() {
        if (blueNexus != null) {
            blueNexus.remove();
            blueNexus = null;
        }
        if (redNexus != null) {
            redNexus.remove();
            redNexus = null;
        }
        if (blueTower1 != null) {
            blueTower1.remove();
            blueTower1 = null;
        }
        if (blueTower2 != null) {
            blueTower2.remove();
            blueTower2 = null;
        }
        if (redTower1 != null) {
            redTower1.remove();
            redTower1 = null;
        }
        if (redTower2 != null) {
            redTower2.remove();
            redTower2 = null;
        }
    }

    private void removeAllExistingStructures() {
        if (world == null) {
            return;
        }

        for (Entity entity : new ArrayList<>(world.getEntities())) {
            if (!(entity instanceof EnderCrystal crystal)) {
                continue;
            }

            if (crystal.hasMetadata("NexusStructure")
                    || crystal.hasMetadata("BlueNexus")
                    || crystal.hasMetadata("RedNexus")
                    || crystal.hasMetadata("BlueTower1")
                    || crystal.hasMetadata("BlueTower2")
                    || crystal.hasMetadata("RedTower1")
                    || crystal.hasMetadata("RedTower2")) {
                crystal.remove();
            }
        }

        blueNexus = null;
        redNexus = null;
        blueTower1 = null;
        blueTower2 = null;
        redTower1 = null;
        redTower2 = null;
    }

    private void removeNearbyStructureCrystals(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        for (Entity entity : new ArrayList<>(location.getWorld().getNearbyEntities(location, 3, 4, 3))) {
            if (!(entity instanceof EnderCrystal crystal)) {
                continue;
            }

            if (crystal.hasMetadata("NexusStructure")
                    || crystal.hasMetadata("BlueNexus")
                    || crystal.hasMetadata("RedNexus")
                    || crystal.hasMetadata("BlueTower1")
                    || crystal.hasMetadata("BlueTower2")
                    || crystal.hasMetadata("RedTower1")
                    || crystal.hasMetadata("RedTower2")) {
                crystal.remove();
            }
        }
    }

    private void removeAllStructureLocations() {
        removeNearbyStructureCrystals(blueNexusLocation);
        removeNearbyStructureCrystals(redNexusLocation);
        removeNearbyStructureCrystals(blueTower1Location);
        removeNearbyStructureCrystals(blueTower2Location);
        removeNearbyStructureCrystals(redTower1Location);
        removeNearbyStructureCrystals(redTower2Location);
    }

    private void clearTasks() {
        if (enablePvpTask != null) {
            enablePvpTask.cancel();
            enablePvpTask = null;
        }

        if (towerAttackTask != null) {
            towerAttackTask.cancel();
            towerAttackTask = null;
        }
    }

    private void checkWinConditionByTeamEmpty() {
        if (!teamSelected || ending) {
            return;
        }

        if (blueTeam.isEmpty()) {
            win("red");
            return;
        }

        if (redTeam.isEmpty()) {
            win("blue");
        }
    }

    private boolean isTower(EnderCrystal crystal) {
        return crystal == blueTower1 || crystal == blueTower2 || crystal == redTower1 || crystal == redTower2;
    }

    private boolean isNexus(EnderCrystal crystal) {
        return crystal == blueNexus || crystal == redNexus;
    }

    private String getCrystalTeam(EnderCrystal crystal) {
        if (crystal == blueNexus || crystal == blueTower1 || crystal == blueTower2) {
            return "blue";
        }

        if (crystal == redNexus || crystal == redTower1 || crystal == redTower2) {
            return "red";
        }

        return null;
    }

    private EnderCrystal getTower(String team, int id) {
        if (team.equals("blue")) {
            return id == 1 ? blueTower1 : blueTower2;
        }
        return id == 1 ? redTower1 : redTower2;
    }

    private void setTowerReference(String team, int id, EnderCrystal crystal) {
        if (team.equals("blue")) {
            if (id == 1) {
                blueTower1 = crystal;
            } else {
                blueTower2 = crystal;
            }
        } else {
            if (id == 1) {
                redTower1 = crystal;
            } else {
                redTower2 = crystal;
            }
        }
    }

    private boolean isBlueNexusUnlocked() {
        return (blueTower1 == null || !blueTower1.isValid()) && (blueTower2 == null || !blueTower2.isValid());
    }

    private boolean isRedNexusUnlocked() {
        return (redTower1 == null || !redTower1.isValid()) && (redTower2 == null || !redTower2.isValid());
    }

    private Location readLocation(String path, boolean withYawPitch) {
        String worldName = config.getString(path + ".world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Nexus: " + path + ".world não encontrado na config.");
        }

        World w = plugin.getServer().getWorld(worldName);
        if (w == null) {
            throw new IllegalStateException("Nexus: mundo '" + worldName + "' não está carregado.");
        }

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");

        if (withYawPitch) {
            float yaw = (float) config.getDouble(path + ".Yaw");
            float pitch = (float) config.getDouble(path + ".Pitch");
            return new Location(w, x, y, z, yaw, pitch);
        }

        return new Location(w, x, y, z);
    }

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        List<DiscordWebhookManager.TopEntry> list = new ArrayList<>();

        kills.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(3)
                .forEach(entry -> list.add(
                        new DiscordWebhookManager.TopEntry(
                                entry.getKey().getName(),
                                String.valueOf(entry.getValue())
                        )
                ));

        return list;
    }

    public HashMap<Player, Integer> getBlueTeam() {
        return blueTeam;
    }

    public HashMap<Player, Integer> getRedTeam() {
        return redTeam;
    }

    public List<Player> getDeadPlayers() {
        return deadPlayers;
    }

    public List<Player> getInvinciblePlayers() {
        return invinciblePlayers;
    }

    public EnderCrystal getBlueNexus() {
        return blueNexus;
    }

    public EnderCrystal getRedNexus() {
        return redNexus;
    }

    public boolean isPvPEnabled() {
        return pvpEnabled;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.BlockPartyListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.cryptomorin.xseries.XSound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class BlockParty extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final BlockPartyListener listener = new BlockPartyListener();

    private BukkitTask monitorTask;
    private BukkitTask countdownTask;

    private boolean levelHappening = false;
    private final boolean lastPlayerWin;
    private final int maxLevel;

    private final long startTime;
    private final long delay;
    private final long timeReduction;
    private final long minTime;
    private long remainingTime;
    private int levelCounter;

    private final String blockCustomName;

    private final Cuboid cuboid;
    private final HashMap<Material, List<Block>> typeBlocks = new HashMap<>();
    private final List<Material> materials = new ArrayList<>();
    private Material lastMaterial;
    private final HashMap<Player, Float> playerInitialXP = new HashMap<>();

    private final List<String> nextLevelStarting;
    private final Random random = new Random();

    private final Set<UUID> rewardProtectedWinners = new HashSet<>();

    public BlockParty(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.startTime = config.getInt("Evento.Start") * 20L;
        this.delay = config.getInt("Evento.Delay") * 20L;
        this.timeReduction = config.getInt("Evento.Time reduction") * 10L;
        this.remainingTime = config.getInt("Evento.Time") * 20L;
        this.minTime = config.getInt("Evento.Time minimo") * 20L;

        this.lastPlayerWin = config.getBoolean("Evento.Last player wins");
        this.maxLevel = config.getInt("Evento.Max level");
        this.blockCustomName = ColorUtils.colorize(config.getString("Evento.Block custom name", ""));
        this.nextLevelStarting = config.getStringList("Messages.Next level");

        World world = plugin.getServer().getWorld(config.getString("Locations.Pos1.world"));
        if (world == null) {
            throw new IllegalStateException("Mundo do BlockParty não encontrado.");
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

        this.cuboid = new Cuboid(pos1, pos2);
        this.levelCounter = 1;
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        rewardProtectedWinners.clear();
        typeBlocks.clear();
        materials.clear();

        for (Block block : cuboid.getBlocks()) {
            typeBlocks.computeIfAbsent(block.getType(), ignored -> new ArrayList<>()).add(block);

            if (!materials.contains(block.getType())) {
                materials.add(block.getType());
            }
        }

        if (materials.isEmpty()) {
            stop();
            return;
        }

        for (Player player : getPlayers()) {
            playerInitialXP.put(player, player.getExp());
            player.setExp(1.0F);
        }

        lastMaterial = materials.get(random.nextInt(materials.size()));

        for (String message : config.getStringList("Messages.Inicio")) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@time", String.valueOf(startTime / 20L))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        levelCounter = 1;

        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                cancelTask(monitorTask);
                return;
            }

            if (!levelHappening) {
                if (lastPlayerWin && getPlayers().size() == 1) {
                    win();
                    cancelTask(monitorTask);
                    return;
                }

                if (levelCounter > maxLevel) {
                    win();
                    cancelTask(monitorTask);
                    return;
                }

                startLevel();
            }
        }, startTime, 20L);
    }

    private void startLevel() {
        levelHappening = true;

        ItemStack randomBlock = getRandomBlock();
        if (randomBlock == null) {
            stop();
            return;
        }

        for (String message : nextLevelStarting) {
            sendToEvent(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@nivel", String.valueOf(levelCounter))
                            .replace("@time", String.valueOf(remainingTime / 20L))
            ));
        }

        setHotbar(randomBlock);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            AtomicInteger counter = new AtomicInteger();
            float division = 1F / Math.max(1F, remainingTime);

            countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!isHappening()) {
                    cancelTask(countdownTask);
                    return;
                }

                if (counter.get() < remainingTime) {
                    for (Player player : getPlayers()) {
                        float newExp = player.getExp() - division;
                        player.setExp(Math.max(0F, newExp));

                        if (counter.get() % 20 == 0) {
                            XSound.BLOCK_NOTE_BLOCK_PLING.play(player, 1F, 1F);
                        }
                    }

                    counter.incrementAndGet();
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Block block : cuboid.getBlocks()) {
                        if (block.getType() == lastMaterial) {
                            continue;
                        }
                        block.setType(Material.AIR);
                    }

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        restoreFloor();

                        if (remainingTime > minTime) {
                            remainingTime -= timeReduction;
                        }

                        for (Player player : getPlayers()) {
                            player.setExp(1.0F);
                        }

                        if (getPlayers().size() != 1) {
                            setHotbar(null);
                        }

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            levelHappening = false;
                            levelCounter++;
                        }, delay);

                    }, 5 * 20L);
                });

                cancelTask(countdownTask);
            }, 0L, 1L);
        }, 10L);
    }

    public void win() {
        List<Player> winnersPlayers = new ArrayList<>(getPlayers());
        List<String> winnersNames = new ArrayList<>();

        for (Player player : winnersPlayers) {
            winnersNames.add(player.getName());
            rewardProtectedWinners.add(player.getUniqueId());

            // ✅ TOURNAMENT
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        this.setWinners();

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", String.join(", ", winnersNames))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        if (!winnersPlayers.isEmpty()) {
            if (winnersPlayers.size() == 1) {
                DiscordWebhookManager.sendPlayerWinner(
                        winnersPlayers.get(0).getName(),
                        config.getString("Evento.Title")
                );
            } else {
                DiscordWebhookManager.sendTeamWinner(
                        String.join(", ", winnersNames),
                        config.getString("Evento.Title"),
                        List.of()
                );
            }
        }

        endEventPreservingWinners();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : winnersPlayers) {
                if (!player.isOnline()) {
                    continue;
                }

                for (String command : config.getStringList("Rewards.Commands")) {
                    executeConsoleCommand(player, command.replace("@winner", player.getName()));
                }

                player.updateInventory();
            }
        }, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, rewardProtectedWinners::clear, 60L);
    }

    private void endEventPreservingWinners() {
        cancelTask(monitorTask);
        cancelTask(countdownTask);

        restoreFloor();

        List<Player> currentPlayers = new ArrayList<>(getPlayers());
        List<Player> currentSpectators = new ArrayList<>(getSpectators());

        for (Player player : currentPlayers) {
            resetPlayerExp(player);

            if (!rewardProtectedWinners.contains(player.getUniqueId())) {
                player.getInventory().clear();
            }
        }

        for (Player player : currentSpectators) {
            resetPlayerExp(player);
        }

        levelHappening = false;
        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    @Override
    public void stop() {
        cancelTask(monitorTask);
        cancelTask(countdownTask);

        restoreFloor();

        for (Player player : new ArrayList<>(getPlayers())) {
            if (!rewardProtectedWinners.contains(player.getUniqueId())) {
                player.getInventory().clear();
            }
            resetPlayerExp(player);
        }

        for (Player player : new ArrayList<>(getSpectators())) {
            resetPlayerExp(player);
        }

        levelHappening = false;
        HandlerList.unregisterAll(listener);
        removePlayers();
        rewardProtectedWinners.clear();
    }

    @Override
    public void join(Player player) {
        super.join(player);
        playerInitialXP.put(player, player.getExp());
    }

    @Override
    public void leave(Player player) {
        super.leave(player);
        resetPlayerExp(player);

        if (!rewardProtectedWinners.contains(player.getUniqueId())) {
            player.getInventory().clear();
        }

        if (isHappening() && lastPlayerWin && getPlayers().size() == 1) {
            win();
        }
    }

    public void eliminate(@NotNull Player player) {
        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        resetPlayerExp(player);

        if (!rewardProtectedWinners.contains(player.getUniqueId())) {
            player.getInventory().clear();
        }

        remove(player);
        notifyLeave(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                getConfig().getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        if (isHappening() && getPlayers().size() == 1 && lastPlayerWin) {
            win();
        }
    }

    private ItemStack getRandomBlock() {
        if (materials.isEmpty()) {
            return null;
        }

        Material randomMaterial = materials.get(random.nextInt(materials.size()));

        if (materials.size() > 1 && randomMaterial == lastMaterial) {
            return getRandomBlock();
        }

        lastMaterial = randomMaterial;

        ItemStack item = new ItemStack(randomMaterial);

        if (!blockCustomName.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(blockCustomName);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    private void setHotbar(ItemStack itemStack) {
        for (Player player : getPlayers()) {
            for (int i = 0; i <= 8; i++) {
                player.getInventory().setItem(i, itemStack == null ? null : itemStack.clone());
            }
            player.updateInventory();
        }
    }

    private void restoreFloor() {
        for (Material material : materials) {
            List<Block> blocks = typeBlocks.get(material);
            if (blocks == null) {
                continue;
            }

            for (Block block : blocks) {
                block.setType(material);
            }
        }
    }

    private void resetPlayerExp(@NotNull Player player) {
        Float exp = playerInitialXP.remove(player);
        if (exp != null) {
            player.setExp(exp);
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

    public Cuboid getCuboid() {
        return cuboid;
    }

    public double getArenaMinY() {
        return Math.min(
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos2.y")
        );
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.listeners.eventos.FrogListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.List;

public final class Frog extends Evento {

    private final YamlConfiguration config;
    private final FrogListener listener = new FrogListener();

    private final Map<Block, Material> originalBlocks = new HashMap<>();
    private final Set<Material> remainingMaterials = new HashSet<>();
    private final List<Block> platformBlocks = new ArrayList<>();

    private final Cuboid cuboid;
    private Block woolBlock;

    private final int startDelay;
    private final int time;
    private final int snowTime;

    private boolean levelHappening;
    private boolean finalPhaseStarted;

    private final Random random = new Random();

    private BukkitTask startTask;
    private BukkitTask mainTask;
    private BukkitTask roundRemoveTask;
    private BukkitTask roundResetTask;
    private BukkitTask actionbarTask;

    public Frog(YamlConfiguration config) {
        super(config);

        this.config = config;
        this.startDelay = Math.max(1, config.getInt("Evento.Start", 5));
        this.time = Math.max(1, config.getInt("Evento.Time", 3));
        this.snowTime = Math.max(1, config.getInt("Evento.Snow", 2));

        this.levelHappening = false;
        this.finalPhaseStarted = false;

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Frog: Locations.Pos1.world não encontrado na config.");
        }

        World world = AbsolutEventsPlugin.getInstance().getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Frog: mundo '" + worldName + "' não está carregado.");
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
    }

    @Override
    public void start() {
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(
                listener,
                AbsolutEventsPlugin.getInstance()
        );

        listener.setEvento();

        cancelTask(startTask);
        cancelTask(mainTask);
        cancelTask(roundRemoveTask);
        cancelTask(roundResetTask);
        cancelTask(actionbarTask);

        originalBlocks.clear();
        remainingMaterials.clear();
        platformBlocks.clear();

        woolBlock = null;
        levelHappening = false;
        finalPhaseStarted = false;

        Material redWool = XMaterial.RED_WOOL.parseMaterial();

        for (Block block : cuboid.getBlocks()) {
            Material type = block.getType();

            if (type == Material.AIR || type == Material.WATER) {
                continue;
            }

            if (type == Material.SNOW_BLOCK || type == redWool) {
                block.setType(Material.AIR);
                continue;
            }

            originalBlocks.put(block, type);
            remainingMaterials.add(type);
            platformBlocks.add(block);
        }

        startTask = AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    mainTask = AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskTimer(
                            AbsolutEventsPlugin.getInstance(),
                            () -> {
                                if (!isHappening()) {
                                    cancelTask(mainTask);
                                    return;
                                }

                                if (levelHappening || finalPhaseStarted) {
                                    return;
                                }

                                frog();
                            },
                            20L,
                            20L
                    );
                },
                startDelay * 20L
        );
    }

    @Override
    public void winner(Player player) {
        for (String message : config.getStringList("Messages.Winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(
                    ColorUtils.colorize(
                            message.replace("&", "§")
                                    .replace("@winner", player.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        setWinner(player);
        stop();

        AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    for (String command : config.getStringList("Rewards.Commands")) {
                        executeConsoleCommand(player, command.replace("@winner", player.getName()));
                    }
                },
                2L
        );
    }

    @Override
    public void stop() {
        cancelTask(startTask);
        cancelTask(mainTask);
        cancelTask(roundRemoveTask);
        cancelTask(roundResetTask);
        cancelTask(actionbarTask);

        restoreArena();

        originalBlocks.clear();
        remainingMaterials.clear();
        platformBlocks.clear();

        woolBlock = null;
        levelHappening = false;
        finalPhaseStarted = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    private void frog() {
        if (!isHappening()) {
            return;
        }

        if (remainingMaterials.size() <= 1) {
            startFinalPhase();
            return;
        }

        levelHappening = true;

        List<Material> materialList = new ArrayList<>(remainingMaterials);
        Material materialRemove = materialList.get(random.nextInt(materialList.size()));

        List<Block> affectedBlocks = new ArrayList<>();
        for (Block block : platformBlocks) {
            if (block.getType() == materialRemove) {
                affectedBlocks.add(block);
            }
        }

        if (affectedBlocks.isEmpty()) {
            remainingMaterials.remove(materialRemove);
            levelHappening = false;
            return;
        }

        sendRemoveTitle(materialName(materialRemove));
        startWaitingActionbar(time + snowTime);

        for (Block block : affectedBlocks) {
            block.setType(Material.SNOW_BLOCK);
        }

        roundRemoveTask = AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    for (Block block : affectedBlocks) {
                        block.setType(Material.AIR);
                    }

                    remainingMaterials.remove(materialRemove);
                },
                snowTime * 20L
        );

        roundResetTask = AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    levelHappening = false;
                    cancelTask(actionbarTask);
                },
                (snowTime + time) * 20L
        );
    }

    private void startFinalPhase() {
        if (finalPhaseStarted) {
            return;
        }

        finalPhaseStarted = true;
        levelHappening = true;

        Material redWool = XMaterial.RED_WOOL.parseMaterial();
        if (redWool == null) {
            redWool = Material.RED_WOOL;
        }

        List<Block> candidates = new ArrayList<>();

        for (Block block : platformBlocks) {
            block.setType(Material.SNOW_BLOCK);
            candidates.add(block);
        }

        if (candidates.isEmpty()) {
            return;
        }

        woolBlock = candidates.get(random.nextInt(candidates.size()));
        woolBlock.setType(redWool);

        listener.setWool();

        sendFinalTitle();
        startFinalActionbar();

        for (Player player : getPlayers()) {
            for (String message : config.getStringList("Messages.Wool")) {
                player.sendMessage(
                        ColorUtils.colorize(
                                message.replace("&", "§")
                                        .replace("@name", config.getString("Evento.Title"))
                        )
                );
            }
        }

        for (Player player : getSpectators()) {
            for (String message : config.getStringList("Messages.Wool")) {
                player.sendMessage(
                        ColorUtils.colorize(
                                message.replace("&", "§")
                                        .replace("@name", config.getString("Evento.Title"))
                        )
                );
            }
        }
    }

    private void restoreArena() {
        for (Block block : platformBlocks) {
            Material original = originalBlocks.get(block);
            if (original != null) {
                block.setType(original);
            } else {
                block.setType(Material.AIR);
            }
        }

        if (woolBlock != null) {
            Material original = originalBlocks.get(woolBlock);
            woolBlock.setType(original != null ? original : Material.AIR);
        }
    }

    private void startWaitingActionbar(int seconds) {
        if (!config.getBoolean("Actionbar.Enabled", false)) {
            return;
        }

        cancelTask(actionbarTask);

        final int[] remaining = {seconds};

        actionbarTask = AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || finalPhaseStarted) {
                        cancelTask(actionbarTask);
                        return;
                    }

                    String message = config.getString("Actionbar.Waiting.Message", "&e[@name] &fPróxima remoção em &e@time");
                    message = message.replace("@time", String.valueOf(Math.max(remaining[0], 0)))
                            .replace("@name", config.getString("Evento.Title", "Frog"));

                    String parsed = ColorUtils.colorize(message.replace("&", "§"));

                    for (Player player : getPlayers()) {
                        player.sendActionBar(parsed);
                    }

                    remaining[0]--;
                    if (remaining[0] < 0) {
                        cancelTask(actionbarTask);
                    }
                },
                0L,
                20L
        );
    }

    private void startFinalActionbar() {
        if (!config.getBoolean("Actionbar.Enabled", false)) {
            return;
        }

        cancelTask(actionbarTask);

        actionbarTask = AbsolutEventsPlugin.getInstance().getServer().getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || !finalPhaseStarted || woolBlock == null) {
                        cancelTask(actionbarTask);
                        return;
                    }

                    String message = config.getString("Actionbar.Final.Message", "&c[@name] &fPise na lã vermelha para vencer!");
                    message = message.replace("@name", config.getString("Evento.Title", "Frog"));

                    String parsed = ColorUtils.colorize(message.replace("&", "§"));

                    for (Player player : getPlayers()) {
                        player.sendActionBar(parsed);
                    }
                },
                0L,
                20L
        );
    }

    private void sendRemoveTitle(String colorName) {
        if (!config.getBoolean("Title.Enabled", false)) {
            return;
        }

        int fadeIn = config.getInt("Title.FadeIn", 5);
        int stay = config.getInt("Title.Stay", 20);
        int fadeOut = config.getInt("Title.FadeOut", 5);

        String title = config.getString("Title.Remove.Title", "&cATENÇÃO!");
        String subtitle = config.getString("Title.Remove.Subtitle", "&fA cor &c@color &fvai cair!");

        title = ColorUtils.colorize(title.replace("&", "§"));
        subtitle = ColorUtils.colorize(
                subtitle.replace("&", "§")
                        .replace("@color", colorName)
        );

        for (Player player : getPlayers()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private void sendFinalTitle() {
        if (!config.getBoolean("Title.Enabled", false)) {
            return;
        }

        int fadeIn = config.getInt("Title.FadeIn", 5);
        int stay = config.getInt("Title.Stay", 20);
        int fadeOut = config.getInt("Title.FadeOut", 5);

        String title = config.getString("Title.Final.Title", "&cCORRA!");
        String subtitle = config.getString("Title.Final.Subtitle", "&fPise na lã vermelha para vencer!");

        title = ColorUtils.colorize(title.replace("&", "§"));
        subtitle = ColorUtils.colorize(subtitle.replace("&", "§"));

        for (Player player : getPlayers()) {
            player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    private String materialName(Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace("_", " ");
        String[] parts = name.split(" ");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }

        return builder.toString().trim();
    }

    public Block getWoolBlock() {
        return woolBlock;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
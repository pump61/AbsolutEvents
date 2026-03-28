package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.listeners.eventos.RainbowRunListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import com.cryptomorin.xseries.XMaterial;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RainbowRun extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    public static final List<XMaterial> ACCEPTED_BLOCKS = new ArrayList<>();
    public static final Material GROUND = XMaterial.WHITE_CONCRETE.parseMaterial();

    private final YamlConfiguration config;
    private final RainbowRunListener listener = new RainbowRunListener();
    private final Cuboid cuboid;

    private BukkitTask startTask;
    private BukkitTask counterTask;
    private BukkitTask xpLevelTask;
    private BukkitTask finishTask;
    private BukkitTask actionbarTask;

    private boolean started = false;
    private boolean ending = false;

    private final int startTime;
    private final int maxTime;
    private int remainingSeconds;

    private final List<RainbowPlayer> rainbowPlayers = new ArrayList<>();
    private final Map<Player, Float> playerInitialXP = new HashMap<>();
    private final Map<Player, Integer> playerExpLevel = new HashMap<>();
    private final Map<Player, RainbowPlayer> rainbowPlayerMap = new HashMap<>();

    static {
        ACCEPTED_BLOCKS.addAll(Arrays.asList(
                XMaterial.WHITE_WOOL,
                XMaterial.RED_WOOL,
                XMaterial.BLACK_WOOL,
                XMaterial.BLUE_WOOL,
                XMaterial.BROWN_WOOL,
                XMaterial.CYAN_WOOL,
                XMaterial.GRAY_WOOL,
                XMaterial.GREEN_WOOL,
                XMaterial.LIGHT_BLUE_WOOL,
                XMaterial.LIGHT_GRAY_WOOL,
                XMaterial.LIME_WOOL,
                XMaterial.MAGENTA_WOOL,
                XMaterial.ORANGE_WOOL,
                XMaterial.PINK_WOOL,
                XMaterial.PURPLE_WOOL,
                XMaterial.YELLOW_WOOL,
                XMaterial.LIME_TERRACOTTA,
                XMaterial.YELLOW_TERRACOTTA,
                XMaterial.LIME_CONCRETE,
                XMaterial.YELLOW_CONCRETE,
                XMaterial.RED_CONCRETE,
                XMaterial.MAGENTA_CONCRETE,
                XMaterial.RED_NETHER_BRICKS,
                XMaterial.NETHER_BRICKS,
                XMaterial.SANDSTONE,
                XMaterial.RED_SANDSTONE,
                XMaterial.LAPIS_BLOCK,
                XMaterial.REDSTONE_BLOCK,
                XMaterial.GOLD_BLOCK
        ));
    }

    public RainbowRun(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.startTime = config.getInt("Evento.Start");
        this.maxTime = config.getInt("Evento.Max time");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("RainbowRun: Locations.Pos1.world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("RainbowRun: mundo '" + worldName + "' não está carregado.");
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
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        cancelAllTasks();
        restoreGround();

        started = false;
        ending = false;
        remainingSeconds = maxTime * 60;
        rainbowPlayers.clear();
        rainbowPlayerMap.clear();
        listener.getConqueredBlocks().clear();

        for (String message : config.getStringList("Messages.Start evento")) {
            sendToEvent(
                    ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@time", String.valueOf(startTime))
                    )
            );
        }

        List<Player> shuffled = new ArrayList<>(getPlayers());
        java.util.Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size() && i < ACCEPTED_BLOCKS.size(); i++) {
            Player player = shuffled.get(i);
            RainbowPlayer rainbowPlayer = new RainbowPlayer(player, ACCEPTED_BLOCKS.get(i));
            rainbowPlayers.add(rainbowPlayer);
            rainbowPlayerMap.put(player, rainbowPlayer);
            setHotbar(player, rainbowPlayer.getMaterial().parseItem());
        }

        for (Player player : getPlayers()) {
            player.setExp(1F);
            player.setLevel(maxTime * 60);
        }

        startTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            started = true;
            startCounters();
            startActionbar();
        }, startTime * 20L);
    }

    private void startCounters() {
        float division = 1F / (maxTime * 60F * 20F);

        counterTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending) {
                cancelTask(counterTask);
                return;
            }

            for (Player player : getPlayers()) {
                player.setExp(Math.max(0F, player.getExp() - division));
            }
        }, 0L, 1L);

        xpLevelTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending) {
                cancelTask(xpLevelTask);
                return;
            }

            if (remainingSeconds > 0) {
                remainingSeconds--;
            }

            for (Player player : getPlayers()) {
                if (player.getLevel() > 0) {
                    player.setLevel(player.getLevel() - 1);
                }
            }
        }, 20L, 20L);

        finishTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            finishByScore();
        }, maxTime * 60L * 20L);
    }

    private void startActionbar() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending || !started) {
                cancelTask(actionbarTask);
                return;
            }

            String format = config.getString(
                    "Messages.Actionbar",
                    "&fBlocos: &a@blocks &8| &fPosição: &b@position &8| &fTempo Restante: &e@time"
            );

            String timeFormatted = formatTime(remainingSeconds);
            List<RainbowPlayer> ranking = getRanking();

            for (Player player : getPlayers()) {
                RainbowPlayer rainbowPlayer = getRainbowPlayer(player);
                int blocks = rainbowPlayer != null ? rainbowPlayer.getPunctuation() : 0;
                int position = getPlayerPosition(player, ranking);

                String message = format
                        .replace("@blocks", String.valueOf(blocks))
                        .replace("@time", timeFormatted)
                        .replace("@position", String.valueOf(position));

                String parsed = ColorUtils.colorize(message);
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(parsed));
            }
        }, 0L, 20L);
    }

    private int getPlayerPosition(Player player, List<RainbowPlayer> ranking) {
        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getPlayer().equals(player)) {
                return i + 1;
            }
        }
        return ranking.size();
    }

    private void finishByScore() {
        List<RainbowPlayer> ranking = getRanking();

        if (ranking.isEmpty()) {
            noWinner();
            return;
        }

        RainbowPlayer first = ranking.get(0);
        if (first.getPunctuation() <= 0) {
            noWinner();
            return;
        }

        winner(first.getPlayer());
    }

    @Override
    public void stop() {
        cancelAllTasks();

        for (Player player : new ArrayList<>(getPlayers())) {
            resetPlayerExp(player);
        }

        started = false;
        ending = false;
        remainingSeconds = 0;
        removePlayers();
        rainbowPlayers.clear();
        rainbowPlayerMap.clear();
        listener.getConqueredBlocks().clear();

        HandlerList.unregisterAll(listener);
        restoreGround();
    }

    @Override
    public void leave(Player player) {
        super.leave(player);

        resetPlayerExp(player);

        RainbowPlayer found = rainbowPlayerMap.remove(player);
        if (found != null) {
            found.restoreConqueredBlocks();
            rainbowPlayers.remove(found);
        }

        playerInitialXP.remove(player);
        playerExpLevel.remove(player);
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        int points = 0;
        RainbowPlayer rp = rainbowPlayerMap.get(player);
        if (rp != null) {
            points = rp.getPunctuation();
        }

        List<RainbowPlayer> ranking = getRanking();
        String top1 = ranking.size() >= 1 ? formatTop(ranking.get(0), 1) : "1. Ninguém - 0";
        String top2 = ranking.size() >= 2 ? formatTop(ranking.get(1), 2) : "2. Ninguém - 0";
        String top3 = ranking.size() >= 3 ? formatTop(ranking.get(2), 3) : "3. Ninguém - 0";

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(
                    ColorUtils.colorize(
                            message
                                    .replace("@winner", player.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@blocks", String.valueOf(points))
                                    .replace("@top1", top1)
                                    .replace("@top2", top2)
                                    .replace("@top3", top3)
                    )
            );
        }

        setWinner(player);
        stop();

        Player rewardTarget = player;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!rewardTarget.isOnline()) {
                return;
            }

            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(rewardTarget, command.replace("@winner", rewardTarget.getName()));
            }
        }, 2L);
    }

    public void noWinner() {
        if (ending) {
            return;
        }

        ending = true;

        List<RainbowPlayer> ranking = getRanking();
        String top1 = ranking.size() >= 1 ? formatTop(ranking.get(0), 1) : "1. Ninguém - 0";
        String top2 = ranking.size() >= 2 ? formatTop(ranking.get(1), 2) : "2. Ninguém - 0";
        String top3 = ranking.size() >= 3 ? formatTop(ranking.get(2), 3) : "3. Ninguém - 0";

        for (String message : config.getStringList("Messages.No winner")) {
            plugin.getServer().broadcastMessage(
                    ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@top1", top1)
                                    .replace("@top2", top2)
                                    .replace("@top3", top3)
                    )
            );
        }

        stop();
    }

    @Override
    public void join(Player player) {
        super.join(player);
        playerInitialXP.put(player, player.getExp());
        playerExpLevel.put(player, player.getLevel());
    }

    private List<RainbowPlayer> getRanking() {
        return rainbowPlayers.stream()
                .sorted(Comparator.comparingInt(RainbowPlayer::getPunctuation).reversed())
                .collect(Collectors.toList());
    }

    private String formatTop(RainbowPlayer rainbowPlayer, int position) {
        return position + ". " + rainbowPlayer.getPlayer().getName() + " - " + rainbowPlayer.getPunctuation();
    }

    private String formatTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void restoreGround() {
        for (Block block : cuboid.getBlocks()) {
            block.setType(GROUND);
        }
    }

    private void resetPlayerExp(Player player) {
        if (playerInitialXP.containsKey(player)) {
            player.setExp(playerInitialXP.get(player));
            player.setLevel(playerExpLevel.getOrDefault(player, 0));
            playerInitialXP.remove(player);
            playerExpLevel.remove(player);
        }
    }

    public RainbowPlayer getRainbowPlayer(Player player) {
        return rainbowPlayerMap.get(player);
    }

    private void setHotbar(Player player, ItemStack item) {
        if (item == null) {
            return;
        }

        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, item.clone());
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

    private void cancelAllTasks() {
        cancelTask(startTask);
        cancelTask(counterTask);
        cancelTask(xpLevelTask);
        cancelTask(finishTask);
        cancelTask(actionbarTask);

        startTask = null;
        counterTask = null;
        xpLevelTask = null;
        finishTask = null;
        actionbarTask = null;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public boolean isStarted() {
        return started;
    }

    public Cuboid getCuboid() {
        return cuboid;
    }

    public static class RainbowPlayer {
        private final Player player;
        private final List<Block> conqueredBlocks;
        private final XMaterial material;
        private int punctuation;

        public RainbowPlayer(Player player, XMaterial material) {
            this.player = player;
            this.material = material;
            this.conqueredBlocks = new ArrayList<>();
            this.punctuation = 0;
        }

        public void incrementPoints() {
            this.punctuation++;
        }

        public void decrementPoints() {
            if (this.punctuation > 0) {
                this.punctuation--;
            }
        }

        public void restoreConqueredBlocks() {
            this.conqueredBlocks.forEach(block -> block.setType(GROUND));
            this.conqueredBlocks.clear();
            this.punctuation = 0;
        }

        public Player getPlayer() {
            return player;
        }

        public List<Block> getConqueredBlocks() {
            return conqueredBlocks;
        }

        public XMaterial getMaterial() {
            return material;
        }

        public int getPunctuation() {
            return punctuation;
        }
    }
}
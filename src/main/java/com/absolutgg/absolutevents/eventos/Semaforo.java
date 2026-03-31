package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SemaforoListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XMaterial;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Semaforo extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final SemaforoListener listener = new SemaforoListener();

    private final boolean hotbar;

    private boolean semaforoRunning;
    private boolean canWalk;
    private boolean ending;

    private final int green;
    private final int yellow;
    private final int red;

    private final String greenName;
    private final String yellowName;
    private final String redName;

    private String currentColorKey = "RED";

    private BukkitTask runnable;
    private BukkitTask greenTask;
    private BukkitTask yellowTask;
    private BukkitTask redTask;
    private BukkitTask resetCycleTask;
    private BukkitTask actionbarTask;

    public Semaforo(YamlConfiguration config) {
        super(config);

        this.config = config;
        this.canWalk = false;
        this.ending = false;
        this.hotbar = config.getBoolean("Evento.Show on hotbar");

        this.green = config.getInt("Evento.Green");
        this.yellow = config.getInt("Evento.Yellow");
        this.red = config.getInt("Evento.Red");

        this.greenName = ColorUtils.colorize(config.getString("Messages.Green item", ""));
        this.yellowName = ColorUtils.colorize(config.getString("Messages.Yellow item", ""));
        this.redName = ColorUtils.colorize(config.getString("Messages.Red item", ""));
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        cancelAllTasks();

        semaforoRunning = false;
        canWalk = false;
        ending = false;
        currentColorKey = "RED";

        startActionbar();

        runnable = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        stopMainTask();
                        return;
                    }

                    if (getPlayers().isEmpty()) {
                        noWinner();
                        return;
                    }

                    if (getPlayers().size() == 1) {
                        winner(getPlayers().get(0));
                        return;
                    }

                    if (!semaforoRunning) {
                        startCycle();
                    }
                },
                0L,
                20L
        );
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        stop();

        Player rewardTarget = player;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!rewardTarget.isOnline()) {
                return;
            }

            List<String> commands = config.getStringList("Rewards.Commands");
            for (String command : commands) {
                executeConsoleCommand(rewardTarget, command.replace("@winner", rewardTarget.getName()));
            }
        }, 2L);
    }

    public void noWinner() {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    @Override
    public void stop() {
        cancelAllTasks();

        semaforoRunning = false;
        canWalk = false;
        ending = false;
        currentColorKey = "RED";

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        super.remove(player);

        if (!isHappening() || ending) {
            return;
        }

        if (getPlayers().isEmpty()) {
            noWinner();
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    private void stopMainTask() {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
    }

    private void cancelAllTasks() {
        stopMainTask();
        cancelTask(greenTask);
        cancelTask(yellowTask);
        cancelTask(redTask);
        cancelTask(resetCycleTask);
        cancelTask(actionbarTask);

        greenTask = null;
        yellowTask = null;
        redTask = null;
        resetCycleTask = null;
        actionbarTask = null;
    }

    private void startCycle() {
        if (!isHappening() || ending) {
            return;
        }

        semaforoRunning = true;

        allowWalking();
        currentColorKey = "GREEN";
        broadcastMessages(config.getStringList("Messages.Green"));
        updateHotbar(XMaterial.LIME_TERRACOTTA, greenName);
        playSound("Sounds.Green");

        greenTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    currentColorKey = "YELLOW";
                    broadcastMessages(config.getStringList("Messages.Yellow"));
                    updateHotbar(XMaterial.YELLOW_TERRACOTTA, yellowName);
                    playSound("Sounds.Yellow");
                },
                green * 20L
        );

        redTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    denyWalking();
                    currentColorKey = "RED";
                    broadcastMessages(config.getStringList("Messages.Red"));
                    updateHotbar(XMaterial.RED_TERRACOTTA, redName);
                    playSound("Sounds.Red");
                },
                (green + yellow) * 20L
        );

        resetCycleTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    semaforoRunning = false;
                },
                (green + yellow + red) * 20L
        );
    }

    private void broadcastMessages(List<String> messages) {
        for (Player player : getPlayers()) {
            sendMessages(player, messages);
        }

        for (Player player : getSpectators()) {
            sendMessages(player, messages);
        }
    }

    private void sendMessages(Player player, List<String> messages) {
        for (String message : messages) {
            player.sendMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void updateHotbar(XMaterial material, String name) {
        if (!hotbar || !requireEmptyInventory()) {
            return;
        }

        ItemStack item = material.parseItem();
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }

        for (Player player : getPlayers()) {
            for (int i = 0; i < 9; i++) {
                player.getInventory().setItem(i, item.clone());
            }
            player.updateInventory();
        }
    }

    private void startActionbar() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending) {
                cancelTask(actionbarTask);
                return;
            }

            String colorText;
            switch (currentColorKey) {
                case "GREEN":
                    colorText = ColorUtils.colorize(config.getString("Messages.Green item", "&a&lVÁ!"));
                    break;
                case "YELLOW":
                    colorText = ColorUtils.colorize(config.getString("Messages.Yellow item", "&e&lATENÇÃO!"));
                    break;
                default:
                    colorText = ColorUtils.colorize(config.getString("Messages.Red item", "&c&lPARE!"));
                    break;
            }

            String format = config.getString("Messages.Actionbar", "&fSemáforo: @color");
            String parsed = ColorUtils.colorize(
                    format.replace("@color", colorText)
            );

            for (Player player : new ArrayList<>(getPlayers())) {
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(parsed));
            }
        }, 0L, 10L);
    }

    private void playSound(String path) {
        String soundName = config.getString(path);

        if (soundName == null || soundName.isBlank()) {
            return;
        }

        Sound sound = parseSound(soundName);
        if (sound == null) {
            return;
        }

        for (Player player : getPlayers()) {
            player.playSound(player.getLocation(), sound, 1f, 1f);
        }
    }

    private Sound parseSound(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);

        NamespacedKey directKey = NamespacedKey.fromString(normalized);
        if (directKey != null) {
            Sound direct = Registry.SOUNDS.get(directKey);
            if (direct != null) {
                return direct;
            }
        }

        String vanillaPath = normalized
                .replace("minecraft:", "")
                .replace('_', '.');

        Sound vanilla = Registry.SOUNDS.get(NamespacedKey.minecraft(vanillaPath));
        if (vanilla != null) {
            return vanilla;
        }

        return null;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public boolean canWalk() {
        return this.canWalk;
    }

    public void allowWalking() {
        this.canWalk = true;
    }

    public void denyWalking() {
        this.canWalk = false;
    }
}
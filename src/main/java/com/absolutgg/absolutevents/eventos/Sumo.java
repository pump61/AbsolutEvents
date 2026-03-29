package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SumoListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.CustomItemResolver;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class Sumo extends Evento {

    private final YamlConfiguration config;
    private final SumoListener listener = new SumoListener();

    private final boolean definedItems;
    private final int countdownSeconds;

    private boolean ending;
    private boolean pvpEnabled;

    private BukkitTask countdownTask;

    public Sumo(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.definedItems = config.getBoolean("Itens.Enabled", false);
        this.countdownSeconds = config.getInt("Evento.Countdown", 5);
        this.ending = false;
        this.pvpEnabled = false;
    }

    @Override
    public void start() {
        ending = false;
        pvpEnabled = false;

        AbsolutEventsPlugin.getInstance().getServer()
                .getPluginManager()
                .registerEvents(listener, AbsolutEventsPlugin.getInstance());

        listener.setEvento();

        if (definedItems) {
            for (Player player : new ArrayList<>(getPlayers())) {
                applyConfiguredItems(player);
            }
        }

        startCountdown();
    }

    @Override
    public void leave(Player player) {
        if (getPlayers().contains(player)) {
            String parsed = ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance().getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            );

            for (Player p : getPlayers()) {
                p.sendMessage(parsed);
            }
        }

        if (definedItems) {
            clearInventory(player);
        }

        remove(player);
        checkWinState();
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        List<String> broadcast = config.getStringList("Messages.Winner");

        for (String message : broadcast) {
            Bukkit.broadcastMessage(
                    ColorUtils.colorize(
                            message
                                    .replace("@winner", player.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        this.setWinner(player);

        String winnerName = player.getName();

        this.stop();

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    Player onlineWinner = Bukkit.getPlayerExact(winnerName);
                    if (onlineWinner == null || !onlineWinner.isOnline()) {
                        return;
                    }

                    List<String> commands = config.getStringList("Rewards.Commands");
                    for (String command : commands) {
                        executeConsoleCommand(onlineWinner, command.replace("@winner", onlineWinner.getName()));
                    }
                },
                5L
        );
    }

    public void noWinner() {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(
                    ColorUtils.colorize(
                            message.replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        stop();
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        if (definedItems) {
            clearInventory(player);
        }

        remove(player);

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(loseEvent);

        checkWinState();
    }

    @Override
    public void stop() {
        ending = false;
        pvpEnabled = false;

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (definedItems) {
            for (Player player : new ArrayList<>(getPlayers())) {
                clearInventory(player);
            }
        }

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    private void checkWinState() {
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

    private void startCountdown() {
        final int[] timeLeft = {countdownSeconds};

        countdownTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || ending) {
                        if (countdownTask != null) {
                            countdownTask.cancel();
                            countdownTask = null;
                        }
                        return;
                    }

                    if (timeLeft[0] > 0) {
                        for (Player player : getPlayers()) {
                            player.sendTitle(ColorUtils.colorize("&e&l" + timeLeft[0]), "", 0, 20, 0);
                        }

                        for (String message : config.getStringList("Messages.Countdown")) {
                            String parsed = ColorUtils.colorize(
                                    message
                                            .replace("@name", config.getString("Evento.Title"))
                                            .replace("@time", String.valueOf(timeLeft[0]))
                            );

                            for (Player player : getPlayers()) {
                                player.sendMessage(parsed);
                            }

                            for (Player player : getSpectators()) {
                                player.sendMessage(parsed);
                            }
                        }

                        timeLeft[0]--;
                        return;
                    }

                    pvpEnabled = true;

                    for (Player player : getPlayers()) {
                        player.sendTitle(ColorUtils.colorize("&a&lLUTEM!"), "", 0, 20, 10);
                    }

                    for (String message : config.getStringList("Messages.PvP enabled")) {
                        String parsed = ColorUtils.colorize(
                                message.replace("@name", config.getString("Evento.Title"))
                        );

                        for (Player player : getPlayers()) {
                            player.sendMessage(parsed);
                        }

                        for (Player player : getSpectators()) {
                            player.sendMessage(parsed);
                        }
                    }

                    countdownTask.cancel();
                    countdownTask = null;
                },
                0L,
                20L
        );
    }

    private void applyConfiguredItems(Player player) {
        clearInventory(player);

        ConfigurationSection itensSection = config.getConfigurationSection("Itens");
        if (itensSection != null) {
            EventKitApplier.apply(player, itensSection);
            return;
        }

        ItemStack helmet = getOptionalItem("Itens.Helmet");
        ItemStack chestplate = getOptionalItem("Itens.Chestplate");
        ItemStack leggings = getOptionalItem("Itens.Leggings");
        ItemStack boots = getOptionalItem("Itens.Boots");
        ItemStack offhand = getOptionalItem("Itens.Offhand");

        if (helmet != null) {
            player.getInventory().setHelmet(helmet);
        }

        if (chestplate != null) {
            player.getInventory().setChestplate(chestplate);
        }

        if (leggings != null) {
            player.getInventory().setLeggings(leggings);
        }

        if (boots != null) {
            player.getInventory().setBoots(boots);
        }

        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }

        ConfigurationSection inventory = config.getConfigurationSection("Itens.Inventory");
        if (inventory != null) {
            for (String slot : inventory.getKeys(false)) {
                ItemStack item = getOptionalItem("Itens.Inventory." + slot);
                if (item != null) {
                    player.getInventory().setItem(Integer.parseInt(slot), item);
                }
            }
        }

        player.updateInventory();
    }

    private ItemStack getOptionalItem(String path) {
        if (!config.isConfigurationSection(path)) {
            return null;
        }

        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null || section.getKeys(false).isEmpty()) {
            return null;
        }

        return CustomItemResolver.resolve(section);
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    public double getArenaMinY() {
        if (!config.isConfigurationSection("Locations.Pos1") || !config.isConfigurationSection("Locations.Pos2")) {
            return Double.NEGATIVE_INFINITY;
        }

        return Math.min(
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos2.y")
        );
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
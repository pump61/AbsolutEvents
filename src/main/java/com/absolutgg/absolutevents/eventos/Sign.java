package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SignListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.manager.ParkourRecordManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Sign extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final SignListener listener = new SignListener();
    private final boolean returnOnDamage;

    private boolean ending;
    private BukkitTask actionbarTask;

    private final Map<UUID, Long> startTimes = new HashMap<>();
    private final Map<UUID, Long> backCooldowns = new HashMap<>();

    public Sign(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.returnOnDamage = config.getBoolean("Evento.Return on damage");
        this.ending = false;
    }

    @Override
    public void startCall() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();
        listener.clearCheckpoints();

        super.startCall();
    }

    @Override
    public void start() {
        ending = false;

        startTimes.clear();
        backCooldowns.clear();

        long now = System.currentTimeMillis();

        for (Player player : getPlayers()) {
            startTimes.put(player.getUniqueId(), now);
            giveBackItem(player);
        }

        startActionbar();
    }

    @Override
    public void join(Player player) {
        super.join(player);
    }

    @Override
    public void leave(Player player) {
        listener.removeCheckpoint(player);
        clearPlayerData(player);

        super.leave(player);

        if (!isHappening() || ending) {
            return;
        }

        if (getPlayers().isEmpty()) {
            noWinner();
        }
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        List<Player> losers = new ArrayList<>(getPlayers());
        losers.removeIf(target -> target.getUniqueId().equals(player.getUniqueId()));

        long elapsed = getElapsedMillis(player);
        ParkourRecordManager.getInstance().updateRecord(getRecordKey(), player, elapsed);

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(
                    ColorUtils.colorize(
                            message.replace("@winner", player.getName())
                                    .replace("@name", getConfig().getString("Evento.Title"))
                                    .replace("@time", formatDuration(elapsed))
                                    .replace("@best", ParkourRecordManager.getInstance().getFormattedRecord(
                                            getRecordKey(),
                                            player.getUniqueId()
                                    ))
                    )
            );
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), getConfig().getString("Evento.Title"));

        setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        if (plugin.getLeagueManager() != null) {
            plugin.getLeagueManager().handleSoloWin(
                    player,
                    losers,
                    "sign"
            );
        }

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

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(
                    ColorUtils.colorize(
                            message.replace("@name", getConfig().getString("Evento.Title"))
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
                plugin.getConfig().getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        clearPlayerData(player);
        super.remove(player);

        if (!isHappening() || ending) {
            return;
        }

        if (getPlayers().isEmpty()) {
            noWinner();
        }
    }

    @Override
    public void stop() {
        ending = false;

        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        HandlerList.unregisterAll(listener);
        listener.clearCheckpoints();
        startTimes.clear();
        backCooldowns.clear();
        removePlayers();
    }

    public boolean returnsOnDamage() {
        return this.returnOnDamage;
    }

    public boolean isEnding() {
        return ending;
    }

    public String getRecordKey() {
        String filename = config.getString("filename", "");
        if (filename == null || filename.isBlank()) {
            return "sign";
        }
        return filename.replace(".yml", "").toLowerCase();
    }

    public long getElapsedMillis(Player player) {
        Long start = startTimes.get(player.getUniqueId());
        if (start == null) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - start);
    }

    public String getElapsedFormatted(Player player) {
        return formatDuration(getElapsedMillis(player));
    }

    public String getBestFormatted(Player player) {
        return ParkourRecordManager.getInstance().getFormattedRecord(getRecordKey(), player.getUniqueId());
    }

    public long getBackCooldownSeconds() {
        return config.getLong("Evento.Back item cooldown", 5L);
    }

    public boolean isBackOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        long expiresAt = backCooldowns.getOrDefault(player.getUniqueId(), 0L);
        return now < expiresAt;
    }

    public long getBackRemainingSeconds(Player player) {
        long now = System.currentTimeMillis();
        long expiresAt = backCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now >= expiresAt) {
            return 0L;
        }
        return Math.max(1L, (expiresAt - now + 999L) / 1000L);
    }

    public void applyBackCooldown(Player player) {
        long duration = getBackCooldownSeconds() * 1000L;
        backCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + duration);
    }

    public ItemStack createBackItem() {
        Material material = Material.matchMaterial(config.getString("Evento.Back item material", "SLIME_BALL"));
        if (material == null) {
            material = Material.SLIME_BALL;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(
                    config.getString("Messages.Back item", "&aVoltar ao Checkpoint")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isBackItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || item.getItemMeta() == null) {
            return false;
        }

        if (!item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String expected = ColorUtils.colorize(
                config.getString("Messages.Back item", "&aVoltar ao Checkpoint")
        );

        return item.getItemMeta().getDisplayName().equals(expected);
    }

    public boolean canUseBackItem(Player player) {
        return isHappening() && !ending && getPlayers().contains(player);
    }

    public void giveBackItem(Player player) {
        player.getInventory().setItem(config.getInt("Evento.Back item slot", 8), createBackItem());
        player.updateInventory();
    }

    public String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long centiseconds = (millis % 1000L) / 10L;
        return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds);
    }

    private void startActionbar() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending) {
                if (actionbarTask != null) {
                    actionbarTask.cancel();
                    actionbarTask = null;
                }
                return;
            }

            String format = config.getString(
                    "Messages.Actionbar",
                    "&fTempo: &a@time &8| &fRecorde: &e@best &8| &fCheckpoint: &b@cooldown"
            );

            for (Player player : getPlayers()) {
                String cooldown = isBackOnCooldown(player)
                        ? getBackRemainingSeconds(player) + "s"
                        : "Pronto";

                String parsed = ColorUtils.colorize(
                        format.replace("@time", getElapsedFormatted(player))
                                .replace("@best", getBestFormatted(player))
                                .replace("@cooldown", cooldown)
                );

                player.sendActionBar(
                        LegacyComponentSerializer.legacySection().deserialize(parsed)
                );
            }
        }, 0L, 10L);
    }

    private void clearPlayerData(Player player) {
        startTimes.remove(player.getUniqueId());
        backCooldowns.remove(player.getUniqueId());
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
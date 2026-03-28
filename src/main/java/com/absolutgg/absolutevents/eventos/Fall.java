package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.listeners.eventos.FallListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class Fall extends Evento {

    private final YamlConfiguration config;
    private final FallListener listener = new FallListener();

    private final int time;
    private final boolean effectsEnabled;
    private final int levitationTicks;
    private final int slowFallingTicks;

    private BukkitTask finishTask;
    private BukkitTask actionbarTask;
    private BukkitTask effectsTask;

    private int countdown;
    private boolean ending;

    public Fall(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.time = config.getInt("Evento.Time");
        this.effectsEnabled = config.getBoolean("Effects.Enabled", true);
        this.levitationTicks = Math.max(0, config.getInt("Effects.Levitation ticks", 30));
        this.slowFallingTicks = Math.max(0, config.getInt("Effects.Slow falling ticks", 220));
        this.countdown = time;
        this.ending = false;
    }

    @Override
    public void start() {
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(listener, AbsolutEventsPlugin.getInstance());
        listener.setEvento();

        this.countdown = time;
        this.ending = false;

        sendStartTitles();
        applyStartEffects();
        startActionbar();

        finishTask = Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    if (getPlayers().isEmpty()) {
                        noWinner();
                        return;
                    }

                    win();
                },
                time * 20L
        );
    }

    private void sendStartTitles() {
        if (!config.getBoolean("Title.Enabled", true)) {
            return;
        }

        String title = config.getString("Title.Start.Title", "&b&lASTRONAUTA");
        String subtitle = config.getString("Title.Start.Subtitle", "&fSobreviva por @time segundos");

        for (Player player : getPlayers()) {
            player.sendTitle(
                    ColorUtils.colorize(
                            title.replace("&", "§")
                                    .replace("@name", config.getString("Evento.Title"))
                    ),
                    ColorUtils.colorize(
                            subtitle.replace("&", "§")
                                    .replace("@time", String.valueOf(time))
                                    .replace("@name", config.getString("Evento.Title"))
                    ),
                    config.getInt("Title.FadeIn", 10),
                    config.getInt("Title.Stay", 40),
                    config.getInt("Title.FadeOut", 10)
            );
        }
    }

    private void applyStartEffects() {
        if (!effectsEnabled) {
            return;
        }

        for (Player player : getPlayers()) {
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);

            if (levitationTicks > 0) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.LEVITATION,
                        levitationTicks,
                        1,
                        false,
                        false,
                        true
                ));
            }
        }

        if (slowFallingTicks > 0) {
            effectsTask = Bukkit.getScheduler().runTaskLater(
                    AbsolutEventsPlugin.getInstance(),
                    () -> {
                        if (!isHappening() || ending) {
                            return;
                        }

                        for (Player player : getPlayers()) {
                            player.removePotionEffect(PotionEffectType.LEVITATION);
                            player.addPotionEffect(new PotionEffect(
                                    PotionEffectType.SLOW_FALLING,
                                    slowFallingTicks,
                                    0,
                                    false,
                                    false,
                                    true
                            ));
                        }
                    },
                    Math.max(1L, levitationTicks)
            );
        }
    }

    private void startActionbar() {
        actionbarTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    for (Player player : getPlayers()) {
                        String actionbar = config.getString(
                                "Messages.Actionbar",
                                "&bTempo restante: &f@time"
                        );

                        player.sendActionBar(ColorUtils.colorize(
                                actionbar.replace("&", "§")
                                        .replace("@time", countdown + "s")
                                        .replace("@players", String.valueOf(getPlayers().size()))
                        ));

                        if (countdown <= 3 && countdown > 0) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.2F);
                            player.sendTitle(
                                    ColorUtils.colorize("&c&l" + countdown),
                                    ColorUtils.colorize("&fAguente firme!"),
                                    0, 20, 0
                            );
                        }
                    }

                    if (countdown == 5) {
                        for (Player player : getPlayers()) {
                            String title = config.getString("Title.Final.Title", "&e&lFASE FINAL");
                            String subtitle = config.getString("Title.Final.Subtitle", "&fSegure por mais alguns segundos!");

                            player.sendTitle(
                                    ColorUtils.colorize(title.replace("&", "§")),
                                    ColorUtils.colorize(subtitle.replace("&", "§")),
                                    config.getInt("Title.FadeIn", 10),
                                    20,
                                    config.getInt("Title.FadeOut", 10)
                            );
                        }
                    }

                    if (countdown > 0) {
                        countdown--;
                    }
                },
                0L,
                20L
        );
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
                        .replace("&", "§")
        ));

        for (String message : config.getStringList("Messages.Player eliminated")) {
            sendToEvent(ColorUtils.colorize(
                    message.replace("&", "§")
                            .replace("@player", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        remove(player);

        if (getPlayers().isEmpty()) {
            noWinner();
        }
    }

    public void win() {
        if (ending) {
            return;
        }

        List<Player> winnersList = new ArrayList<>(getPlayers());

        if (winnersList.isEmpty()) {
            noWinner();
            return;
        }

        ending = true;

        List<String> winners = new ArrayList<>();
        this.setWinners();

        for (Player player : winnersList) {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
            winners.add(player.getName());
        }

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("&", "§")
                            .replace("@winner", String.join(", ", winners))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    public void noWinner() {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("&", "§")
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    private void sendToEvent(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(message);
        }
    }

    @Override
    public void stop() {
        if (finishTask != null) {
            finishTask.cancel();
            finishTask = null;
        }

        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }

        if (effectsTask != null) {
            effectsTask.cancel();
            effectsTask = null;
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            player.removePotionEffect(PotionEffectType.LEVITATION);
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
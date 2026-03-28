package com.absolutgg.absolutevents.api;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.EventoStartedEvent;
import com.absolutgg.absolutevents.api.events.EventoStartingEvent;
import com.absolutgg.absolutevents.api.events.EventoStopEvent;
import com.absolutgg.absolutevents.api.events.PlayerWinEvent;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventoChat implements EventoInterface {

    private final EventoType type;
    private boolean happening;

    private final boolean countParticipation;
    private final boolean countWin;
    private final String permission;
    private final YamlConfiguration config;
    private final double reward;

    public EventoChat(YamlConfiguration config) {
        this.config = config;
        this.type = EventoType.getEventoType(config.getString("Evento.Type"));
        this.permission = config.getString("Evento.Permission");

        if (config.isSet("custom_reward")) {
            this.reward = config.getDouble("custom_reward");
        } else {
            this.reward = -1;
        }

        switch (type) {
            case VOTACAO:
                this.countParticipation = false;
                this.countWin = false;
                break;

            default:
                this.countParticipation = config.getBoolean("Evento.Count participation");
                this.countWin = config.getBoolean("Evento.Count victory");
                break;
        }
    }

    public void startCall() {
        this.happening = true;

        EventoStartingEvent startingEvent = new EventoStartingEvent(getFileNameWithoutExtension(), type);
        Bukkit.getPluginManager().callEvent(startingEvent);

        new BukkitRunnable() {
            int calls = config.getInt("Evento.Calls");

            @Override
            public void run() {
                if (!EventoChat.this.isHappening() || !EventoChat.this.isOpen()) {
                    cancel();
                    return;
                }

                if (calls >= 0) {
                    List<String> broadcastMessages = getBroadcastMessages();
                    for (String message : broadcastMessages) {
                        parseMessage(message, calls);
                    }

                    calls--;
                    return;
                }

                cancel();
                start();

                EventoStartedEvent startedEvent = new EventoStartedEvent(getFileNameWithoutExtension(), type);
                Bukkit.getPluginManager().callEvent(startedEvent);
            }
        }.runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                0L,
                config.getInt("Evento.Calls interval") * 20L
        );
    }

    public void start() {
    }

    public void forceStart() {
        start();

        EventoStartedEvent startedEvent = new EventoStartedEvent(getFileNameWithoutExtension(), type);
        Bukkit.getPluginManager().callEvent(startedEvent);
    }

    public void winner(Player player) {
    }

    public void setWinner(Player player) {
        if (!this.countWin) {
            return;
        }

        List<String> winners = new ArrayList<>();
        winners.add(player.getUniqueId().toString());

        AbsolutEventsPlugin.getInstance().getConnectionManager().insertUser(player.getUniqueId());
        AbsolutEventsPlugin.getInstance().getConnectionManager().addWin(getFileNameWithoutExtension(), player.getUniqueId());
        AbsolutEventsPlugin.getInstance().getConnectionManager().setEventoWinner(getFileNameWithoutExtension(), winners);

        AbsolutEventsPlugin.getInstance().getCacheManager().updateCache();

        PlayerWinEvent winEvent = new PlayerWinEvent(player, getFileNameWithoutExtension(), type);
        Bukkit.getPluginManager().callEvent(winEvent);
    }

    public void setWinners() {
    }

    public void stop() {
    }

    public void removePlayers() {
        this.happening = false;

        EventoStopEvent stopEvent = new EventoStopEvent(getFileNameWithoutExtension(), type);
        Bukkit.getPluginManager().callEvent(stopEvent);

        AbsolutEventsPlugin.getInstance().getCacheManager().updateCache();
        AbsolutEventsPlugin.getInstance().getEventoChatManager().clearEvento();
    }

    public void join(Player player) {
    }

    public void leave(Player player) {
    }

    public void remove(Player player) {
    }

    public void remove(Player player, boolean leaved) {
    }

    public void spectate(Player player) {
    }

    protected List<String> getBroadcastMessages() {
        if (config.isList("Messages.Broadcast")) {
            return config.getStringList("Messages.Broadcast");
        }

        if (config.isString("Messages.Broadcast")) {
            return Collections.singletonList(config.getString("Messages.Broadcast", ""));
        }

        if (config.isList("Evento.Broadcast")) {
            return config.getStringList("Evento.Broadcast");
        }

        if (config.isString("Evento.Broadcast")) {
            return Collections.singletonList(config.getString("Evento.Broadcast", ""));
        }

        if (config.isList("Broadcast")) {
            return config.getStringList("Broadcast");
        }

        if (config.isString("Broadcast")) {
            return Collections.singletonList(config.getString("Broadcast", ""));
        }

        return Collections.emptyList();
    }

    public void parseMessage(String message, int calls) {
        String parsed = message
                .replace("@broadcasts", String.valueOf(calls))
                .replace("@calls", String.valueOf(calls))
                .replace("@name", config.getString("Evento.Title", getFileNameWithoutExtension()))
                .replace("@event", getFileNameWithoutExtension())
                .replace("@evento", getFileNameWithoutExtension());

        String colored = ColorUtils.colorize(parsed);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(colored);
        }

        Bukkit.getConsoleSender().sendMessage(colored);
    }

    public void parseCommand(Player player, String[] args) {
    }

    public void parsePlayerMessage(Player player, String message) {
    }

    protected void executeConsoleCommand(Player player, String command) {
        AbsolutEventsPlugin.getInstance().getServer().dispatchCommand(
                AbsolutEventsPlugin.getInstance().getServer().getConsoleSender(),
                command
        );
    }

    public YamlConfiguration getConfig() {
        return this.config;
    }

    public List<Player> getPlayers() {
        return Collections.emptyList();
    }

    public List<Player> getSpectators() {
        return Collections.emptyList();
    }

    public String getPermission() {
        return this.permission;
    }

    public EventoType getType() {
        return this.type;
    }

    public boolean isElimination() {
        return false;
    }

    public boolean isHappening() {
        return this.happening;
    }

    public boolean isOpen() {
        return true;
    }

    public boolean isSpectatorAllowed() {
        return false;
    }

    public boolean requireEmptyInventory() {
        return false;
    }

    public boolean countParticipation() {
        return this.countParticipation;
    }

    public boolean countWin() {
        return this.countWin;
    }

    public double getReward() {
        return this.reward;
    }

    protected String getFileNameWithoutExtension() {
        return config.getString("filename", "").replace(".yml", "");
    }
}
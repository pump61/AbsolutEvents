package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class Palavra extends EventoChat {

    private final YamlConfiguration config;
    private final String result;
    private final double reward;

    public Palavra(YamlConfiguration config) {
        super(config);
        this.config = config;

        List<String> words = config.getStringList("Words");
        if (words == null || words.isEmpty()) {
            this.result = "Palavra";
        } else {
            this.result = words.get(ThreadLocalRandom.current().nextInt(words.size()));
        }

        double baseReward = getReward();
        if (baseReward != -1) {
            this.reward = baseReward;
        } else if (config.isSet("Evento.Reward")) {
            this.reward = config.getDouble("Evento.Reward");
        } else if (config.isSet("custom_reward")) {
            this.reward = config.getDouble("custom_reward");
        } else {
            this.reward = 0D;
        }
    }

    @Override
    public void start() {
        for (String message : config.getStringList("Messages.No winner")) {
            sendGlobal(
                    applyBasePlaceholders(message, 0)
                            .replace("@result", result)
            );
        }

        stop();
    }

    @Override
    public void stop() {
        removePlayers();
    }

    @Override
    public void winner(Player player) {
        for (String message : config.getStringList("Messages.Winner")) {
            sendGlobal(
                    applyBasePlaceholders(message, 0)
                            .replace("@winner", player.getName())
                            .replace("@result", result)
            );
        }

        setWinner(player);

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        if (reward > 0) {
            AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, reward);
        }

        stop();
    }

    @Override
    public void parseMessage(String message, int calls) {
        sendGlobal(
                applyBasePlaceholders(message, calls)
                        .replace("@word", result)
        );
    }

    @Override
    public void parsePlayerMessage(Player player, String message) {
        if (!isHappening()) {
            return;
        }

        if (message == null) {
            return;
        }

        if (message.equals(result)) {
            winner(player);
        }
    }

    private void sendGlobal(String text) {
        String colored = ColorUtils.colorize(text);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(colored);
        }

        Bukkit.getConsoleSender().sendMessage(colored);
    }

    private String applyBasePlaceholders(String message, int calls) {
        String moneyText = getMoneyText();

        return message
                .replace("@broadcasts", String.valueOf(calls))
                .replace("@calls", String.valueOf(calls))
                .replace("@name", config.getString("Evento.Title", "Evento"))
                .replace("@event", getFileNameWithoutExtension())
                .replace("@evento", getFileNameWithoutExtension())
                .replace("@money", moneyText)
                .replace("@reward", moneyText);
    }

    private String getMoneyText() {
        double value;

        if (reward > 0) {
            value = reward;
        } else if (config.isSet("Evento.Reward")) {
            value = config.getDouble("Evento.Reward");
        } else if (config.isSet("Rewards.Money")) {
            value = config.getDouble("Rewards.Money");
        } else if (config.isSet("custom_reward")) {
            value = config.getDouble("custom_reward");
        } else {
            value = 0D;
        }

        if (value == (long) value) {
            return String.valueOf((long) value);
        }

        return String.format(Locale.US, "%.2f", value);
    }
}
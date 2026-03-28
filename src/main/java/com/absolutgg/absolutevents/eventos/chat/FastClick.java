package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.utils.ColorUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class FastClick extends EventoChat {

    private final YamlConfiguration config;
    private final String clickable;
    private final String notClickable;
    private final double reward;
    private final int totalLines;
    private final int quantity;
    private final int correctLine;
    private final int correctIndex;

    public FastClick(YamlConfiguration config) {
        super(config);
        this.config = config;

        double baseReward = getReward();
        if (baseReward != -1) {
            this.reward = baseReward;
        } else if (config.isSet("custom_reward")) {
            this.reward = config.getDouble("custom_reward");
        } else if (config.isSet("Evento.Reward")) {
            this.reward = config.getDouble("Evento.Reward");
        } else {
            this.reward = 0D;
        }

        this.clickable = config.getString("Messages.Clickable", "&b[X] ");
        this.notClickable = config.getString("Messages.Not clickable", "&3[X] ");
        this.totalLines = Math.max(1, config.getInt("Evento.Lines", 3));
        this.quantity = Math.max(1, config.getInt("Evento.Quantity", 12));

        this.correctLine = ThreadLocalRandom.current().nextInt(1, totalLines + 1);
        this.correctIndex = ThreadLocalRandom.current().nextInt(1, quantity + 1);
    }

    @Override
    public void start() {
        for (String message : config.getStringList("Messages.No winner")) {
            sendGlobal(applyBasePlaceholders(message, 0));
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
        if (!isHappening()) {
            stop();
            return;
        }

        String parsed = applyBasePlaceholders(message, calls);

        for (int line = 1; line <= totalLines; line++) {
            String token = "@line" + line;

            if (!parsed.contains(token)) {
                continue;
            }

            String baseText = ColorUtils.colorize(parsed.replace(token, ""));
            Component base = LegacyComponentSerializer.legacySection().deserialize(baseText);

            for (int index = 1; index <= quantity; index++) {
                String text = (line == correctLine && index == correctIndex)
                        ? clickable
                        : notClickable;

                Component clickComponent = LegacyComponentSerializer.legacySection()
                        .deserialize(ColorUtils.colorize(text))
                        .clickEvent(ClickEvent.runCommand("/evento " + line + " " + index));

                base = base.append(clickComponent);
            }

            for (Player online : Bukkit.getOnlinePlayers()) {
                online.sendMessage(base);
            }

            Bukkit.getConsoleSender().sendMessage(base);
            return;
        }

        Component component = LegacyComponentSerializer.legacySection()
                .deserialize(ColorUtils.colorize(parsed));

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(component);
        }

        Bukkit.getConsoleSender().sendMessage(component);
    }

    @Override
    public void parseCommand(Player player, String[] args) {
        if (!isHappening()) {
            stop();
            return;
        }

        if (args.length < 2) {
            sendWrong(player);
            return;
        }

        try {
            int line = Integer.parseInt(args[0]);
            int index = Integer.parseInt(args[1]);

            if (line == correctLine && index == correctIndex) {
                winner(player);
            } else {
                sendWrong(player);
            }
        } catch (NumberFormatException exception) {
            sendWrong(player);
        }
    }

    private void sendWrong(Player player) {
        player.sendMessage(ColorUtils.colorize(
                config.getString("Messages.Wrong", "&cVocê clicou na opção errada.")
        ));
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
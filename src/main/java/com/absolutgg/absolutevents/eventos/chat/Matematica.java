package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.NumberFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Matematica extends EventoChat {

    private final YamlConfiguration config;

    private String sum;
    private int result;

    private double reward = getReward();

    public Matematica(YamlConfiguration config) {
        super(config);

        this.config = config;

        if (this.reward == -1) {
            this.reward = config.getDouble("Evento.Reward");
        }

        int accountType = ThreadLocalRandom.current().nextInt(0, 2);

        int number1 = ThreadLocalRandom.current().nextInt(
                config.getInt("Evento.Min"),
                config.getInt("Evento.Max") + 1
        );

        int number2 = ThreadLocalRandom.current().nextInt(
                config.getInt("Evento.Min"),
                config.getInt("Evento.Max") + 1
        );

        switch (accountType) {
            case 0:
                sum = number1 + " + " + number2;
                result = number1 + number2;
                break;

            case 1:
            default:
                sum = number1 + " - " + number2;
                result = number1 - number2;
                break;
        }
    }

    @Override
    public void start() {
        for (String message : config.getStringList("Messages.No winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@result", String.valueOf(result))
                            .replace("@name", config.getString("Evento.Title"))
            ));
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
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@result", String.valueOf(result))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        this.setWinner(player);

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, this.reward);

        this.stop();
    }

    @Override
    public void parseMessage(String message, int calls) {
        String parsed = ColorUtils.colorize(
                message
                        .replace("@broadcasts", String.valueOf(calls))
                        .replace("@sum", sum)
                        .replace("@name", config.getString("Evento.Title"))
                        .replace("@reward", NumberFormatter.parse(this.reward))
        );

        AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(parsed);
    }

    @Override
    public void parsePlayerMessage(Player player, String message) {
        try {
            int integer = Integer.parseInt(message);

            if (integer == result) {
                winner(player);
            }

        } catch (NumberFormatException ignored) {
        }
    }
}
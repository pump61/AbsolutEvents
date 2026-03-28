package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.NumberFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class Loteria extends EventoChat {

    private final YamlConfiguration config;

    private final int number;
    private final int maxNumber;
    private final double cost;
    private double reward = getReward();

    public Loteria(YamlConfiguration config) {
        super(config);

        this.config = config;
        this.cost = config.getDouble("Evento.Cost");
        this.maxNumber = config.getInt("Evento.Max number");

        if (this.reward == -1) {
            this.reward = config.getDouble("Evento.Reward");
        }

        this.number = ThreadLocalRandom.current().nextInt(1, this.maxNumber + 1);
    }

    @Override
    public void start() {
        for (String message : config.getStringList("Messages.No winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@number", String.valueOf(this.number))
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
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@number", String.valueOf(this.number))
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
                        .replace("@name", config.getString("Evento.Title"))
                        .replace("@reward", NumberFormatter.parse(this.reward))
                        .replace("@cost", NumberFormatter.parse(this.cost))
                        .replace("@max", String.valueOf(this.maxNumber))
        );

        AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(parsed);
    }

    @Override
    public void parseCommand(Player player, String[] args) {

        if (args.length == 0) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Invalid", "&cNúmero inválido.")
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@max", String.valueOf(this.maxNumber))
            ));
            return;
        }

        if (AbsolutEventsPlugin.getInstance().getEconomy().getBalance(player) < this.cost) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.No money", "&cVocê não tem dinheiro suficiente.")
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@cost", NumberFormatter.parse(this.cost))
            ));
            return;
        }

        try {
            int playerNumber = Integer.parseInt(args[0]);

            if (playerNumber <= 0 || playerNumber > this.maxNumber) {
                player.sendMessage(ColorUtils.colorize(
                        config.getString("Messages.Invalid", "&cNúmero inválido.")
                                .replace("@name", config.getString("Evento.Title"))
                                .replace("@max", String.valueOf(this.maxNumber))
                ));
                return;
            }

            AbsolutEventsPlugin.getInstance().getEconomy().withdrawPlayer(player, this.cost);

            if (playerNumber == this.number) {
                winner(player);
            } else {
                player.sendMessage(ColorUtils.colorize(
                        config.getString("Messages.Lose", "&cVocê errou. Seu número foi @number.")
                                .replace("@name", config.getString("Evento.Title"))
                                .replace("@number", String.valueOf(playerNumber))
                ));
            }

        } catch (NumberFormatException exception) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Invalid", "&cNúmero inválido.")
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@max", String.valueOf(this.maxNumber))
            ));
        }
    }
}

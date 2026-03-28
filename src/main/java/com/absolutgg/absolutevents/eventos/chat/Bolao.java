package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Bolao extends EventoChat {

    private final YamlConfiguration config;

    private final List<Player> players = new ArrayList<>();
    private final double cost;

    private double reward = getReward();
    private boolean hasWinner = false;

    public Bolao(YamlConfiguration config) {
        super(config);

        this.config = config;
        this.cost = config.getDouble("Evento.Cost");

        if (this.reward == -1) {
            this.reward = config.getDouble("Evento.Reward");
        }
    }

    @Override
    public void start() {
        if (players.isEmpty()) {
            for (String message : config.getStringList("Messages.No winner")) {
                Bukkit.broadcastMessage(ColorUtils.colorize(
                        message.replace("@name", config.getString("Evento.Title", "Bolão"))
                ));
            }

            stop();
            return;
        }

        Random random = new Random();
        winner(players.get(random.nextInt(players.size())));
    }

    @Override
    public void stop() {
        if (!hasWinner) {
            for (Player player : new ArrayList<>(players)) {
                AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, cost);
            }
        }

        players.clear();
        removePlayers();
    }

    @Override
    public void leave(Player player) {
        if (players.remove(player)) {
            this.reward -= this.cost;
            AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, cost);
        }
    }

    @Override
    public void winner(Player player) {
        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title", "Bolão"))
            ));
        }

        hasWinner = true;
        this.setWinner(player);

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, this.reward);

        this.stop();
    }

    @Override
    public void parseMessage(String message, int calls) {
        Bukkit.broadcastMessage(ColorUtils.colorize(
                message
                        .replace("@broadcasts", String.valueOf(calls))
                        .replace("@name", config.getString("Evento.Title", "Bolão"))
                        .replace("@reward", NumberFormatter.parse(this.reward))
                        .replace("@cost", NumberFormatter.parse(this.cost))
                        .replace("@players", String.valueOf(players.size()))
        ));
    }

    @Override
    public void parseCommand(Player player, String[] args) {
        if (players.contains(player)) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Already joined", "&cVocê já entrou no bolão.")
                            .replace("@name", config.getString("Evento.Title", "Bolão"))
            ));
            return;
        }

        if (AbsolutEventsPlugin.getInstance().getEconomy().getBalance(player) < this.cost) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.No money", "&cVocê não tem dinheiro suficiente.")
                            .replace("@name", config.getString("Evento.Title", "Bolão"))
                            .replace("@cost", NumberFormatter.parse(this.cost))
            ));
            return;
        }

        AbsolutEventsPlugin.getInstance().getEconomy().withdrawPlayer(player, this.cost);
        this.reward += this.cost;

        players.add(player);

        player.sendMessage(ColorUtils.colorize(
                config.getString("Messages.Joined", "&aVocê entrou no bolão.")
                        .replace("@name", config.getString("Evento.Title", "Bolão"))
        ));
    }

    @Override
    public List<Player> getPlayers() {
        return this.players;
    }
}
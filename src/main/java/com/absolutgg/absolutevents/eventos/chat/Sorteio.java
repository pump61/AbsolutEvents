// ==================== Sorteio.java ====================
package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.NumberFormatter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Sorteio extends EventoChat {

    private final YamlConfiguration config;
    private final List<Player> players = new ArrayList<>();
    private final long cost;

    private boolean hasWinner = false;

    public Sorteio(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.cost = config.getLong("Evento.Cost");
    }

    @Override
    public void start() {

        if (players.isEmpty()) {
            for (String message : config.getStringList("Messages.No winner")) {
                AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                        message.replace("@name", config.getString("Evento.Title"))
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
            AbsolutEventsPlugin.getInstance().getEconomy().depositPlayer(player, cost);
        }
    }

    @Override
    public void winner(Player player) {
        for (String message : config.getStringList("Messages.Winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        hasWinner = true;
        this.setWinner(player);

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        this.stop();
    }

    @Override
    public void parseMessage(String message, int calls) {
        String parsed = ColorUtils.colorize(
                message
                        .replace("@broadcasts", String.valueOf(calls))
                        .replace("@name", config.getString("Evento.Title"))
                        .replace("@cost", NumberFormatter.parse(this.cost))
                        .replace("@players", String.valueOf(players.size()))
        );

        AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(parsed);
    }

    @Override
    public void parseCommand(Player player, String[] args) {

        if (players.contains(player)) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Already joined", "&cVocê já entrou no sorteio.")
                            .replace("@name", config.getString("Evento.Title"))
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

        AbsolutEventsPlugin.getInstance().getEconomy().withdrawPlayer(player, this.cost);

        players.add(player);
        player.sendMessage(ColorUtils.colorize(
                config.getString("Messages.Joined", "&aVocê entrou no sorteio.")
                        .replace("@name", config.getString("Evento.Title"))
        ));
    }

    @Override
    public List<Player> getPlayers() {
        return this.players;
    }
}
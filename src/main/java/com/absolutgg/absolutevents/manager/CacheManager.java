package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.absolutgg.absolutevents.utils.MenuConfigFile;
import com.iridium.iridiumcolorapi.IridiumColorAPI;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class CacheManager {

    private final Map<OfflinePlayer, Map<String, Integer>> playerWins = new ConcurrentHashMap<>();
    private final Map<OfflinePlayer, Map<String, Integer>> playerParticipations = new ConcurrentHashMap<>();

    private LinkedHashMap<OfflinePlayer, Integer> topPlayerWins = new LinkedHashMap<>();
    private LinkedHashMap<OfflinePlayer, Integer> topPlayerParticipations = new LinkedHashMap<>();

    private final LinkedHashMap<OfflinePlayer, List<String>> menuWinsData = new LinkedHashMap<>();
    private final LinkedHashMap<OfflinePlayer, List<String>> menuParticipationsData = new LinkedHashMap<>();

    public Map<String, Integer> getPlayerWins(OfflinePlayer player) {
        return playerWins.getOrDefault(player, Collections.emptyMap());
    }

    public Map<String, Integer> getPlayerParticipations(OfflinePlayer player) {
        return playerParticipations.getOrDefault(player, Collections.emptyMap());
    }

    public Map<OfflinePlayer, Map<String, Integer>> getPlayerWinsList() {
        return playerWins;
    }

    public Map<OfflinePlayer, Map<String, Integer>> getPlayerParticipationsList() {
        return playerParticipations;
    }

    public void updateCache() {
        playerWins.clear();
        playerParticipations.clear();

        topPlayerWins.clear();
        topPlayerParticipations.clear();

        menuWinsData.clear();
        menuParticipationsData.clear();

        AbsolutEventsPlugin.getInstance().getConnectionManager().getPlayersWins();
        AbsolutEventsPlugin.getInstance().getConnectionManager().getPlayersParticipations();

        calculateTopWins();
        calculateTopParticipations();
    }

    public int getPlayerTopWinsPosition(OfflinePlayer player) {
        int position = 1;

        for (OfflinePlayer current : topPlayerWins.keySet()) {
            if (current.equals(player)) {
                return position;
            }
            position++;
        }

        return 0;
    }

    public int getPlayerTopParticipationsPosition(OfflinePlayer player) {
        int position = 1;

        for (OfflinePlayer current : topPlayerParticipations.keySet()) {
            if (current.equals(player)) {
                return position;
            }
            position++;
        }

        return 0;
    }

    public LinkedHashMap<OfflinePlayer, Integer> getPlayerTopWinsList() {
        return topPlayerWins;
    }

    public LinkedHashMap<OfflinePlayer, Integer> getPlayerTopParticipationsList() {
        return topPlayerParticipations;
    }

    public LinkedHashMap<OfflinePlayer, List<String>> getTopWinsMenuItems() {
        if (!menuWinsData.isEmpty()) {
            return menuWinsData;
        }

        YamlConfiguration config = MenuConfigFile.get("top_players");
        int position = 1;

        for (OfflinePlayer player : topPlayerWins.keySet()) {
            int totalWins = getPlayerWins(player).values().stream().mapToInt(Integer::intValue).sum();
            int totalParticipations = getPlayerParticipations(player).values().stream().mapToInt(Integer::intValue).sum();

            if (totalWins == 0) {
                continue;
            }

            List<String> lore = new ArrayList<>();

            for (String line : config.getStringList("Menu.Items.Player.Lore")) {
                lore.add(IridiumColorAPI.process(
                        line.replace("@position", String.valueOf(position))
                                .replace("@total_wins", String.valueOf(totalWins))
                                .replace("@total_participations", String.valueOf(totalParticipations))
                                .replace("&", "§")
                ));
            }

            if (config.getBoolean("Eventos.Enabled")) {
                boolean hasData = false;

                for (String eventLine : config.getStringList("Eventos.List")) {
                    String[] split = eventLine.split(":");

                    if (split.length < 2) {
                        continue;
                    }

                    if (!EventoConfigFile.exists(split[0])) {
                        continue;
                    }

                    int wins = getPlayerWins(player).getOrDefault(split[0], 0);
                    int participations = getPlayerParticipations(player).getOrDefault(split[0], 0);

                    if (config.getBoolean("Eventos.Only with wins") && wins == 0 && participations == 0) {
                        continue;
                    }

                    hasData = true;

                    lore.add(IridiumColorAPI.process(
                            config.getString("Eventos.Format", "&7@evento_name: @evento_wins/@evento_participations")
                                    .replace("@evento_name", split[1])
                                    .replace("@evento_wins", String.valueOf(wins))
                                    .replace("@evento_participations", String.valueOf(participations))
                                    .replace("&", "§")
                    ));
                }

                if (!hasData) {
                    lore.add(IridiumColorAPI.process(
                            config.getString("Eventos.Empty", "&7Sem dados.").replace("&", "§")
                    ));
                }

                if (config.getBoolean("Eventos.New line")) {
                    lore.add("");
                }
            }

            menuWinsData.put(player, lore);
            position++;
        }

        return menuWinsData;
    }

    public LinkedHashMap<OfflinePlayer, List<String>> getTopParticipationsMenuItems() {
        if (!menuParticipationsData.isEmpty()) {
            return menuParticipationsData;
        }

        YamlConfiguration config = MenuConfigFile.get("top_players");
        int position = 1;

        for (OfflinePlayer player : topPlayerParticipations.keySet()) {
            int totalWins = getPlayerWins(player).values().stream().mapToInt(Integer::intValue).sum();
            int totalParticipations = getPlayerParticipations(player).values().stream().mapToInt(Integer::intValue).sum();

            if (totalParticipations == 0) {
                continue;
            }

            List<String> lore = new ArrayList<>();

            for (String line : config.getStringList("Menu.Items.Player.Lore")) {
                lore.add(IridiumColorAPI.process(
                        line.replace("@position", String.valueOf(position))
                                .replace("@total_wins", String.valueOf(totalWins))
                                .replace("@total_participations", String.valueOf(totalParticipations))
                                .replace("&", "§")
                ));
            }

            if (config.getBoolean("Eventos.Enabled")) {
                boolean hasData = false;

                for (String eventLine : config.getStringList("Eventos.List")) {
                    String[] split = eventLine.split(":");

                    if (split.length < 2) {
                        continue;
                    }

                    if (!EventoConfigFile.exists(split[0])) {
                        continue;
                    }

                    int wins = getPlayerWins(player).getOrDefault(split[0], 0);
                    int participations = getPlayerParticipations(player).getOrDefault(split[0], 0);

                    if (config.getBoolean("Eventos.Only with wins") && wins == 0 && participations == 0) {
                        continue;
                    }

                    hasData = true;

                    lore.add(IridiumColorAPI.process(
                            config.getString("Eventos.Format", "&7@evento_name: @evento_wins/@evento_participations")
                                    .replace("@evento_name", split[1])
                                    .replace("@evento_wins", String.valueOf(wins))
                                    .replace("@evento_participations", String.valueOf(participations))
                                    .replace("&", "§")
                    ));
                }

                if (!hasData) {
                    lore.add(IridiumColorAPI.process(
                            config.getString("Eventos.Empty", "&7Sem dados.").replace("&", "§")
                    ));
                }

                if (config.getBoolean("Eventos.New line")) {
                    lore.add("");
                }
            }

            menuParticipationsData.put(player, lore);
            position++;
        }

        return menuParticipationsData;
    }

    public void calculateTopWins() {
        Map<OfflinePlayer, Integer> temp = new HashMap<>();

        for (OfflinePlayer player : playerWins.keySet()) {
            int total = playerWins.get(player)
                    .values()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum();

            temp.put(player, total);
        }

        topPlayerWins = temp.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }

    public void calculateTopParticipations() {
        Map<OfflinePlayer, Integer> temp = new HashMap<>();

        for (OfflinePlayer player : playerParticipations.keySet()) {
            int total = playerParticipations.get(player)
                    .values()
                    .stream()
                    .mapToInt(Integer::intValue)
                    .sum();

            temp.put(player, total);
        }

        topPlayerParticipations = temp.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
    }
}
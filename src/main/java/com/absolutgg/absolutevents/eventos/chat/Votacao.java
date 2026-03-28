package com.absolutgg.absolutevents.eventos.chat;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoChat;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class Votacao extends EventoChat {

    private final YamlConfiguration config;
    private final ConfigurationSection alternativesSection;

    private final LinkedHashMap<Integer, VoteOption> options = new LinkedHashMap<>();
    private final LinkedHashMap<Player, Integer> votes = new LinkedHashMap<>();

    private final Random random = new Random();

    public Votacao(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.alternativesSection = config.getConfigurationSection("Alternatives");

        loadAlternatives();

        if (options.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Nenhuma alternativa válida foi encontrada no evento de votação.");
        }
    }

    @Override
    public void start() {
        if (options.isEmpty()) {
            stop();
            return;
        }

        if (votes.isEmpty()) {
            broadcastNoVotesMessage();
            int selectedId = getRandomOptionId();
            startSelectedEvent(selectedId);
            stop();
            return;
        }

        LinkedHashMap<Integer, Integer> totalVotes = countVotes();
        List<Integer> winners = getTopVotedOptions(totalVotes);

        if (winners.isEmpty()) {
            int selectedId = getRandomOptionId();
            startSelectedEvent(selectedId);
            stop();
            return;
        }

        int winnerId = winners.size() == 1
                ? winners.get(0)
                : winners.get(random.nextInt(winners.size()));

        int winnerVotes = totalVotes.getOrDefault(winnerId, 0);
        float percent = votes.isEmpty() ? 0.0f : (winnerVotes * 100.0f) / getTotalVoteWeight();

        broadcastWinnerMessage(options.get(winnerId).displayName(), winnerVotes, percent);
        startSelectedEvent(winnerId);
        stop();
    }

    @Override
    public void stop() {
        removePlayers();
    }

    @Override
    public void parseMessage(String message, int calls) {
        String parsed = message
                .replace("@broadcasts", String.valueOf(calls))
                .replace("@name", config.getString("Evento.Title", "Votação"));

        if (parsed.contains("@alternatives")) {
            String before = parsed.replace("@alternatives", "");
            if (!before.isBlank()) {
                Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.colorize(before)));
            }

            broadcastAlternativesList();
            return;
        }

        for (Map.Entry<Integer, VoteOption> entry : options.entrySet()) {
            int id = entry.getKey();
            VoteOption option = entry.getValue();

            if (!parsed.contains("@alternative" + id)) {
                continue;
            }

            int currentVotes = getVotesFor(id);
            float percentage = getTotalVoteWeight() == 0
                    ? 0.0f
                    : (currentVotes * 100.0f) / getTotalVoteWeight();

            String finalLine = parsed
                    .replace("@alternative" + id, option.displayName())
                    .replace("@id", String.valueOf(id))
                    .replace("@votes", String.valueOf(currentVotes))
                    .replace("@percentage", String.valueOf((int) Math.floor(percentage)));

            Component component = buildVoteComponent(id, option.displayName(), finalLine);
            Bukkit.broadcast(component);
            return;
        }

        for (Map.Entry<Integer, VoteOption> entry : options.entrySet()) {
            parsed = parsed.replace("@alternative" + entry.getKey(), entry.getValue().displayName());
        }

        Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(ColorUtils.colorize(parsed)));
    }

    @Override
    public void parseCommand(Player player, String[] args) {
        if (args.length == 0) {
            sendInvalidVote(player);
            return;
        }

        if (votes.containsKey(player)) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Already voted", "&cVocê já votou.")
                            .replace("@name", config.getString("Evento.Title", "Votação"))
            ));
            return;
        }

        try {
            int voteId = Integer.parseInt(args[0]);

            if (!options.containsKey(voteId)) {
                sendInvalidVote(player);
                return;
            }

            votes.put(player, voteId);

            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Voted", "&aVocê votou em @alternative.")
                            .replace("@name", config.getString("Evento.Title", "Votação"))
                            .replace("@alternative", options.get(voteId).displayName())
            ));

        } catch (NumberFormatException exception) {
            sendInvalidVote(player);
        }
    }

    @Override
    public void leave(Player player) {
        votes.remove(player);
    }

    @Override
    public List<Player> getPlayers() {
        return new ArrayList<>(votes.keySet());
    }

    private void loadAlternatives() {
        if (alternativesSection == null) {
            return;
        }

        int id = 1;

        for (String key : alternativesSection.getKeys(false)) {
            ConfigurationSection section = alternativesSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            String displayName = section.getString("Name", key);
            String fileName = section.getString("File", key);

            options.put(id, new VoteOption(id, displayName, fileName));
            id++;
        }
    }

    private void broadcastAlternativesList() {
        String format = config.getString(
                "Messages.Alternative format",
                "&e[@id] &f@alternative &7- &a@votes votos &7(@percentage%)"
        );

        for (Map.Entry<Integer, VoteOption> entry : options.entrySet()) {
            int id = entry.getKey();
            VoteOption option = entry.getValue();

            int currentVotes = getVotesFor(id);
            float percentage = getTotalVoteWeight() == 0
                    ? 0.0f
                    : (currentVotes * 100.0f) / getTotalVoteWeight();

            String line = ColorUtils.colorize(
                    format
                            .replace("@id", String.valueOf(id))
                            .replace("@alternative", option.displayName())
                            .replace("@votes", String.valueOf(currentVotes))
                            .replace("@percentage", String.valueOf((int) Math.floor(percentage)))
                            .replace("@name", config.getString("Evento.Title", "Votação"))
            );

            Component component = buildVoteComponent(id, option.displayName(), line);
            Bukkit.broadcast(component);
        }
    }

    private Component buildVoteComponent(int id, String alternativeName, String line) {
        Component base = LegacyComponentSerializer.legacySection().deserialize(line);

        String hoverText = ColorUtils.colorize(
                config.getString("Messages.Hover", "&aClique para votar em @alternative")
                        .replace("@alternative", alternativeName)
                        .replace("@id", String.valueOf(id))
        );

        Component hover = LegacyComponentSerializer.legacySection().deserialize(hoverText);

        return base.hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.runCommand("/evento " + id));
    }

    private LinkedHashMap<Integer, Integer> countVotes() {
        LinkedHashMap<Integer, Integer> totalVotes = new LinkedHashMap<>();

        for (Integer id : options.keySet()) {
            totalVotes.put(id, 0);
        }

        for (Map.Entry<Player, Integer> entry : votes.entrySet()) {
            Player player = entry.getKey();
            Integer votedId = entry.getValue();

            if (!options.containsKey(votedId)) {
                continue;
            }

            int weight = getVoteWeight(player);
            totalVotes.put(votedId, totalVotes.get(votedId) + weight);
        }

        return totalVotes;
    }

    private List<Integer> getTopVotedOptions(Map<Integer, Integer> totalVotes) {
        List<Integer> top = new ArrayList<>();
        int highest = Integer.MIN_VALUE;

        for (Map.Entry<Integer, Integer> entry : totalVotes.entrySet()) {
            int id = entry.getKey();
            int votesCount = entry.getValue();

            if (votesCount > highest) {
                highest = votesCount;
                top.clear();
                top.add(id);
            } else if (votesCount == highest) {
                top.add(id);
            }
        }

        return top;
    }

    private int getVotesFor(int optionId) {
        int total = 0;

        for (Map.Entry<Player, Integer> entry : votes.entrySet()) {
            if (entry.getValue() == optionId) {
                total += getVoteWeight(entry.getKey());
            }
        }

        return total;
    }

    private int getVoteWeight(Player player) {
        ConfigurationSection weightSection = config.getConfigurationSection("VoteWeight");
        if (weightSection == null) {
            return 1;
        }

        int best = 1;

        for (String permission : weightSection.getKeys(false)) {
            int weight = weightSection.getInt(permission, 1);
            if (player.hasPermission(permission) && weight > best) {
                best = weight;
            }
        }

        return best;
    }

    private int getTotalVoteWeight() {
        int total = 0;

        for (Player player : votes.keySet()) {
            total += getVoteWeight(player);
        }

        return total;
    }

    private int getRandomOptionId() {
        List<Integer> ids = new ArrayList<>(options.keySet());
        return ids.get(random.nextInt(ids.size()));
    }

    private void startSelectedEvent(int optionId) {
        VoteOption option = options.get(optionId);
        if (option == null) {
            return;
        }

        YamlConfiguration eventConfig = EventoConfigFile.get(option.fileName());
        if (eventConfig == null) {
            return;
        }

        EventoType type = EventoType.getEventoType(eventConfig.getString("Evento.Type"));

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (EventoType.isEventoChat(type)) {
                        AbsolutEventsPlugin.getInstance().getEventoChatManager().startEvento(type, eventConfig);
                    } else {
                        AbsolutEventsPlugin.getInstance().getEventoManager().startEvento(type, eventConfig);
                    }
                },
                20L
        );
    }

    private void broadcastNoVotesMessage() {
        for (String message : config.getStringList("Messages.No votes")) {
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(
                    ColorUtils.colorize(
                            message.replace("@name", config.getString("Evento.Title", "Votação"))
                    )
            ));
        }
    }

    private void broadcastWinnerMessage(String winnerName, int winnerVotes, float percent) {
        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcast(LegacyComponentSerializer.legacySection().deserialize(
                    ColorUtils.colorize(
                            message
                                    .replace("@winner", winnerName)
                                    .replace("@votes", String.valueOf(winnerVotes))
                                    .replace("@percentage", String.valueOf((int) Math.floor(percent)))
                                    .replace("@name", config.getString("Evento.Title", "Votação"))
                    )
            ));
        }
    }

    private void sendInvalidVote(Player player) {
        player.sendMessage(ColorUtils.colorize(
                config.getString("Messages.Invalid", "&cVoto inválido.")
                        .replace("@name", config.getString("Evento.Title", "Votação"))
        ));
    }

    private record VoteOption(int id, String displayName, String fileName) {
    }
}

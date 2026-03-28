package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.listeners.eventos.QuizListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.Cuboid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public final class Quiz extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final QuizListener listener = new QuizListener();

    private final LinkedHashMap<String, String> questions = new LinkedHashMap<>();
    private final int time;
    private final int delay;
    private final int maxQuestions;
    private final int firstQuestionDelay;

    private final Cuboid trueCuboid;
    private final Cuboid falseCuboid;
    private final Cuboid middleCuboid;

    private BukkitTask runnable;
    private BukkitTask countdownTask;

    private boolean questionHappening;
    private boolean lockedAnswers;
    private boolean ending;
    private boolean waitingFirstQuestion;
    private int totalQuestions;

    public Quiz(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.time = this.config.getInt("Evento.Time");
        this.delay = this.config.getInt("Evento.Interval");
        this.maxQuestions = this.config.getInt("Evento.Max questions");
        this.firstQuestionDelay = this.config.getInt("Evento.First question delay", 5);

        String worldName = this.config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Quiz: Locations.Pos1.world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Quiz: mundo '" + worldName + "' não está carregado.");
        }

        Location pos1 = new Location(
                world,
                this.config.getDouble("Locations.Pos1.x"),
                this.config.getDouble("Locations.Pos1.y"),
                this.config.getDouble("Locations.Pos1.z")
        );
        Location pos2 = new Location(
                world,
                this.config.getDouble("Locations.Pos2.x"),
                this.config.getDouble("Locations.Pos2.y"),
                this.config.getDouble("Locations.Pos2.z")
        );
        this.trueCuboid = new Cuboid(pos1, pos2);

        Location pos3 = new Location(
                world,
                this.config.getDouble("Locations.Pos3.x"),
                this.config.getDouble("Locations.Pos3.y"),
                this.config.getDouble("Locations.Pos3.z")
        );
        Location pos4 = new Location(
                world,
                this.config.getDouble("Locations.Pos4.x"),
                this.config.getDouble("Locations.Pos4.y"),
                this.config.getDouble("Locations.Pos4.z")
        );
        this.falseCuboid = new Cuboid(pos3, pos4);

        Location pos5 = new Location(
                world,
                this.config.getDouble("Locations.Pos5.x"),
                this.config.getDouble("Locations.Pos5.y"),
                this.config.getDouble("Locations.Pos5.z")
        );
        Location pos6 = new Location(
                world,
                this.config.getDouble("Locations.Pos6.x"),
                this.config.getDouble("Locations.Pos6.y"),
                this.config.getDouble("Locations.Pos6.z")
        );
        this.middleCuboid = new Cuboid(pos5, pos6);

        for (String entry : config.getStringList("Questions")) {
            String[] separated = entry.split("-", 2);

            if (separated.length == 1) {
                questions.put(separated[0].trim(), null);
            } else {
                questions.put(separated[0].trim(), separated[1].trim());
            }
        }
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        cancelRunnable();
        cancelCountdown();
        questionHappening = false;
        lockedAnswers = false;
        ending = false;
        waitingFirstQuestion = true;
        totalQuestions = 0;

        sendFirstQuestionWarning(firstQuestionDelay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            teleportPlayersToMiddle();
            waitingFirstQuestion = false;
        }, firstQuestionDelay * 20L);

        runnable = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        cancelRunnable();
                        cancelCountdown();
                        return;
                    }

                    if (waitingFirstQuestion) {
                        return;
                    }

                    if (getPlayers().isEmpty()) {
                        noWinners();
                        cancelRunnable();
                        cancelCountdown();
                        return;
                    }

                    if (getPlayers().size() == 1) {
                        Player winner = getPlayers().get(0);
                        winner(winner);
                        cancelRunnable();
                        cancelCountdown();
                        return;
                    }

                    if (questionHappening) {
                        return;
                    }

                    if (totalQuestions < maxQuestions && !questions.isEmpty()) {
                        question();
                    } else {
                        win();
                        cancelRunnable();
                        cancelCountdown();
                    }
                },
                0L,
                20L
        );
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", getConfig().getString("Evento.Title"))
            ));
        }

        setWinner(player);
        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }, 5L);
    }

    public void win() {
        if (ending) {
            return;
        }

        ending = true;

        List<Player> winnersNow = new ArrayList<>(getPlayers());
        List<String> winners = winnersNow.stream().map(Player::getName).collect(Collectors.toList());

        setWinners(new HashSet<>(winnersNow));

        for (String message : this.config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", String.join(", ", winners))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : winnersNow) {
                if (!player.isOnline()) {
                    continue;
                }

                for (String command : this.config.getStringList("Rewards.Commands")) {
                    executeConsoleCommand(player, command.replace("@winner", player.getName()));
                }
            }
        }, 5L);
    }

    public void noWinners() {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : this.config.getStringList("Messages.No winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    @Override
    public void stop() {
        cancelRunnable();
        cancelCountdown();
        questionHappening = false;
        lockedAnswers = false;
        waitingFirstQuestion = false;
        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                plugin.getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(loseEvent);

        remove(player);
    }

    private void question() {
        if (!isHappening() || ending || questions.isEmpty()) {
            return;
        }

        List<String> questionValues = new ArrayList<>(questions.keySet());
        List<String> questionBroadcast = config.getStringList("Messages.Question");
        totalQuestions++;
        questionHappening = true;
        lockedAnswers = false;

        String questionText = questionValues.get(new Random().nextInt(questionValues.size()));
        String answer = questions.remove(questionText);

        for (Player player : getPlayers()) {
            for (String message : questionBroadcast) {
                player.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@currentquestion", String.valueOf(totalQuestions))
                                .replace("@question", questionText)
                                .replace("@name", this.config.getString("Evento.Title"))
                ));
            }

            sendConfiguredTitle(player, "Title.Question",
                    "@question", questionText,
                    "@currentquestion", String.valueOf(totalQuestions),
                    "@name", this.config.getString("Evento.Title"));
        }

        for (Player player : getSpectators()) {
            for (String message : questionBroadcast) {
                player.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@currentquestion", String.valueOf(totalQuestions))
                                .replace("@question", questionText)
                                .replace("@name", this.config.getString("Evento.Title"))
                ));
            }
        }

        startCountdown();

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> finishQuestion(answer),
                time * 20L
        );
    }

    private void startCountdown() {
        final int[] secondsLeft = {time};

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening() || ending || !questionHappening) {
                cancelCountdown();
                return;
            }

            String actionbarFormat = config.getString(
                    "Messages.Countdown actionbar",
                    "&eResponda em &f@time &esegundos &8| &aVerde = Verdadeiro &8| &cVermelho = Falso"
            );

            for (Player player : getPlayers()) {
                player.sendActionBar(ColorUtils.colorize(
                        actionbarFormat.replace("@time", String.valueOf(secondsLeft[0]))
                ));

                if (secondsLeft[0] <= 3 && secondsLeft[0] > 0) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0F, 1.2F);
                    player.sendTitle(
                            ColorUtils.colorize("&c&l" + secondsLeft[0]),
                            ColorUtils.colorize("&fCorra para a resposta!"),
                            0, 20, 0
                    );
                }
            }

            secondsLeft[0]--;

            if (secondsLeft[0] < 0) {
                cancelCountdown();
            }
        }, 0L, 20L);
    }

    private void finishQuestion(String answer) {
        if (!isHappening() || ending || !questionHappening) {
            return;
        }

        lockedAnswers = true;
        cancelCountdown();

        List<Player> correctPlayers = new ArrayList<>();
        List<Player> wrongPlayers = new ArrayList<>();

        for (Player player : new ArrayList<>(getPlayers())) {
            boolean inTrue = trueCuboid.isIn(player);
            boolean inFalse = falseCuboid.isIn(player);
            boolean inMiddle = middleCuboid.isIn(player);

            boolean correct = false;

            if (answer == null) {
                correct = inTrue || inFalse;
            } else if (answer.equalsIgnoreCase("true")) {
                correct = inTrue;
            } else if (answer.equalsIgnoreCase("false")) {
                correct = inFalse;
            }

            if (inMiddle || (!inTrue && !inFalse)) {
                wrongPlayers.add(player);
                continue;
            }

            if (correct) {
                correctPlayers.add(player);
            } else {
                wrongPlayers.add(player);
            }
        }

        String correctAnswerText;
        if (answer == null) {
            correctAnswerText = config.getString("Messages.Answer free", "SEM RESPOSTA DEFINIDA");
        } else if (answer.equalsIgnoreCase("true")) {
            correctAnswerText = config.getString("Messages.Answer true", "VERDADEIRO");
        } else {
            correctAnswerText = config.getString("Messages.Answer false", "FALSO");
        }

        for (Player player : getPlayers()) {
            sendConfiguredTitle(player, "Title.Answer",
                    "@answer", correctAnswerText,
                    "@name", config.getString("Evento.Title"));
        }

        for (Player player : getSpectators()) {
            sendConfiguredTitle(player, "Title.Answer",
                    "@answer", correctAnswerText,
                    "@name", config.getString("Evento.Title"));
        }

        String correctNames = namesOrPlaceholder(correctPlayers, config.getString("Messages.No one", "Ninguém"));
        String wrongNames = namesOrPlaceholder(wrongPlayers, config.getString("Messages.No one", "Ninguém"));

        for (String message : config.getStringList("Messages.Result correct")) {
            sendToAllRelevant(ColorUtils.colorize(
                    message
                            .replace("@players", correctNames)
                            .replace("@answer", correctAnswerText)
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        for (String message : config.getStringList("Messages.Result wrong")) {
            sendToAllRelevant(ColorUtils.colorize(
                    message
                            .replace("@players", wrongNames)
                            .replace("@answer", correctAnswerText)
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        for (Player player : correctPlayers) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Player correct", "&aVocê acertou!")
                            .replace("@name", config.getString("Evento.Title"))
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        }

        for (Player player : new ArrayList<>(wrongPlayers)) {
            player.sendMessage(ColorUtils.colorize(
                    config.getString("Messages.Player wrong", "&cVocê errou!")
                            .replace("@name", config.getString("Evento.Title"))
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
        }

        int aliveAfterRound = getPlayers().size() - wrongPlayers.size();

        if (aliveAfterRound <= 0) {
            for (Player player : new ArrayList<>(wrongPlayers)) {
                eliminate(player);
            }
            noWinners();
            return;
        }

        if (aliveAfterRound == 1) {
            Player futureWinner = null;

            for (Player player : new ArrayList<>(getPlayers())) {
                if (!wrongPlayers.contains(player)) {
                    futureWinner = player;
                    break;
                }
            }

            for (Player player : new ArrayList<>(wrongPlayers)) {
                eliminate(player);
            }

            if (futureWinner != null) {
                winner(futureWinner);
            } else {
                noWinners();
            }
            return;
        }

        for (Player player : new ArrayList<>(wrongPlayers)) {
            eliminate(player);
        }

        if (!isHappening() || ending) {
            return;
        }

        sendNextQuestionWarning(delay);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            lockedAnswers = false;
            questionHappening = false;
            teleportPlayersToMiddle();
        }, delay * 20L);
    }

    private void sendFirstQuestionWarning(int seconds) {
        for (Player player : getPlayers()) {
            sendConfiguredTitle(player, "Title.First question",
                    "@time", String.valueOf(seconds),
                    "@name", config.getString("Evento.Title"));
        }

        for (Player player : getSpectators()) {
            sendConfiguredTitle(player, "Title.First question",
                    "@time", String.valueOf(seconds),
                    "@name", config.getString("Evento.Title"));
        }

        List<String> messages = config.getStringList("Messages.First question");
        if (messages.isEmpty()) {
            messages = config.getStringList("Messages.Next question");
        }

        for (String message : messages) {
            sendToAllRelevant(ColorUtils.colorize(
                    message
                            .replace("@time", String.valueOf(seconds))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void sendNextQuestionWarning(int seconds) {
        for (Player player : getPlayers()) {
            sendConfiguredTitle(player, "Title.Next round",
                    "@time", String.valueOf(seconds),
                    "@name", config.getString("Evento.Title"));
        }

        for (Player player : getSpectators()) {
            sendConfiguredTitle(player, "Title.Next round",
                    "@time", String.valueOf(seconds),
                    "@name", config.getString("Evento.Title"));
        }

        for (String message : config.getStringList("Messages.Next question")) {
            sendToAllRelevant(ColorUtils.colorize(
                    message
                            .replace("@time", String.valueOf(seconds))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private void teleportPlayersToMiddle() {
        Location target = null;

        boolean middleConfigured =
                config.isSet("Locations.Pos5.world")
                        && config.isSet("Locations.Pos6.world")
                        && config.getString("Locations.Pos5.world") != null
                        && config.getString("Locations.Pos6.world") != null;

        if (middleConfigured) {
            Location center = middleCuboid.getCenter().clone().add(0, 1.0, 0);
            if (center.getWorld() != null) {
                target = center;
            }
        }

        if (target == null) {
            String worldName = config.getString("Locations.Entrance.world");
            if (worldName != null) {
                World world = Bukkit.getWorld(worldName);

                if (world != null) {
                    target = new Location(
                            world,
                            config.getDouble("Locations.Entrance.x"),
                            config.getDouble("Locations.Entrance.y"),
                            config.getDouble("Locations.Entrance.z"),
                            (float) config.getDouble("Locations.Entrance.Yaw"),
                            (float) config.getDouble("Locations.Entrance.Pitch")
                    );
                }
            }
        }

        if (target == null) {
            return;
        }

        for (Player player : getPlayers()) {
            player.teleport(target);
        }
    }

    private void sendToAllRelevant(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(message);
        }
    }

    private String namesOrPlaceholder(List<Player> players, String placeholder) {
        if (players.isEmpty()) {
            return placeholder;
        }

        return players.stream().map(Player::getName).collect(Collectors.joining(", "));
    }

    private void sendConfiguredTitle(Player player, String sectionPath, String... replacements) {
        if (!config.getBoolean("Title.Enabled", true)) {
            return;
        }

        String title = config.getString(sectionPath + ".Title");
        String subtitle = config.getString(sectionPath + ".Subtitle");

        if ((title == null || title.isBlank()) && (subtitle == null || subtitle.isBlank())) {
            if (sectionPath.equalsIgnoreCase("Title.First question")) {
                title = config.getString("Title.Next round.Title");
                subtitle = config.getString("Title.Next round.Subtitle");
            }
        }

        if (title == null && subtitle == null) {
            return;
        }

        if (title == null) title = "";
        if (subtitle == null) subtitle = "";

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            title = title.replace(replacements[i], replacements[i + 1]);
            subtitle = subtitle.replace(replacements[i], replacements[i + 1]);
        }

        player.sendTitle(
                ColorUtils.colorize(title),
                ColorUtils.colorize(subtitle),
                config.getInt("Title.FadeIn", 10),
                config.getInt("Title.Stay", 30),
                config.getInt("Title.FadeOut", 10)
        );
    }

    private void cancelRunnable() {
        if (runnable != null) {
            runnable.cancel();
            runnable = null;
        }
    }

    private void cancelCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    public boolean isLockedAnswers() {
        return lockedAnswers;
    }

    public Cuboid getMiddleCuboid() {
        return middleCuboid;
    }

    public Cuboid getTrueCuboid() {
        return trueCuboid;
    }

    public Cuboid getFalseCuboid() {
        return falseCuboid;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}

package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AutoStarter {

    private BukkitTask task;
    private final Set<String> executedKeys = new HashSet<>();

    public void setup() {

        if (!AbsolutEventsPlugin.getInstance().getConfig().getBoolean("AutoStart.Enabled")) {
            return;
        }

        stop();

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(
                AbsolutEventsPlugin.getInstance(),
                this::check,
                20L,
                20L
        );
    }

    private void check() {

        if (AbsolutEventsPlugin.getInstance().getEventoManager().hasEventoRunning()
                || AbsolutEventsPlugin.getInstance().getEventoChatManager().hasEventoRunning()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int minute = now.getMinute();
        DayOfWeek day = now.getDayOfWeek();

        List<String> times = AbsolutEventsPlugin.getInstance().getConfig().getStringList("AutoStart.Times");

        for (String line : times) {

            String[] split = line.split("-");

            try {

                if (split.length == 3) {
                    DayOfWeek configDay = parseDay(split[0]);

                    if (configDay != day) {
                        continue;
                    }

                    startIfMatch(split[1], split[2], hour, minute, line, day);

                } else if (split.length == 2) {
                    startIfMatch(split[0], split[1], hour, minute, line, day);
                }

            } catch (Exception exception) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao processar AutoStart: " + line);
            }
        }

        clearExpiredExecutions(day, hour, minute);
    }

    private void startIfMatch(String eventNames, String time, int hour, int minute, String line, DayOfWeek day) {

        String[] hm = time.split(":");

        int configHour = Integer.parseInt(hm[0]);
        int configMinute = Integer.parseInt(hm[1]);

        if (hour != configHour || minute != configMinute) {
            return;
        }

        String executionKey = day.name() + ":" + hour + ":" + minute + ":" + line;

        if (executedKeys.contains(executionKey)) {
            return;
        }

        String[] events = eventNames.split(",");
        if (events.length == 0) {
            executedKeys.add(executionKey);
            return;
        }

        String selectedEvent = events[ThreadLocalRandom.current().nextInt(events.length)].trim();

        if (selectedEvent.isEmpty()) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Nenhum evento válido foi encontrado no AutoStart: " + eventNames
            );
            executedKeys.add(executionKey);
            return;
        }

        if (!EventoConfigFile.exists(selectedEvent)) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Evento configurado no AutoStart não existe: " + selectedEvent
            );
            executedKeys.add(executionKey);
            return;
        }

        YamlConfiguration config = EventoConfigFile.get(selectedEvent);
        if (config == null) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Não foi possível carregar o evento do AutoStart: " + selectedEvent
            );
            executedKeys.add(executionKey);
            return;
        }

        EventoType type = EventoType.getEventoType(config.getString("Evento.Type"));

        Bukkit.getScheduler().runTask(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (EventoType.isEventoChat(type)) {
                        AbsolutEventsPlugin.getInstance().getEventoChatManager().startEvento(type, config);
                    } else {
                        AbsolutEventsPlugin.getInstance().getEventoManager().startEvento(type, config);
                    }
                }
        );

        Bukkit.getConsoleSender().sendMessage(
                "§a[AbsolutEvents] Evento sorteado no AutoStart: §f" + selectedEvent + " §7(" + eventNames + ")"
        );

        executedKeys.add(executionKey);
    }

    private void clearExpiredExecutions(DayOfWeek currentDay, int currentHour, int currentMinute) {
        String currentPrefix = currentDay.name() + ":" + currentHour + ":" + currentMinute + ":";
        executedKeys.removeIf(key -> !key.startsWith(currentPrefix));
    }

    private DayOfWeek parseDay(String day) {
        switch (day.toLowerCase()) {
            case "domingo":
            case "sunday":
                return DayOfWeek.SUNDAY;

            case "segunda":
            case "monday":
                return DayOfWeek.MONDAY;

            case "terça":
            case "terca":
            case "tuesday":
                return DayOfWeek.TUESDAY;

            case "quarta":
            case "wednesday":
                return DayOfWeek.WEDNESDAY;

            case "quinta":
            case "thursday":
                return DayOfWeek.THURSDAY;

            case "sexta":
            case "friday":
                return DayOfWeek.FRIDAY;

            case "sábado":
            case "sabado":
            case "saturday":
                return DayOfWeek.SATURDAY;

            default:
                throw new IllegalArgumentException("Dia inválido no AutoStart: " + day);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        executedKeys.clear();
    }
}
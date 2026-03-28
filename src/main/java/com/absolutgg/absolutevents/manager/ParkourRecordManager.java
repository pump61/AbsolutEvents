package com.absolutgg.absolutevents.managers;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

public final class ParkourRecordManager {

    private static ParkourRecordManager instance;

    private final AbsolutEventsPlugin plugin;
    private final File file;
    private final YamlConfiguration config;

    private ParkourRecordManager() {
        this.plugin = AbsolutEventsPlugin.getInstance();
        this.file = new File(plugin.getDataFolder(), "parkour-times.yml");

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Não foi possível criar parkour-times.yml", e);
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public static ParkourRecordManager getInstance() {
        if (instance == null) {
            instance = new ParkourRecordManager();
        }
        return instance;
    }

    public void updateRecord(String eventKey, Player player, long millis) {
        UUID uuid = player.getUniqueId();
        long current = getRecord(eventKey, uuid);

        if (current <= 0L || millis < current) {
            config.set("events." + eventKey + ".players." + uuid + ".name", player.getName());
            config.set("events." + eventKey + ".players." + uuid + ".best", millis);
            save();
        }
    }

    public long getRecord(String eventKey, UUID uuid) {
        return config.getLong("events." + eventKey + ".players." + uuid + ".best", 0L);
    }

    public long getRecord(String eventKey, String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return 0L;
        }

        ConfigurationSection section = config.getConfigurationSection("events." + eventKey + ".players");
        if (section == null) {
            return 0L;
        }

        for (String key : section.getKeys(false)) {
            String savedName = config.getString("events." + eventKey + ".players." + key + ".name", "");
            if (savedName.equalsIgnoreCase(playerName)) {
                return config.getLong("events." + eventKey + ".players." + key + ".best", 0L);
            }
        }

        return 0L;
    }

    public String getFormattedRecord(String eventKey, UUID uuid) {
        return format(getRecord(eventKey, uuid));
    }

    public String getFormattedRecord(String eventKey, String playerName) {
        return format(getRecord(eventKey, playerName));
    }

    public String getFormattedRecord(String eventKey, OfflinePlayer player) {
        if (player == null) {
            return format(0L);
        }
        if (player.getUniqueId() != null) {
            return format(getRecord(eventKey, player.getUniqueId()));
        }
        return format(getRecord(eventKey, player.getName()));
    }

    public List<Entry<String, Long>> getTop(String eventKey) {
        Map<String, Long> map = new HashMap<>();

        ConfigurationSection section = config.getConfigurationSection("events." + eventKey + ".players");
        if (section == null) {
            return new ArrayList<>();
        }

        for (String key : section.getKeys(false)) {
            String name = config.getString("events." + eventKey + ".players." + key + ".name", "");
            long time = config.getLong("events." + eventKey + ".players." + key + ".best", 0L);

            if (!name.isBlank() && time > 0L) {
                map.put(name, time);
            }
        }

        List<Entry<String, Long>> list = new ArrayList<>(map.entrySet());
        list.sort(Entry.comparingByValue());
        return list;
    }

    public String getTopName(String eventKey, int position) {
        List<Entry<String, Long>> top = getTop(eventKey);
        if (position <= 0 || position > top.size()) {
            return "-";
        }
        return top.get(position - 1).getKey();
    }

    public String getTopFormatted(String eventKey, int position) {
        List<Entry<String, Long>> top = getTop(eventKey);
        if (position <= 0 || position > top.size()) {
            return "--:--.--";
        }
        return format(top.get(position - 1).getValue());
    }

    public long getTopRaw(String eventKey, int position) {
        List<Entry<String, Long>> top = getTop(eventKey);
        if (position <= 0 || position > top.size()) {
            return 0L;
        }
        return top.get(position - 1).getValue();
    }

    public String format(long millis) {
        if (millis <= 0L) {
            return "--:--.--";
        }

        long totalSeconds = millis / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        long centiseconds = (millis % 1000L) / 10L;
        return String.format("%02d:%02d.%02d", minutes, seconds, centiseconds);
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
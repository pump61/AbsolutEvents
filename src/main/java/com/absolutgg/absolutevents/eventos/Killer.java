package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.KillerListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.CustomItemResolver;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Killer extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final KillerListener listener = new KillerListener();

    private final HashMap<Player, Integer> kills = new HashMap<>();

    private boolean pvpEnabled;
    private boolean ending;

    private BukkitTask actionbarTask;
    private final Set<UUID> rewardProtected = new HashSet<>();

    private int enablingTime;

    public Killer(YamlConfiguration config) {
        super(config);
        this.config = config;
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        kills.clear();
        rewardProtected.clear();

        pvpEnabled = false;
        ending = false;
        enablingTime = config.getInt("Evento.Time", 0);

        for (Player player : getPlayers()) {
            kills.put(player, 0);
        }

        for (String message : config.getStringList("Messages.Start")) {
            sendToEvent(message);
        }

        if (isKitEnabled()) {
            for (Player player : getPlayers()) {
                applyKit(player);
            }
        }

        startActionbar();
    }

    @Override
    public void stop() {
        cancelTask(actionbarTask);

        for (Player player : new ArrayList<>(getPlayers())) {
            if (!rewardProtected.contains(player.getUniqueId())) {
                player.getInventory().clear();
            }
        }

        HandlerList.unregisterAll(listener);
        removePlayers();

        kills.clear();
        rewardProtected.clear();
        pvpEnabled = false;
        ending = false;
    }

    @Override
    public void winner(Player player) {
        if (ending) return;

        ending = true;
        rewardProtected.add(player.getUniqueId());

        setWinner(player);

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title", "Killer"))
                            .replace("@winner", player.getName())
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title", "Killer"),
                buildTopEntries()
        );

        sendTopKills();
        stopKeepingWinner(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }

            player.updateInventory();
        }, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, rewardProtected::clear, 60L);
    }

    private void stopKeepingWinner(Player winner) {
        cancelTask(actionbarTask);

        for (Player player : new ArrayList<>(getPlayers())) {
            if (!player.getUniqueId().equals(winner.getUniqueId())) {
                player.getInventory().clear();
            }
        }

        HandlerList.unregisterAll(listener);
        removePlayers();
        pvpEnabled = false;
    }

    private void startActionbar() {
        if (!config.getBoolean("Actionbar.Enabled", true)) return;

        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) return;

            if (!pvpEnabled) {
                if (enablingTime <= 0) {
                    pvpEnabled = true;

                    for (String message : config.getStringList("Messages.Enabled")) {
                        sendToEvent(message);
                    }
                    return;
                }

                String raw = config.getString("Actionbar.Enabling", "&ePvP em &f@time");

                String message = ColorUtils.colorize(
                        raw.replace("@time", String.valueOf(enablingTime))
                );

                for (Player player : getPlayers()) {
                    player.sendActionBar(message);
                }

                for (Player player : getSpectators()) {
                    player.sendActionBar(message);
                }

                enablingTime--;
                return;
            }

            String raw = config.getString(
                    "Actionbar.Message",
                    "&aVivos: &f@alive &8| &aKills: &f@kills"
            );

            for (Player player : getPlayers()) {
                String message = ColorUtils.colorize(
                        raw.replace("@alive", String.valueOf(getPlayers().size()))
                                .replace("@kills", String.valueOf(kills.getOrDefault(player, 0)))
                );

                player.sendActionBar(message);
            }

        }, 0L, 20L);
    }

    private void sendTopKills() {
        if (!config.getBoolean("Top kills.Enabled")) return;

        List<Map.Entry<Player, Integer>> top = new ArrayList<>(kills.entrySet());
        top.sort(Comparator.comparingInt((Map.Entry<Player, Integer> e) -> e.getValue()).reversed());

        String t1 = top.size() > 0 ? top.get(0).getKey().getName() : "Ninguém";
        String t2 = top.size() > 1 ? top.get(1).getKey().getName() : "Ninguém";
        String t3 = top.size() > 2 ? top.get(2).getKey().getName() : "Ninguém";

        String k1 = top.size() > 0 ? String.valueOf(top.get(0).getValue()) : "0";
        String k2 = top.size() > 1 ? String.valueOf(top.get(1).getValue()) : "0";
        String k3 = top.size() > 2 ? String.valueOf(top.get(2).getValue()) : "0";

        for (String message : config.getStringList("Top kills.Format")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@top1", t1)
                            .replace("@top2", t2)
                            .replace("@top3", t3)
                            .replace("@kills1", k1)
                            .replace("@kills2", k2)
                            .replace("@kills3", k3)
            ));
        }
    }

    private void sendToEvent(String message) {
        String parsed = ColorUtils.colorize(
                message.replace("@name", config.getString("Evento.Title", "Killer"))
        );

        for (Player player : getPlayers()) {
            player.sendMessage(parsed);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(parsed);
        }
    }

    private boolean isKitEnabled() {
        return config.getBoolean("Itens.Enabled") || config.getBoolean("Kit.Enabled");
    }

    private ConfigurationSection getInventorySection() {
        if (config.isConfigurationSection("Itens.Inventory")) {
            return config.getConfigurationSection("Itens.Inventory");
        }

        if (config.isConfigurationSection("Kit.Inventory")) {
            return config.getConfigurationSection("Kit.Inventory");
        }

        return null;
    }

    private ConfigurationSection getArmorRoot() {
        if (config.isConfigurationSection("Kit.Armor")) {
            return config.getConfigurationSection("Kit.Armor");
        }

        if (config.isConfigurationSection("Itens")) {
            return config.getConfigurationSection("Itens");
        }

        return null;
    }

    private void applyKit(Player player) {
        if (config.isConfigurationSection("Itens")) {
            EventKitApplier.apply(player, config.getConfigurationSection("Itens"));
            return;
        }

        setInventoryLegacy(player);
    }

    private void setInventoryLegacy(Player player) {
        player.getInventory().clear();
        applyArmorLegacy(player);

        ConfigurationSection inv = getInventorySection();
        if (inv != null) {
            for (String key : inv.getKeys(false)) {
                ItemStack item = buildItem(inv.getCurrentPath() + "." + key);
                if (item == null) continue;

                try {
                    int slot = Integer.parseInt(key);
                    player.getInventory().setItem(slot, item);
                } catch (Exception ignored) {
                }
            }
        }

        player.updateInventory();
    }

    private void applyArmorLegacy(Player player) {
        ConfigurationSection root = getArmorRoot();
        if (root == null) return;

        ItemStack helmet = buildItem(root.getCurrentPath() + ".Helmet");
        ItemStack chest = buildItem(root.getCurrentPath() + ".Chestplate");
        ItemStack leg = buildItem(root.getCurrentPath() + ".Leggings");
        ItemStack boots = buildItem(root.getCurrentPath() + ".Boots");

        if (helmet != null) player.getInventory().setHelmet(helmet);
        if (chest != null) player.getInventory().setChestplate(chest);
        if (leg != null) player.getInventory().setLeggings(leg);
        if (boots != null) player.getInventory().setBoots(boots);
    }

    private ItemStack buildItem(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return null;
        }

        ItemStack customResolved = CustomItemResolver.resolve(section);
        if (customResolved != null) {
            return customResolved;
        }

        String materialName = config.getString(path + ".material");
        if (materialName == null) return null;

        Material material = Material.matchMaterial(materialName);
        if (material == null) return null;

        int amount = Math.max(1, config.getInt(path + ".amount", 1));
        ItemStack item = new ItemStack(material, amount);

        ItemMeta meta = item.getItemMeta();
        ConfigurationSection enchants = config.getConfigurationSection(path + ".enchants");

        if (meta != null && enchants != null) {
            for (String enchantName : enchants.getKeys(false)) {
                Enchantment enchant = Enchantment.getByName(enchantName);
                if (enchant != null) {
                    meta.addEnchant(enchant, enchants.getInt(enchantName), true);
                }
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) task.cancel();
    }

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        List<DiscordWebhookManager.TopEntry> entries = new ArrayList<>();
        List<Map.Entry<Player, Integer>> ranking = new ArrayList<>(kills.entrySet());

        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            Map.Entry<Player, Integer> entry = ranking.get(i);
            entries.add(new DiscordWebhookManager.TopEntry(
                    entry.getKey().getName(),
                    String.valueOf(entry.getValue())
            ));
        }

        return entries;
    }

    public boolean isPvPEnabled() {
        return pvpEnabled;
    }

    public boolean isEnding() {
        return ending;
    }

    public HashMap<Player, Integer> getKills() {
        return kills;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
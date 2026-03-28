package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.PaintballListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class Paintball extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final PaintballListener listener = new PaintballListener();

    private final Location blue;
    private final Location red;

    private final List<Player> blueTeam = new ArrayList<>();
    private final List<Player> redTeam = new ArrayList<>();
    private final List<ClanPlayer> simpleClansPlayers = new ArrayList<>();
    private final HashMap<Player, Integer> kills = new HashMap<>();

    private final int time;
    private final String blueName;
    private final String redName;

    private boolean pvpEnabled;
    private boolean teamSelected;
    private boolean ending;

    private BukkitTask enablePvpTask;

    public Paintball(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.blueName = config.getString("Evento.Blue", "Azul");
        this.redName = config.getString("Evento.Red", "Vermelho");
        this.time = config.getInt("Evento.Time");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Paintball: Locations.Pos1.world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Paintball: mundo '" + worldName + "' não está carregado.");
        }

        this.blue = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"),
                (float) config.getDouble("Locations.Pos1.Yaw"),
                (float) config.getDouble("Locations.Pos1.Pitch")
        );

        this.red = new Location(
                world,
                config.getDouble("Locations.Pos2.x"),
                config.getDouble("Locations.Pos2.y"),
                config.getDouble("Locations.Pos2.z"),
                (float) config.getDouble("Locations.Pos2.Yaw"),
                (float) config.getDouble("Locations.Pos2.Pitch")
        );
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        clearTask(enablePvpTask);

        blueTeam.clear();
        redTeam.clear();
        simpleClansPlayers.clear();
        kills.clear();

        pvpEnabled = false;
        teamSelected = false;
        ending = false;

        List<Player> shuffled = new ArrayList<>(getPlayers());
        Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            Player player = shuffled.get(i);
            kills.put(player, 0);

            if (i % 2 == 0) {
                blueTeam.add(player);
            } else {
                redTeam.add(player);
            }
        }

        teamSelected = true;

        List<String> teamMessages = config.getStringList("Messages.Team");
        ItemStack bow = createBow();

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPaintballInventory(player);

            if (blueTeam.contains(player)) {
                sendTeamMessages(player, teamMessages, "§9" + blueName);
                equipColoredArmor(player, Color.BLUE);
                player.teleport(blue);
            } else if (redTeam.contains(player)) {
                sendTeamMessages(player, teamMessages, "§c" + redName);
                equipColoredArmor(player, Color.RED);
                player.teleport(red);
            }

            player.getInventory().setItem(0, bow.clone());
            player.getInventory().setItem(9, new ItemStack(Material.ARROW, 1));
            player.updateInventory();
        }

        setupSimpleClansFriendlyFire();

        enablePvpTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    pvpEnabled = true;

                    List<String> enabledMessages = config.getStringList("Messages.Enabled");

                    for (Player player : getPlayers()) {
                        for (String message : enabledMessages) {
                            player.sendMessage(ColorUtils.colorize(
                                    message.replace("@name", config.getString("Evento.Title"))
                            ));
                        }
                    }

                    for (Player player : getSpectators()) {
                        for (String message : enabledMessages) {
                            player.sendMessage(ColorUtils.colorize(
                                    message.replace("@name", config.getString("Evento.Title"))
                            ));
                        }
                    }
                },
                time * 20L
        );
    }

    public void registerElimination(Player killer, Player victim) {
        if (killer == null || victim == null || killer.equals(victim)) {
            return;
        }

        int newKills = kills.getOrDefault(killer, 0) + 1;
        kills.put(killer, newKills);

        for (String message : config.getStringList("Messages.Kill")) {
            killer.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@victim", victim.getName())
                            .replace("@kills", String.valueOf(newKills))
            ));
        }
    }

    public void win() {
        if (!teamSelected || ending) {
            return;
        }

        ending = true;

        List<Player> winnersNow = new ArrayList<>(getPlayers());
        List<String> winnerNames = new ArrayList<>();

        for (Player player : winnersNow) {
            winnerNames.add(player.getName());
        }

        setWinners(new HashSet<>(winnersNow));
        List<Player> rewardTargets = new ArrayList<>(winnersNow);

        String mvpName = getMvpName();
        String mvpKills = String.valueOf(getMvpKills());

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", String.join(", ", winnerNames))
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@mvp_kills", mvpKills)
                            .replace("@mvp", mvpName)
            ));
        }

        DiscordWebhookManager.sendTeamWinner(
                String.join(", ", winnerNames),
                config.getString("Evento.Title"),
                buildTopEntries()
        );

        stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : rewardTargets) {
                if (!player.isOnline()) {
                    continue;
                }

                for (String command : config.getStringList("Rewards.Commands")) {
                    executeConsoleCommand(player, command.replace("@winner", player.getName()));
                }
            }
        }, 5L);
    }

    @Override
    public void leave(Player player) {
        if (!getPlayers().contains(player)) {
            super.leave(player);
            return;
        }

        sendLeaveMessages(player);
        disableSimpleClansFriendlyFire(player);
        eliminate(player);
    }

    @Override
    public void stop() {
        clearTask(enablePvpTask);

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPaintballInventory(player);
        }

        for (ClanPlayer clanPlayer : simpleClansPlayers) {
            clanPlayer.setFriendlyFire(false);
        }

        simpleClansPlayers.clear();
        blueTeam.clear();
        redTeam.clear();
        kills.clear();
        pvpEnabled = false;
        teamSelected = false;
        ending = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        List<String> eliminatedMessages = config.getStringList("Messages.Eliminated");

        if (blueTeam.contains(player)) {
            blueTeam.remove(player);

            for (Player online : getPlayers()) {
                for (String message : eliminatedMessages) {
                    online.sendMessage(ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@player", "§9" + player.getName())
                                    .replace("@remeaning", "§9" + blueTeam.size())
                                    .replace("@team", "§9" + blueName)
                    ));
                }
            }

            for (Player online : getSpectators()) {
                for (String message : eliminatedMessages) {
                    online.sendMessage(ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@player", "§9" + player.getName())
                                    .replace("@remeaning", "§9" + blueTeam.size())
                                    .replace("@team", "§9" + blueName)
                    ));
                }
            }
        }

        if (redTeam.contains(player)) {
            redTeam.remove(player);

            for (Player online : getPlayers()) {
                for (String message : eliminatedMessages) {
                    online.sendMessage(ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@player", "§c" + player.getName())
                                    .replace("@remeaning", "§c" + redTeam.size())
                                    .replace("@team", "§c" + redName)
                    ));
                }
            }

            for (Player online : getSpectators()) {
                for (String message : eliminatedMessages) {
                    online.sendMessage(ColorUtils.colorize(
                            message
                                    .replace("@name", config.getString("Evento.Title"))
                                    .replace("@player", "§c" + player.getName())
                                    .replace("@remeaning", "§c" + redTeam.size())
                                    .replace("@team", "§c" + redName)
                    ));
                }
            }
        }

        clearPaintballInventory(player);

        PlayerLoseEvent loseEvent = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(loseEvent);

        remove(player);

        if (teamSelected && (blueTeam.isEmpty() || redTeam.isEmpty())) {
            win();
        }
    }

    public List<Player> getBlueTeam() {
        return blueTeam;
    }

    public List<Player> getRedTeam() {
        return redTeam;
    }

    public boolean isPvPEnabled() {
        return pvpEnabled;
    }

    private void sendTeamMessages(Player player, List<String> messages, String teamDisplay) {
        for (String message : messages) {
            player.sendMessage(ColorUtils.colorize(
                    message
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@team", teamDisplay)
                            .replace("@time", String.valueOf(time))
            ));
        }
    }

    private void equipColoredArmor(Player player, Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET, 1);
        LeatherArmorMeta helmetMeta = (LeatherArmorMeta) helmet.getItemMeta();
        if (helmetMeta != null) {
            helmetMeta.setColor(color);
            helmet.setItemMeta(helmetMeta);
        }

        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE, 1);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        if (chestMeta != null) {
            chestMeta.setColor(color);
            chestplate.setItemMeta(chestMeta);
        }

        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS, 1);
        LeatherArmorMeta leggingsMeta = (LeatherArmorMeta) leggings.getItemMeta();
        if (leggingsMeta != null) {
            leggingsMeta.setColor(color);
            leggings.setItemMeta(leggingsMeta);
        }

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS, 1);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        if (bootsMeta != null) {
            bootsMeta.setColor(color);
            boots.setItemMeta(bootsMeta);
        }

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
    }

    private void clearPaintballInventory(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private ItemStack createBow() {
        ItemStack bow = new ItemStack(Material.BOW, 1);
        ItemMeta bowMeta = bow.getItemMeta();

        if (bowMeta != null) {
            bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
            bowMeta.addEnchant(Enchantment.UNBREAKING, 5, true);
            bowMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            bow.setItemMeta(bowMeta);
        }

        return bow;
    }

    private void setupSimpleClansFriendlyFire() {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        for (Player player : getPlayers()) {
            ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);

            if (clanPlayer != null) {
                simpleClansPlayers.add(clanPlayer);
                clanPlayer.setFriendlyFire(true);
            }
        }
    }

    private void disableSimpleClansFriendlyFire(Player player) {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
        simpleClansPlayers.remove(clanPlayer);

        if (clanPlayer != null) {
            clanPlayer.setFriendlyFire(false);
        }
    }

    private void sendLeaveMessages(Player player) {
        for (Player online : getPlayers()) {
            online.sendMessage(ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            ));
        }

        for (Player online : getSpectators()) {
            online.sendMessage(ColorUtils.colorize(
                    plugin.getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            ));
        }
    }

    private String getMvpName() {
        Player best = null;
        int bestKills = -1;

        for (var entry : kills.entrySet()) {
            if (entry.getValue() > bestKills) {
                best = entry.getKey();
                bestKills = entry.getValue();
            }
        }

        return best != null ? best.getName() : "Ninguém";
    }

    private int getMvpKills() {
        int bestKills = 0;

        for (var entry : kills.entrySet()) {
            if (entry.getValue() > bestKills) {
                bestKills = entry.getValue();
            }
        }

        return bestKills;
    }

    private List<DiscordWebhookManager.TopEntry> buildTopEntries() {
        List<DiscordWebhookManager.TopEntry> entries = new ArrayList<>();
        List<HashMap.Entry<Player, Integer>> ranking = new ArrayList<>(kills.entrySet());

        ranking.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            HashMap.Entry<Player, Integer> entry = ranking.get(i);
            entries.add(new DiscordWebhookManager.TopEntry(
                    entry.getKey().getName(),
                    String.valueOf(entry.getValue())
            ));
        }

        return entries;
    }

    private void clearTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
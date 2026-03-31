package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.MorteSubitaListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.EventKitApplier;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.iridium.iridiumcolorapi.IridiumColorAPI;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class MorteSubita extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();
    private final YamlConfiguration config;
    private final MorteSubitaListener listener = new MorteSubitaListener();

    private final Location blueSpawn;
    private final Location redSpawn;
    private final String blueName;
    private final String redName;

    private final List<Player> blueTeam = new ArrayList<>();
    private final List<Player> redTeam = new ArrayList<>();
    private final List<ClanPlayer> simpleClansClans = new ArrayList<>();

    private final int startTime;
    private final double heartsAdded;

    private BukkitTask enablePvpTask;

    private boolean pvpEnabled;
    private boolean teamsAssigned;
    private boolean ending;

    private static final ItemStack SWORD = new ItemStack(Material.STONE_SWORD);
    private static final ItemStack FOOD = new ItemStack(XMaterial.COOKED_BEEF.parseMaterial(), 32);

    static {
        ItemMeta swordMeta = SWORD.getItemMeta();
        if (swordMeta != null && XEnchantment.UNBREAKING.getEnchant() != null) {
            swordMeta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            swordMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            SWORD.setItemMeta(swordMeta);
        }
    }

    public MorteSubita(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.blueName = config.getString("Evento.Blue", "Time Azul");
        this.redName = config.getString("Evento.Red", "Time Vermelho");
        this.startTime = config.getInt("Evento.Enable PvP");
        this.heartsAdded = config.getDouble("Evento.Hearts added");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("MorteSubita: Locations.Pos1.world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("MorteSubita: mundo '" + worldName + "' não está carregado.");
        }

        this.blueSpawn = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"),
                (float) config.getDouble("Locations.Pos1.Yaw"),
                (float) config.getDouble("Locations.Pos1.Pitch")
        );

        this.redSpawn = new Location(
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

        this.pvpEnabled = false;
        this.teamsAssigned = false;
        this.ending = false;

        blueTeam.clear();
        redTeam.clear();
        simpleClansClans.clear();

        assignTeams();
        giveItemsAndTeleport();
        enableFriendlyFireIfNeeded();
        schedulePvpEnable();
    }

    private void assignTeams() {
        List<Player> shuffled = new ArrayList<>(getPlayers());
        Collections.shuffle(shuffled);

        for (int i = 0; i < shuffled.size(); i++) {
            Player player = shuffled.get(i);
            if (i % 2 == 0) {
                blueTeam.add(player);
            } else {
                redTeam.add(player);
            }
        }

        teamsAssigned = true;
    }

    private void giveItemsAndTeleport() {
        for (Player player : getPlayers()) {
            setGear(player);

            if (blueTeam.contains(player)) {
                sendTeamMessage(player, "§9" + blueName);
                player.teleport(blueSpawn);
            } else {
                sendTeamMessage(player, "§c" + redName);
                player.teleport(redSpawn);
            }
        }
    }

    private void sendTeamMessage(Player player, String teamName) {
        for (String message : config.getStringList("Messages.Team")) {
            player.sendMessage(IridiumColorAPI.process(
                    message.replace("&", "§")
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@team", teamName)
                            .replace("@time", String.valueOf(startTime))
            ));
        }
    }

    private void enableFriendlyFireIfNeeded() {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        for (Player player : getPlayers()) {
            ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
            if (clanPlayer != null) {
                simpleClansClans.add(clanPlayer);
                clanPlayer.setFriendlyFire(true);
            }
        }
    }

    private void schedulePvpEnable() {
        enablePvpTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!isHappening() || ending) {
                return;
            }

            pvpEnabled = true;

            for (String message : config.getStringList("Messages.Enabled")) {
                sendToEvent(message.replace("&", "§")
                        .replace("@name", config.getString("Evento.Title")));
            }
        }, startTime * 20L);
    }

    @Override
    public void leave(Player player) {
        if (!getPlayers().contains(player)) {
            super.leave(player);
            return;
        }

        String leaveMessage = IridiumColorAPI.process(
                plugin.getConfig()
                        .getString("Messages.Leave", "&c@player saiu do evento.")
                        .replace("&", "§")
                        .replace("@player", player.getName())
        );

        for (Player online : getPlayers()) {
            online.sendMessage(leaveMessage);
        }

        for (Player online : getSpectators()) {
            online.sendMessage(leaveMessage);
        }

        disableFriendlyFire(player);
        blueTeam.remove(player);
        redTeam.remove(player);
        clearPlayer(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        super.remove(player);
        checkWinCondition();
    }

    @Override
    public void stop() {
        if (enablePvpTask != null) {
            enablePvpTask.cancel();
            enablePvpTask = null;
        }

        for (ClanPlayer clanPlayer : simpleClansClans) {
            clanPlayer.setFriendlyFire(false);
        }

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayer(player);
        }

        simpleClansClans.clear();
        blueTeam.clear();
        redTeam.clear();

        pvpEnabled = false;
        teamsAssigned = false;
        ending = false;

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public void eliminate(Player victim, Player killer) {
        if (!isHappening() || ending) {
            return;
        }

        blueTeam.remove(victim);
        redTeam.remove(victim);

        clearPlayer(victim);
        victim.setHealth(victim.getMaxHealth());
        victim.setFoodLevel(20);

        String killerName = killer != null ? killer.getName() : "Desconhecido";

        for (String message : config.getStringList("Messages.Eliminado")) {
            victim.sendMessage(IridiumColorAPI.process(
                    message.replace("&", "§")
                            .replace("@name", config.getString("Evento.Title"))
                            .replace("@killer", killerName)
            ));
        }

        PlayerLoseEvent lose = new PlayerLoseEvent(
                victim,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        super.remove(victim);
        checkWinCondition();
    }

    private void checkWinCondition() {
        if (!teamsAssigned || ending) {
            return;
        }

        if (blueTeam.isEmpty() && redTeam.isEmpty()) {
            return;
        }

        if (blueTeam.isEmpty()) {
            win(redName, new ArrayList<>(redTeam));
            return;
        }

        if (redTeam.isEmpty()) {
            win(blueName, new ArrayList<>(blueTeam));
        }
    }

    public void win(String teamName, List<Player> winners) {
        if (!teamsAssigned || ending) {
            return;
        }

        ending = true;

        List<String> winnerNames = new ArrayList<>();
        for (Player player : winners) {
            winnerNames.add(player.getName());
        }

        for (String message : config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(IridiumColorAPI.process(
                    message.replace("&", "§")
                            .replace("@team", teamName)
                            .replace("@winner", String.join(", ", winnerNames))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendTeamWinner(teamName, config.getString("Evento.Title"), List.of());

        setWinners(new HashSet<>(winners));

        // ✅ TOURNAMENT STATS
        for (Player player : winners) {
            TournamentStatsManager.getInstance().addWin(player.getUniqueId());
        }

        List<Player> rewardTargets = new ArrayList<>(winners);

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

    private void setGear(Player player) {
        player.getInventory().clear();
        player.getInventory().setItemInOffHand(null);

        ConfigurationSection itensSection = config.getConfigurationSection("Itens");
        boolean customItemsEnabled = itensSection != null && config.getBoolean("Itens.Enabled", false);

        if (customItemsEnabled) {
            EventKitApplier.apply(player, itensSection);
            return;
        }

        player.getInventory().setItem(0, SWORD.clone());
        player.getInventory().setItem(1, FOOD.clone());

        Color color = redTeam.contains(player) ? Color.RED : Color.BLUE;

        ItemStack helmet = coloredArmor(Material.LEATHER_HELMET, color);
        ItemStack chestplate = coloredArmor(Material.LEATHER_CHESTPLATE, color);
        ItemStack leggings = coloredArmor(Material.LEATHER_LEGGINGS, color);
        ItemStack boots = coloredArmor(Material.LEATHER_BOOTS, color);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
        player.updateInventory();
    }

    private ItemStack coloredArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material, 1);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();

        if (meta != null) {
            meta.setColor(color);

            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                meta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 100, true);
            }

            if (XEnchantment.PROTECTION.getEnchant() != null) {
                meta.addEnchant(XEnchantment.PROTECTION.getEnchant(), 1, true);
            }

            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }

        return item;
    }

    private void clearPlayer(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void disableFriendlyFire(Player player) {
        if (plugin.getSimpleClans() == null) {
            return;
        }

        ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
        simpleClansClans.remove(clanPlayer);

        if (clanPlayer != null) {
            clanPlayer.setFriendlyFire(false);
        }
    }

    private void sendToEvent(String message) {
        String parsed = IridiumColorAPI.process(message);

        for (Player player : getPlayers()) {
            player.sendMessage(parsed);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(parsed);
        }
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public List<Player> getBlueTeam() {
        return blueTeam;
    }

    public List<Player> getRedTeam() {
        return redTeam;
    }

    public double getHeartsAdded() {
        return heartsAdded;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
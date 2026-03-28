package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.listeners.eventos.CorridaArmadaListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XMaterial;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public final class CorridaArmada extends Evento {

    private final YamlConfiguration config;
    private final CorridaArmadaListener listener = new CorridaArmadaListener();

    private int leaderLevel = 0;
    private final int startTime;
    private final int killedWaitTime;
    private final int invincibilityTime;
    private final int hearts;

    private boolean pvpEnabled;

    private final HashMap<Player, Integer> playerLevel = new HashMap<>();
    private final List<Location> respawnLocations = new ArrayList<>();
    private final List<Player> deadPlayers = new ArrayList<>();
    private final List<Player> invinciblePlayers = new ArrayList<>();
    private final Random random = new Random();

    private BukkitTask actionbarTask;

    public CorridaArmada(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.pvpEnabled = false;
        this.startTime = config.getInt("Evento.Time");
        this.killedWaitTime = config.getInt("Evento.Dead time");
        this.invincibilityTime = config.getInt("Evento.Invincibility");
        this.hearts = config.getInt("Evento.Hearts added");

        World world = findArenaWorld();
        if (world == null) {
            throw new IllegalStateException("CorridaArmada: nenhum mundo válido foi encontrado em Locations.Pos1/Pos2/Pos3/Pos4/Entrance/Lobby.");
        }

        addRespawn(world, "Pos1");
        addRespawn(world, "Pos2");
        addRespawn(world, "Pos3");
        addRespawn(world, "Pos4");
    }

    private World findArenaWorld() {
        String[] paths = {
                "Locations.Pos1.world",
                "Locations.Pos2.world",
                "Locations.Pos3.world",
                "Locations.Pos4.world",
                "Locations.Entrance.world",
                "Locations.Lobby.world"
        };

        for (String path : paths) {
            String worldName = config.getString(path);
            if (worldName == null || worldName.isBlank()) {
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                return world;
            }
        }

        return null;
    }

    @Override
    public void start() {
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(listener, AbsolutEventsPlugin.getInstance());
        listener.setEvento();

        List<Player> shuffled = new ArrayList<>(getPlayers());
        Collections.shuffle(shuffled);

        for (Player player : shuffled) {
            resetPlayerState(player);
            setArmor(player);

            if (!respawnLocations.isEmpty()) {
                player.teleport(respawnLocations.get(random.nextInt(respawnLocations.size())), PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            playerLevel.put(player, 1);
            updateGearByLevel(player, 1);
        }

        startActionbar();

        for (String message : config.getStringList("Messages.Enabling")) {
            broadcastToEvent(ColorUtils.colorize(
                    message.replace("@time", String.valueOf(startTime))
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    this.pvpEnabled = true;

                    for (String message : config.getStringList("Messages.Enabled")) {
                        broadcastToEvent(ColorUtils.colorize(
                                message.replace("@name", config.getString("Evento.Title"))
                        ));
                    }
                },
                startTime * 20L
        );
    }

    @Override
    public void leave(Player player) {
        if (getPlayers().contains(player)) {
            String leaveMessage = ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance().getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            );

            for (Player online : getPlayers()) {
                online.sendMessage(leaveMessage);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(leaveMessage);
            }
        }

        playerLevel.remove(player);
        deadPlayers.remove(player);
        invinciblePlayers.remove(player);
        clearPlayer(player);

        PlayerLoseEvent lose = new PlayerLoseEvent(
                player,
                config.getString("filename", "").replace(".yml", ""),
                getType()
        );
        Bukkit.getPluginManager().callEvent(lose);

        this.remove(player);

        if (!isHappening()) {
            return;
        }

        if (getPlayers().isEmpty()) {
            stop();
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    @Override
    public void winner(Player player) {
        for (String message : config.getStringList("Messages.Winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(
                    ColorUtils.colorize(
                            message.replace("@winner", player.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        this.setWinner(player);
        this.stop();

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }
    }

    @Override
    public void stop() {
        cancelTask(actionbarTask);

        for (Player player : new ArrayList<>(getPlayers())) {
            clearPlayer(player);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            resetPlayerState(player);
        }

        playerLevel.clear();
        pvpEnabled = false;
        leaderLevel = 0;
        deadPlayers.clear();
        invinciblePlayers.clear();

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    public void eliminate(Player victim, Player killer) {
        if (!isHappening() || !getPlayers().contains(victim)) {
            return;
        }

        deadPlayers.add(victim);

        if (killer != null && getPlayers().contains(killer) && playerLevel.containsKey(killer)) {
            int killerLevel = playerLevel.get(killer) + 1;
            playerLevel.put(killer, killerLevel);

            if (killerLevel >= 15) {
                winner(killer);
                return;
            }

            double newHealth = Math.min(killer.getMaxHealth(), killer.getHealth() + hearts);
            killer.setHealth(newHealth);
            killer.setFoodLevel(20);
            killer.setSaturation(20.0f);

            for (String message : config.getStringList("Messages.Next level")) {
                killer.sendMessage(
                        ColorUtils.colorize(
                                message.replace("@name", config.getString("Evento.Title"))
                                        .replace("@level", String.valueOf(killerLevel))
                        )
                );
            }

            updateGearByLevel(killer, killerLevel);

            if (killerLevel > leaderLevel) {
                leaderLevel = killerLevel;

                for (String message : config.getStringList("Messages.Leader")) {
                    broadcastToEvent(
                            ColorUtils.colorize(
                                    message.replace("@name", config.getString("Evento.Title"))
                                            .replace("@player", killer.getName())
                                            .replace("@level", String.valueOf(killerLevel))
                            )
                    );
                }
            }
        }

        clearPlayer(victim);
        resetPlayerState(victim);

        if (!respawnLocations.isEmpty()) {
            victim.teleport(respawnLocations.get(random.nextInt(respawnLocations.size())), PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 500000, 10));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 500000, 1));

        ItemStack helmet = XMaterial.CARVED_PUMPKIN.parseItem();
        ItemStack chestplate = XMaterial.LEATHER_CHESTPLATE.parseItem();
        ItemStack leggings = XMaterial.LEATHER_LEGGINGS.parseItem();
        ItemStack boots = XMaterial.LEATHER_BOOTS.parseItem();

        if (helmet != null) {
            victim.getInventory().setHelmet(helmet);
        }

        paintBlack(chestplate);
        paintBlack(leggings);
        paintBlack(boots);

        victim.getInventory().setChestplate(chestplate);
        victim.getInventory().setLeggings(leggings);
        victim.getInventory().setBoots(boots);
        victim.updateInventory();

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || !getPlayers().contains(victim)) {
                        return;
                    }

                    deadPlayers.remove(victim);

                    Integer level = playerLevel.get(victim);
                    if (level == null) {
                        level = 1;
                        playerLevel.put(victim, level);
                    }

                    resetPlayerState(victim);
                    setArmor(victim);
                    updateGearByLevel(victim, level);

                    victim.removePotionEffect(PotionEffectType.BLINDNESS);
                    victim.removePotionEffect(PotionEffectType.SLOWNESS);

                    invinciblePlayers.add(victim);

                    Bukkit.getScheduler().runTaskLater(
                            AbsolutEventsPlugin.getInstance(),
                            () -> invinciblePlayers.remove(victim),
                            invincibilityTime * 20L
                    );
                },
                killedWaitTime * 20L
        );
    }

    public void updateGearByLevel(Player player, int level) {
        if (!isHappening() || player == null) {
            return;
        }

        player.getInventory().setItem(0, null);
        player.getInventory().setItem(1, null);

        if (level == 14) {
            player.updateInventory();
            return;
        }

        if (level >= 15) {
            player.updateInventory();
            return;
        }

        ItemStack weapon = null;

        switch (level) {
            case 1:
                weapon = XMaterial.NETHERITE_SWORD.parseItem();
                break;
            case 2:
                weapon = XMaterial.NETHERITE_AXE.parseItem();
                break;
            case 3:
                weapon = XMaterial.DIAMOND_SWORD.parseItem();
                break;
            case 4:
                weapon = XMaterial.DIAMOND_AXE.parseItem();
                break;
            case 5:
                weapon = XMaterial.GOLDEN_SWORD.parseItem();
                break;
            case 6:
                weapon = XMaterial.GOLDEN_AXE.parseItem();
                break;
            case 7:
                weapon = XMaterial.IRON_SWORD.parseItem();
                break;
            case 8:
                weapon = XMaterial.IRON_AXE.parseItem();
                break;
            case 9:
                weapon = XMaterial.BOW.parseItem();
                if (weapon != null) {
                    ItemMeta bowMeta = weapon.getItemMeta();
                    if (bowMeta != null) {
                        bowMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                        bowMeta.addEnchant(Enchantment.UNBREAKING, 1000, true);
                        bowMeta.addEnchant(Enchantment.INFINITY, 1, true);
                        weapon.setItemMeta(bowMeta);
                    }
                    player.getInventory().setItem(0, weapon);
                    player.getInventory().setItem(1, XMaterial.ARROW.parseItem());
                }
                player.updateInventory();
                return;
            case 10:
                weapon = XMaterial.STONE_SWORD.parseItem();
                break;
            case 11:
                weapon = XMaterial.STONE_AXE.parseItem();
                break;
            case 12:
                weapon = XMaterial.WOODEN_SWORD.parseItem();
                break;
            case 13:
                weapon = XMaterial.WOODEN_AXE.parseItem();
                break;
            default:
                break;
        }

        if (weapon == null) {
            player.updateInventory();
            return;
        }

        ItemMeta itemMeta = weapon.getItemMeta();
        if (itemMeta != null) {
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            itemMeta.addEnchant(Enchantment.UNBREAKING, 1000, true);
            weapon.setItemMeta(itemMeta);
        }

        player.getInventory().setItem(0, weapon);
        player.updateInventory();
    }

    public void setArmor(Player player) {
        if (!isHappening()) {
            return;
        }

        ItemStack helmet = XMaterial.IRON_HELMET.parseItem();
        ItemStack chestplate = XMaterial.IRON_CHESTPLATE.parseItem();
        ItemStack leggings = XMaterial.IRON_LEGGINGS.parseItem();
        ItemStack boots = XMaterial.IRON_BOOTS.parseItem();

        applyUnbreaking(helmet);
        applyUnbreaking(chestplate);
        applyUnbreaking(leggings);
        applyUnbreaking(boots);

        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);
        player.getInventory().setLeggings(leggings);
        player.getInventory().setBoots(boots);
        player.updateInventory();
    }

    private void addRespawn(World world, String pos) {
        if (!config.contains("Locations." + pos + ".x")) {
            return;
        }

        float yaw = config.contains("Locations." + pos + ".yaw")
                ? (float) config.getDouble("Locations." + pos + ".yaw")
                : (float) config.getDouble("Locations." + pos + ".Yaw");

        float pitch = config.contains("Locations." + pos + ".pitch")
                ? (float) config.getDouble("Locations." + pos + ".pitch")
                : (float) config.getDouble("Locations." + pos + ".Pitch");

        respawnLocations.add(new Location(
                world,
                config.getDouble("Locations." + pos + ".x"),
                config.getDouble("Locations." + pos + ".y"),
                config.getDouble("Locations." + pos + ".z"),
                yaw,
                pitch
        ));
    }

    private void broadcastToEvent(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(message);
        }
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

    private void paintBlack(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof LeatherArmorMeta leatherArmorMeta)) {
            return;
        }

        leatherArmorMeta.setColor(Color.BLACK);
        item.setItemMeta(leatherArmorMeta);
    }

    private void applyUnbreaking(ItemStack item) {
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.addEnchant(Enchantment.UNBREAKING, 1000, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private void startActionbar() {
        cancelTask(actionbarTask);

        if (!config.getBoolean("Actionbar.Enabled", true)) {
            return;
        }

        LegacyComponentSerializer serializer = LegacyComponentSerializer.builder()
                .character('§')
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();

        actionbarTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        cancelTask(actionbarTask);
                        return;
                    }

                    Player leader = getLeaderPlayer();
                    String leaderName = leader != null ? leader.getName() : "Ninguém";
                    int leaderLevelValue = leader != null ? playerLevel.getOrDefault(leader, 0) : 0;

                    String raw = config.getString(
                            "Actionbar.Message",
                            "&#FDD500Seu nível: &f@level &#FDD500| Líder: &f@leader &#FDD500| Posição: &f@position"
                    );

                    for (Player player : getPlayers()) {
                        String parsed = raw
                                .replace("@level", String.valueOf(playerLevel.getOrDefault(player, 1)))
                                .replace("@leader", leaderName)
                                .replace("@leader_level", String.valueOf(leaderLevelValue))
                                .replace("@position", String.valueOf(getPlayerPosition(player)));

                        parsed = ColorUtils.colorize(parsed);

                        Component component = serializer.deserialize(parsed);
                        player.sendActionBar(component);
                    }

                    for (Player player : getSpectators()) {
                        String parsed = raw
                                .replace("@level", "0")
                                .replace("@leader", leaderName)
                                .replace("@leader_level", String.valueOf(leaderLevelValue))
                                .replace("@position", "-");

                        parsed = ColorUtils.colorize(parsed);

                        Component component = serializer.deserialize(parsed);
                        player.sendActionBar(component);
                    }
                },
                0L,
                20L
        );
    }

    private Player getLeaderPlayer() {
        Player best = null;
        int bestLevel = -1;

        for (Player player : getPlayers()) {
            int level = playerLevel.getOrDefault(player, 0);
            if (level > bestLevel) {
                bestLevel = level;
                best = player;
            }
        }

        return best;
    }

    private int getPlayerPosition(Player target) {
        List<Player> ranking = new ArrayList<>(getPlayers());
        ranking.sort(Comparator
                .comparingInt((Player p) -> playerLevel.getOrDefault(p, 0))
                .reversed()
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < ranking.size(); i++) {
            if (ranking.get(i).getUniqueId().equals(target.getUniqueId())) {
                return i + 1;
            }
        }

        return ranking.size();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private void resetPlayerState(Player player) {
        player.setFireTicks(0);
        player.setVisualFire(false);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setNoDamageTicks(20);
        player.setFreezeTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public int getHearts() {
        return hearts;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public HashMap<Player, Integer> getPlayerLevel() {
        return playerLevel;
    }

    public List<Location> getRespawnLocations() {
        return respawnLocations;
    }

    public List<Player> getDeadPlayers() {
        return deadPlayers;
    }

    public List<Player> getInvinciblePlayers() {
        return invinciblePlayers;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
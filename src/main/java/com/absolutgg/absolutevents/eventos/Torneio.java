package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.TorneioListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.CustomItemResolver;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.attribute.Attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class Torneio extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private BukkitTask monitorTask;
    private BukkitTask fightMonitorTask;
    private BukkitTask maxTimeTask;

    private final YamlConfiguration config;
    private final TorneioListener listener = new TorneioListener();
    private final String eventTitle;

    private final List<ClanPlayer> scClans = new ArrayList<>();
    private List<Player> brackets;
    private final List<PlayerPair> playerPairs = new ArrayList<>();

    private Player fighter1;
    private Player fighter2;

    private final Location fighter1Loc;
    private final Location fighter2Loc;
    private final Location entrance;

    private boolean roundHappening = false;
    private boolean fightHappening = false;
    private boolean lastFight = false;
    private boolean firstRoundFight = true;

    private final int startTime;
    private final int interval;
    private final int maxTime;

    public Torneio(YamlConfiguration config) {
        super(config);
        this.config = config;

        this.eventTitle = config.getString("Evento.Title", "Torneio");
        this.startTime = config.getInt("Evento.Start time");
        this.interval = config.getInt("Evento.Time");
        this.maxTime = config.getInt("Evento.Fight time");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Torneio: Locations.Pos1.world não encontrado na config.");
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Torneio: mundo '" + worldName + "' não está carregado.");
        }

        this.entrance = new Location(
                world,
                config.getDouble("Locations.Entrance.x"),
                config.getDouble("Locations.Entrance.y"),
                config.getDouble("Locations.Entrance.z"),
                (float) config.getDouble("Locations.Entrance.Yaw"),
                (float) config.getDouble("Locations.Entrance.Pitch")
        );

        this.fighter1Loc = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"),
                (float) config.getDouble("Locations.Pos1.Yaw"),
                (float) config.getDouble("Locations.Pos1.Pitch")
        );

        this.fighter2Loc = new Location(
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

        this.fightHappening = false;

        if (plugin.getSimpleClans() != null) {
            for (Player player : getPlayers()) {
                ClanPlayer clanPlayer = plugin.getSimpleClans().getClanManager().getClanPlayer(player);
                if (clanPlayer != null) {
                    scClans.add(clanPlayer);
                    clanPlayer.setFriendlyFire(true);
                }
            }
        }

        monitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                cancelTask(monitorTask);
                return;
            }

            if (roundHappening) {
                return;
            }

            fightRound();
        }, 20L, 20L);
    }

    private void fightRound() {
        playerPairs.clear();
        roundHappening = true;
        firstRoundFight = true;

        this.brackets = new ArrayList<>(getPlayers());
        Collections.shuffle(this.brackets);

        if (brackets.size() <= 1) {
            roundHappening = false;
            if (brackets.size() == 1) {
                winner(brackets.get(0));
            }
            return;
        }

        lastFight = getPlayers().size() == 2;
        if (lastFight) {
            playerPairs.add(new PlayerPair(brackets.get(0), brackets.get(1)));

            for (String message : config.getStringList("Messages.Last fight")) {
                sendToEvent(
                        ColorUtils.colorize(
                                message
                                        .replace("@name", eventTitle)
                                        .replace("@fighter1", brackets.get(0).getName())
                                        .replace("@fighter2", brackets.get(1).getName())
                        )
                );
            }

            Bukkit.getScheduler().runTaskLater(plugin, this::fight, 10 * 20L);
            return;
        }

        if (brackets.size() % 2 == 1) {
            Player luckyOne = brackets.remove((int) (Math.random() * brackets.size()));

            for (String message : config.getStringList("Messages.Odd number")) {
                sendToEvent(
                        ColorUtils.colorize(
                                message
                                        .replace("@name", eventTitle)
                                        .replace("@player", luckyOne.getName())
                        )
                );
            }
        }

        for (int i = 0; i < brackets.size(); i += 2) {
            playerPairs.add(new PlayerPair(brackets.get(i), brackets.get(i + 1)));
        }

        for (String message : config.getStringList("Messages.Starting fights")) {
            sendToEvent(
                    ColorUtils.colorize(
                            message
                                    .replace("@time", String.valueOf(startTime))
                                    .replace("@name", eventTitle)
                    )
            );
        }

        for (PlayerPair pair : playerPairs) {
            for (String message : config.getStringList("Messages.Bracket info")) {
                sendToEvent(
                        ColorUtils.colorize(
                                message
                                        .replace("@name", eventTitle)
                                        .replace("@first", pair.first().getName())
                                        .replace("@second", pair.second().getName())
                        )
                );
            }
        }

        fightMonitorTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isHappening()) {
                cancelTask(fightMonitorTask);
                return;
            }

            if (fightHappening) {
                return;
            }

            normalizePairs();

            if (!roundHappening) {
                cancelTask(fightMonitorTask);
                return;
            }

            fight();
        }, startTime * 20L, 20L);
    }

    private void fight() {
        normalizePairs();

        if (playerPairs.isEmpty()) {
            cancelTask(fightMonitorTask);
            fightHappening = false;
            roundHappening = false;

            if (getPlayers().size() == 1) {
                winner(getPlayers().get(0));
            }

            return;
        }

        fightHappening = true;
        PlayerPair fighters = playerPairs.get(0);

        if (fighters.first() != null && fighters.second() != null) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isHappening()) {
                    return;
                }

                if (!getPlayers().contains(fighters.first()) || !getPlayers().contains(fighters.second())) {
                    fightHappening = false;
                    normalizePairs();
                    return;
                }

                fighter1 = fighters.first();
                fighter2 = fighters.second();

                clearInventoryAndArmor(fighter1);
                clearInventoryAndArmor(fighter2);
                giveEquipment();

                if (fighter1.getAttribute(Attribute.MAX_HEALTH) != null) {
                    fighter1.setHealth(fighter1.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
                fighter1.setFoodLevel(20);
                fighter1.teleport(fighter1Loc, PlayerTeleportEvent.TeleportCause.PLUGIN);

                if (fighter2.getAttribute(Attribute.MAX_HEALTH) != null) {
                    fighter2.setHealth(fighter2.getAttribute(Attribute.MAX_HEALTH).getValue());
                }
                fighter2.setFoodLevel(20);
                fighter2.teleport(fighter2Loc, PlayerTeleportEvent.TeleportCause.PLUGIN);

                maxTimeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!isHappening() || !fightHappening) {
                        return;
                    }

                    playerPairs.remove(fighters);

                    if (fighter1 != null) {
                        remove(fighter1);
                    }
                    if (fighter2 != null) {
                        remove(fighter2);
                    }

                    for (String message : config.getStringList("Messages.Draw")) {
                        sendToEvent(
                                ColorUtils.colorize(
                                        message
                                                .replace("@name", eventTitle)
                                                .replace("@maxtime", String.valueOf(maxTime))
                                )
                        );
                    }

                    fighter1 = null;
                    fighter2 = null;
                    fightHappening = false;
                    normalizePairs();
                }, maxTime * 20L);
            }, firstRoundFight ? 0L : interval * 20L);

            firstRoundFight = false;
            return;
        }

        List<String> messages;

        if (fighters.first() == null && fighters.second() == null) {
            messages = config.getStringList("Messages.Fight cancelled");
            for (String message : messages) {
                sendToEvent(
                        ColorUtils.colorize(
                                message
                                        .replace("@name", eventTitle)
                                        .replace("@first", "desconhecido")
                                        .replace("@second", "desconhecido")
                        )
                );
            }
        } else if (fighters.first() == null) {
            messages = config.getStringList("Messages.Players leaves");
            for (String message : messages) {
                sendToEvent(
                        ColorUtils.colorize(
                                message
                                        .replace("@name", eventTitle)
                                        .replace("@player", "desconhecido")
                                        .replace("@adversary", fighters.second().getName())
                        )
                );
            }
        } else {
            messages = config.getStringList("Messages.Players leaves");
            for (String message : messages) {
                sendToEvent(
                        ColorUtils.colorize(
                                message
                                        .replace("@name", eventTitle)
                                        .replace("@player", "desconhecido")
                                        .replace("@adversary", fighters.first().getName())
                        )
                );
            }
        }

        playerPairs.remove(0);
        fightHappening = false;
        normalizePairs();

        if (firstRoundFight) {
            firstRoundFight = false;
        }
    }

    public void fightWinner(Player winner) {
        if (winner == null) {
            return;
        }

        winner.getInventory().clear();
        clearArmor(winner);
        winner.teleport(entrance, PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (winner.getAttribute(Attribute.MAX_HEALTH) != null) {
            winner.setHealth(winner.getAttribute(Attribute.MAX_HEALTH).getValue());
        }
        winner.setFoodLevel(20);

        cancelTask(maxTimeTask);

        Iterator<PlayerPair> iterator = playerPairs.iterator();
        while (iterator.hasNext()) {
            PlayerPair pair = iterator.next();
            if (!pair.contains(winner)) {
                continue;
            }

            Player adversary = pair.other(winner);

            if (adversary == null) {
                for (String message : config.getStringList("Messages.Fighter leaves the fight")) {
                    sendToEvent(
                            ColorUtils.colorize(
                                    message
                                            .replace("@name", eventTitle)
                                            .replace("@player", winner.getName())
                            )
                    );
                }

                iterator.remove();
                fighter1 = null;
                fighter2 = null;
                fightHappening = false;
                normalizePairs();
                return;
            }

            this.remove(adversary);
            iterator.remove();
            break;
        }

        for (String message : config.getStringList("Messages.Fight winner")) {
            sendToEvent(
                    ColorUtils.colorize(
                            message
                                    .replace("@name", eventTitle)
                                    .replace("@player", winner.getName())
                    )
            );
        }

        fighter1 = null;
        fighter2 = null;
        fightHappening = false;
        normalizePairs();

        if (lastFight) {
            winner(winner);
        }
    }

    @Override
    public void winner(Player player) {
        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(
                    ColorUtils.colorize(
                            message
                                    .replace("@winner", player.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        this.setWinner(player);
        this.stop();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (String command : config.getStringList("Rewards.Commands")) {
                executeConsoleCommand(player, command.replace("@winner", player.getName()));
            }
        }, 2L);
    }

    @Override
    public void remove(Player player) {
        super.remove(player);
        clearArmor(player);
    }

    @Override
    public void stop() {
        cancelTask(monitorTask);
        cancelTask(fightMonitorTask);
        cancelTask(maxTimeTask);

        for (Player player : new ArrayList<>(getPlayers())) {
            clearArmor(player);
            player.getInventory().clear();
        }

        for (ClanPlayer clanPlayer : scClans) {
            clanPlayer.setFriendlyFire(false);
        }

        scClans.clear();

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    @Override
    public void leave(Player player) {
        boolean wasInPlayers = getPlayers().contains(player);

        if (wasInPlayers) {
            disableFriendlyFire(player);

            player.getInventory().clear();
            clearArmor(player);

            for (PlayerPair pair : new ArrayList<>(playerPairs)) {
                if (pair.remove(player)) {
                    if (fighter1 == player) {
                        fightWinner(fighter2);
                    } else if (fighter2 == player) {
                        fightWinner(fighter1);
                    }
                    break;
                }
            }
        }

        super.leave(player);

        if (isHappening()) {
            normalizePairs();

            if (!isOpen() && getPlayers().size() == 1) {
                winner(getPlayers().get(0));
            }
        }
    }

    private void giveEquipment() {
        String base = lastFight ? "Kit.Last fight" : "Kit.Normal";

        if (config.getConfigurationSection(base + ".Inventory") != null) {
            for (String item : config.getConfigurationSection(base + ".Inventory").getKeys(false)) {
                ConfigurationSection itemSection = config.getConfigurationSection(base + ".Inventory." + item);
                if (itemSection == null) {
                    continue;
                }

                ItemStack resolved = CustomItemResolver.resolve(itemSection);
                if (resolved == null) {
                    continue;
                }

                int slot;
                try {
                    slot = Integer.parseInt(item);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                fighter1.getInventory().setItem(slot, resolved.clone());
                fighter2.getInventory().setItem(slot, resolved.clone());
            }
        }

        equipArmorPiece(base + ".Armor.Helmet", item -> {
            fighter1.getInventory().setHelmet(item.clone());
            fighter2.getInventory().setHelmet(item.clone());
        });

        equipArmorPiece(base + ".Armor.Chestplate", item -> {
            fighter1.getInventory().setChestplate(item.clone());
            fighter2.getInventory().setChestplate(item.clone());
        });

        equipArmorPiece(base + ".Armor.Leggings", item -> {
            fighter1.getInventory().setLeggings(item.clone());
            fighter2.getInventory().setLeggings(item.clone());
        });

        equipArmorPiece(base + ".Armor.Boots", item -> {
            fighter1.getInventory().setBoots(item.clone());
            fighter2.getInventory().setBoots(item.clone());
        });

        equipArmorPiece(base + ".Armor.Offhand", item -> {
            fighter1.getInventory().setItemInOffHand(item.clone());
            fighter2.getInventory().setItemInOffHand(item.clone());
        });

        fighter1.updateInventory();
        fighter2.updateInventory();
    }

    private void equipArmorPiece(String path, java.util.function.Consumer<ItemStack> consumer) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return;
        }

        ItemStack resolved = CustomItemResolver.resolve(section);
        if (resolved == null) {
            return;
        }

        consumer.accept(resolved);
    }

    private void normalizePairs() {
        Iterator<PlayerPair> iterator = playerPairs.iterator();

        while (iterator.hasNext()) {
            PlayerPair pair = iterator.next();

            Player first = pair.first();
            Player second = pair.second();

            if (first != null && !getPlayers().contains(first)) {
                pair.remove(first);
                first = null;
            }

            if (second != null && !getPlayers().contains(second)) {
                pair.remove(second);
                second = null;
            }

            if (pair.first() == null && pair.second() == null) {
                iterator.remove();
                continue;
            }

            if (pair.first() == null || pair.second() == null) {
                Player survivor = pair.first() != null ? pair.first() : pair.second();

                if (survivor != null && getPlayers().contains(survivor)) {
                    for (String message : config.getStringList("Messages.Players leaves")) {
                        sendToEvent(
                                ColorUtils.colorize(
                                        message
                                                .replace("@name", eventTitle)
                                                .replace("@player", "desconhecido")
                                                .replace("@adversary", survivor.getName())
                                )
                        );
                    }
                }

                iterator.remove();
            }
        }

        if (playerPairs.isEmpty()) {
            fightHappening = false;
            roundHappening = false;
            cancelTask(fightMonitorTask);
        }
    }

    private void sendToEvent(String message) {
        for (Player player : getPlayers()) {
            player.sendMessage(message);
        }

        for (Player player : getSpectators()) {
            player.sendMessage(message);
        }
    }

    private void clearInventoryAndArmor(Player player) {
        player.getInventory().clear();
        clearArmor(player);
        player.updateInventory();
    }

    private void clearArmor(Player player) {
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
        scClans.remove(clanPlayer);

        if (clanPlayer != null) {
            clanPlayer.setFriendlyFire(false);
        }
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public void cancelMaxTimeTask() {
        cancelTask(maxTimeTask);
    }

    public Player getFighter1() {
        return fighter1;
    }

    public Player getFighter2() {
        return fighter2;
    }

    public Location getEntrance() {
        return entrance;
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }

    private static final class PlayerPair {
        private Player first;
        private Player second;

        private PlayerPair(Player first, Player second) {
            this.first = first;
            this.second = second;
        }

        public Player first() {
            return first;
        }

        public Player second() {
            return second;
        }

        public boolean contains(Player player) {
            return first == player || second == player;
        }

        public Player other(Player player) {
            if (first == player) return second;
            if (second == player) return first;
            return null;
        }

        public boolean remove(Player player) {
            if (first == player) {
                first = null;
                return true;
            }

            if (second == player) {
                second = null;
                return true;
            }

            return false;
        }
    }
}
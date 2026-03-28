package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.FightListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XItemStack;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Fight extends Evento {

    private final YamlConfiguration config;
    private final FightListener listener = new FightListener();
    private final Random random = new Random();

    private Player fighter1;
    private Player fighter2;

    private final int interval;
    private final int maxTime;

    private int task;
    private boolean fightHappening = false;

    private final Location entrance;
    private final Location fight1;
    private final Location fight2;

    private BukkitTask maxTimeTask;
    private BukkitTask delayedFightTask;

    private final ArrayList<ClanPlayer> simpleClansClans = new ArrayList<>();

    public Fight(YamlConfiguration config) {
        super(config);

        this.config = config;
        this.interval = config.getInt("Evento.Time");
        this.maxTime = config.getInt("Evento.Fight time");

        String worldName = config.getString("Locations.Pos1.world");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalStateException("Fight: Locations.Pos1.world não encontrado na config.");
        }

        World world = AbsolutEventsPlugin.getInstance().getServer().getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Fight: mundo '" + worldName + "' não está carregado.");
        }

        entrance = new Location(
                world,
                config.getDouble("Locations.Entrance.x"),
                config.getDouble("Locations.Entrance.y"),
                config.getDouble("Locations.Entrance.z"),
                (float) config.getDouble("Locations.Entrance.Yaw"),
                (float) config.getDouble("Locations.Entrance.Pitch")
        );

        fight1 = new Location(
                world,
                config.getDouble("Locations.Pos1.x"),
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos1.z"),
                (float) config.getDouble("Locations.Pos1.Yaw"),
                (float) config.getDouble("Locations.Pos1.Pitch")
        );

        fight2 = new Location(
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
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(listener, AbsolutEventsPlugin.getInstance());
        listener.setEvento();

        if (AbsolutEventsPlugin.getInstance().getSimpleClans() != null) {
            for (Player p : getPlayers()) {
                ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                        .getSimpleClans()
                        .getClanManager()
                        .getClanPlayer(p);

                if (clanPlayer != null) {
                    simpleClansClans.add(clanPlayer);
                    clanPlayer.setFriendlyFire(true);
                }
            }
        }

        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        Bukkit.getScheduler().cancelTask(task);
                        return;
                    }

                    if (fightHappening) {
                        return;
                    }

                    fight();
                },
                20L,
                20L
        );
    }

    @Override
    public void winner(Player p) {
        List<String> broadcastMessages = config.getStringList("Messages.Winner");
        for (String s : broadcastMessages) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(
                    ColorUtils.colorize(
                            s.replace("@winner", p.getName())
                                    .replace("@name", config.getString("Evento.Title"))
                    )
            );
        }

        DiscordWebhookManager.sendPlayerWinner(
                p.getName(),
                config.getString("Evento.Title")
        );

        this.setWinner(p);
        this.stop();

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    List<String> commands = config.getStringList("Rewards.Commands");
                    for (String s : commands) {
                        executeConsoleCommand(p, s.replace("@winner", p.getName()));
                    }
                },
                2L
        );
    }

    @Override
    public void stop() {
        cancelTask(maxTimeTask);
        cancelTask(delayedFightTask);

        for (Player p : new ArrayList<>(getPlayers())) {
            clearFightInventory(p);
        }

        for (ClanPlayer p : simpleClansClans) {
            p.setFriendlyFire(false);
        }

        simpleClansClans.clear();

        fighter1 = null;
        fighter2 = null;
        fightHappening = false;

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    @Override
    public void leave(Player p) {
        if (getPlayers().contains(p)) {
            String leaveMessage = ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance().getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", p.getName())
            );

            for (Player player : getPlayers()) {
                player.sendMessage(leaveMessage);
            }

            for (Player player : getSpectators()) {
                player.sendMessage(leaveMessage);
            }

            if (AbsolutEventsPlugin.getInstance().getSimpleClans() != null) {
                ClanPlayer clanPlayer = AbsolutEventsPlugin.getInstance()
                        .getSimpleClans()
                        .getClanManager()
                        .getClanPlayer(p);

                simpleClansClans.remove(clanPlayer);

                if (clanPlayer != null) {
                    clanPlayer.setFriendlyFire(false);
                }
            }

            PlayerLoseEvent lose = new PlayerLoseEvent(
                    p,
                    config.getString("filename", "").replace(".yml", ""),
                    getType()
            );
            Bukkit.getPluginManager().callEvent(lose);

            boolean wasFighter = fighter1 == p || fighter2 == p;

            if (wasFighter) {
                setFightLoser(p);
            } else {
                this.remove(p);

                if (fightHappening && (fighter1 == null || fighter2 == null)) {
                    fightHappening = false;
                }

                if (getPlayers().size() == 1) {
                    winner(getPlayers().get(0));
                }
            }

        } else {
            this.remove(p);
        }
    }

    private void fight() {
        if (!isHappening()) {
            return;
        }

        if (getPlayers().size() < 2) {
            if (getPlayers().size() == 1) {
                winner(getPlayers().get(0));
            }
            return;
        }

        this.fightHappening = true;

        List<String> nextMessages = config.getStringList("Messages.Starting fights");
        for (Player player : getPlayers()) {
            for (String s : nextMessages) {
                player.sendMessage(ColorUtils.colorize(
                        s.replace("@time", String.valueOf(interval))
                                .replace("@name", config.getString("Evento.Title"))
                ));
            }
        }

        for (Player player : getSpectators()) {
            for (String s : nextMessages) {
                player.sendMessage(ColorUtils.colorize(
                        s.replace("@time", String.valueOf(interval))
                                .replace("@name", config.getString("Evento.Title"))
                ));
            }
        }

        delayedFightTask = Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening()) {
                        fightHappening = false;
                        return;
                    }

                    if (getPlayers().size() < 2) {
                        fightHappening = false;
                        if (getPlayers().size() == 1) {
                            winner(getPlayers().get(0));
                        }
                        return;
                    }

                    fighter1 = getPlayers().get(random.nextInt(getPlayers().size()));
                    fighter2 = getPlayers().get(random.nextInt(getPlayers().size()));

                    while (fighter2 == fighter1) {
                        fighter2 = getPlayers().get(random.nextInt(getPlayers().size()));
                    }

                    Player currentFighter1 = fighter1;
                    Player currentFighter2 = fighter2;

                    List<String> fightMessages = config.getStringList("Messages.Bracket info");
                    for (Player player : getPlayers()) {
                        for (String s : fightMessages) {
                            player.sendMessage(ColorUtils.colorize(
                                    s.replace("@first", fighter1.getName())
                                            .replace("@second", fighter2.getName())
                                            .replace("@name", config.getString("Evento.Title"))
                            ));
                        }
                    }

                    for (Player player : getSpectators()) {
                        for (String s : fightMessages) {
                            player.sendMessage(ColorUtils.colorize(
                                    s.replace("@first", fighter1.getName())
                                            .replace("@second", fighter2.getName())
                                            .replace("@name", config.getString("Evento.Title"))
                            ));
                        }
                    }

                    clearFightInventory(fighter1);
                    clearFightInventory(fighter2);

                    if (getPlayers().size() == 2) {
                        applyKit("Kit.Last fight", fighter1, fighter2);
                    } else {
                        applyKit("Kit.Normal", fighter1, fighter2);
                    }

                    fighter1.setHealth(fighter1.getMaxHealth());
                    fighter1.setFoodLevel(20);
                    fighter1.teleport(fight1, PlayerTeleportEvent.TeleportCause.PLUGIN);

                    fighter2.setHealth(fighter2.getMaxHealth());
                    fighter2.setFoodLevel(20);
                    fighter2.teleport(fight2, PlayerTeleportEvent.TeleportCause.PLUGIN);

                    maxTimeTask = Bukkit.getScheduler().runTaskLater(
                            AbsolutEventsPlugin.getInstance(),
                            () -> {
                                if (!isHappening()) {
                                    return;
                                }

                                if (!getPlayers().contains(currentFighter1) || !getPlayers().contains(currentFighter2)) {
                                    fightHappening = false;
                                    return;
                                }

                                if (fighter1 != currentFighter1 || fighter2 != currentFighter2) {
                                    fightHappening = false;
                                    return;
                                }

                                setFightLoser(null);
                            },
                            maxTime * 20L
                    );
                },
                interval * 20L
        );
    }

    public void setFightLoser(Player p) {
        cancelTask(maxTimeTask);

        if (fighter1 == null || fighter2 == null) {
            fightHappening = false;
            fighter1 = null;
            fighter2 = null;
            return;
        }

        if (p == null) {
            List<String> noWinnerMessages = config.getStringList("Messages.Draw");
            for (Player player : getPlayers()) {
                for (String s : noWinnerMessages) {
                    player.sendMessage(ColorUtils.colorize(
                            s.replace("@name", config.getString("Evento.Title"))
                                    .replace("@maxtime", String.valueOf(maxTime))
                    ));
                }
            }

            for (Player player : getSpectators()) {
                for (String s : noWinnerMessages) {
                    player.sendMessage(ColorUtils.colorize(
                            s.replace("@name", config.getString("Evento.Title"))
                                    .replace("@maxtime", String.valueOf(maxTime))
                    ));
                }
            }

            clearFightInventory(fighter1);
            clearFightInventory(fighter2);

            fighter1.setHealth(fighter1.getMaxHealth());
            fighter1.setFoodLevel(20);
            fighter1.teleport(entrance, PlayerTeleportEvent.TeleportCause.PLUGIN);

            fighter2.setHealth(fighter2.getMaxHealth());
            fighter2.setFoodLevel(20);
            fighter2.teleport(entrance, PlayerTeleportEvent.TeleportCause.PLUGIN);

        } else {
            if (p != fighter1 && p != fighter2) {
                return;
            }

            clearFightInventory(fighter1);
            clearFightInventory(fighter2);

            List<String> winnerMessages = config.getStringList("Messages.Fight winner");

            if (p == fighter1) {
                for (Player player : getPlayers()) {
                    for (String s : winnerMessages) {
                        player.sendMessage(ColorUtils.colorize(
                                s.replace("@player", fighter2.getName())
                                        .replace("@winner", fighter2.getName())
                                        .replace("@name", config.getString("Evento.Title"))
                        ));
                    }
                }

                for (Player player : getSpectators()) {
                    for (String s : winnerMessages) {
                        player.sendMessage(ColorUtils.colorize(
                                s.replace("@player", fighter2.getName())
                                        .replace("@winner", fighter2.getName())
                                        .replace("@name", config.getString("Evento.Title"))
                        ));
                    }
                }

                fighter2.setHealth(fighter2.getMaxHealth());
                fighter2.setFoodLevel(20);
                fighter2.teleport(entrance, PlayerTeleportEvent.TeleportCause.PLUGIN);

            } else {
                for (Player player : getPlayers()) {
                    for (String s : winnerMessages) {
                        player.sendMessage(ColorUtils.colorize(
                                s.replace("@player", fighter1.getName())
                                        .replace("@winner", fighter1.getName())
                                        .replace("@name", config.getString("Evento.Title"))
                        ));
                    }
                }

                for (Player player : getSpectators()) {
                    for (String s : winnerMessages) {
                        player.sendMessage(ColorUtils.colorize(
                                s.replace("@player", fighter1.getName())
                                        .replace("@winner", fighter1.getName())
                                        .replace("@name", config.getString("Evento.Title"))
                        ));
                    }
                }

                fighter1.setHealth(fighter1.getMaxHealth());
                fighter1.setFoodLevel(20);
                fighter1.teleport(entrance, PlayerTeleportEvent.TeleportCause.PLUGIN);
            }

            p.sendMessage(ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance()
                            .getConfig()
                            .getString("Messages.Eliminated", "&cVocê foi eliminado.")
            ));

            remove(p);

            PlayerLoseEvent lose = new PlayerLoseEvent(
                    p,
                    config.getString("filename", "").replace(".yml", ""),
                    getType()
            );
            Bukkit.getPluginManager().callEvent(lose);
        }

        fighter1 = null;
        fighter2 = null;
        this.fightHappening = false;

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    private void applyKit(String basePath, Player first, Player second) {
        if (config.getConfigurationSection(basePath + ".Inventory") != null) {
            for (String item : config.getConfigurationSection(basePath + ".Inventory").getKeys(false)) {
                first.getInventory().setItem(
                        Integer.parseInt(item),
                        XItemStack.deserialize(config.getConfigurationSection(basePath + ".Inventory." + item))
                );
                second.getInventory().setItem(
                        Integer.parseInt(item),
                        XItemStack.deserialize(config.getConfigurationSection(basePath + ".Inventory." + item))
                );
            }
        }

        if (config.getConfigurationSection(basePath + ".Armor.Helmet") != null) {
            first.getInventory().setHelmet(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Helmet")));
            second.getInventory().setHelmet(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Helmet")));
        }

        if (config.getConfigurationSection(basePath + ".Armor.Chestplate") != null) {
            first.getInventory().setChestplate(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Chestplate")));
            second.getInventory().setChestplate(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Chestplate")));
        }

        if (config.getConfigurationSection(basePath + ".Armor.Leggings") != null) {
            first.getInventory().setLeggings(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Leggings")));
            second.getInventory().setLeggings(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Leggings")));
        }

        if (config.getConfigurationSection(basePath + ".Armor.Boots") != null) {
            first.getInventory().setBoots(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Boots")));
            second.getInventory().setBoots(XItemStack.deserialize(config.getConfigurationSection(basePath + ".Armor.Boots")));
        }

        first.updateInventory();
        second.updateInventory();
    }

    private void clearFightInventory(Player p) {
        p.getInventory().setHelmet(null);
        p.getInventory().setChestplate(null);
        p.getInventory().setLeggings(null);
        p.getInventory().setBoots(null);
        p.getInventory().clear();
        p.updateInventory();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public Player getFighter1() {
        return this.fighter1;
    }

    public Player getFighter2() {
        return this.fighter2;
    }
}
package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.BatataQuenteListener;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Random;

public final class BatataQuente extends Evento {

    private final AbsolutEventsPlugin plugin = AbsolutEventsPlugin.getInstance();

    private final YamlConfiguration config;
    private final BatataQuenteListener listener = new BatataQuenteListener();
    private final Random random = new Random();

    private final int maxTime;

    private int potatoHolderChanges = 0;
    private Player potatoHolder;

    private BukkitTask countdownTask;
    private BukkitTask explodeTask;

    public BatataQuente(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.maxTime = config.getInt("Evento.Time");
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listener.setEvento();

        for (Player player : new ArrayList<>(getPlayers())) {
            resetPlayerState(player);
            clearPlayerInventory(player);
            syncPlayer(player);
        }

        randomHolder();
    }

    @Override
    public void winner(Player player) {
        if (player == null) {
            return;
        }

        for (String message : this.config.getStringList("Messages.Winner")) {
            plugin.getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title", "Batata Quente"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title", "Batata Quente")
        );

        this.setWinner(player);
        this.stop();

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    for (String command : this.config.getStringList("Rewards.Commands")) {
                        executeConsoleCommand(player, command.replace("@winner", player.getName()));
                    }

                    player.updateInventory();
                },
                20L
        );
    }

    @Override
    public void stop() {
        cancelTask(countdownTask);
        cancelTask(explodeTask);

        for (Player player : new ArrayList<>(getPlayers())) {
            resetPlayerState(player);
            clearPlayerInventory(player);
            syncPlayer(player);
        }

        for (Player player : new ArrayList<>(getSpectators())) {
            resetPlayerState(player);
            syncPlayer(player);
        }

        potatoHolder = null;
        potatoHolderChanges = 0;

        HandlerList.unregisterAll(listener);
        this.removePlayers();
    }

    @Override
    public void leave(Player player) {
        boolean wasInEvent = getPlayers().contains(player);

        if (wasInEvent) {
            String leaveMessage = ColorUtils.colorize(
                    AbsolutEventsPlugin.getInstance().getConfig()
                            .getString("Messages.Leave", "&c@player saiu do evento.")
                            .replace("@player", player.getName())
            );

            for (Player online : new ArrayList<>(getPlayers())) {
                online.sendMessage(leaveMessage);
            }

            for (Player online : new ArrayList<>(getSpectators())) {
                online.sendMessage(leaveMessage);
            }
        }

        boolean wasHolder = potatoHolder != null && potatoHolder.getUniqueId().equals(player.getUniqueId());

        clearPlayerInventory(player);
        resetPlayerState(player);
        syncPlayer(player);

        if (wasHolder) {
            potatoHolder = null;
            potatoHolderChanges++;
            cancelTask(countdownTask);
            cancelTask(explodeTask);
        }

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
            return;
        }

        if (wasHolder) {
            randomHolder();
        }
    }

    private void randomHolder() {
        if (!isHappening()) {
            return;
        }

        if (getPlayers().isEmpty()) {
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
            return;
        }

        Player selected = getPlayers().get(random.nextInt(getPlayers().size()));
        setHolder(selected, potatoHolderChanges);
    }

    public void setHolder(Player player, int currentPotatoHolderChanges) {
        if (!isHappening()) {
            return;
        }

        if (player == null || !player.isOnline() || !getPlayers().contains(player)) {
            return;
        }

        cancelTask(countdownTask);
        cancelTask(explodeTask);

        Player previousHolder = this.potatoHolder;

        if (previousHolder != null
                && previousHolder.isOnline()
                && !previousHolder.getUniqueId().equals(player.getUniqueId())) {
            clearPlayerInventory(previousHolder);
            resetPlayerState(previousHolder);
            syncPlayer(previousHolder);
        }

        this.potatoHolder = player;
        this.potatoHolderChanges++;

        resetPlayerState(this.potatoHolder);
        clearPlayerInventory(this.potatoHolder);

        this.potatoHolder.getInventory().setHelmet(new ItemStack(Material.TNT, 1));

        ItemStack potato = XMaterial.POTATO.parseItem();
        if (potato != null) {
            for (int i = 0; i < 9; i++) {
                this.potatoHolder.getInventory().setItem(i, potato.clone());
            }
        }

        syncPlayer(this.potatoHolder);

        Location location = this.potatoHolder.getLocation();
        Firework firework = this.potatoHolder.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffects(FireworkEffect.builder()
                .withColor(Color.RED)
                .with(FireworkEffect.Type.BALL)
                .build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);

        for (Player online : new ArrayList<>(getPlayers())) {
            for (String message : config.getStringList("Messages.Potato")) {
                online.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@player", this.potatoHolder.getName())
                                .replace("@name", this.config.getString("Evento.Title", "Batata Quente"))
                ));
            }
        }

        for (Player online : new ArrayList<>(getSpectators())) {
            for (String message : config.getStringList("Messages.Potato")) {
                online.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@player", this.potatoHolder.getName())
                                .replace("@name", this.config.getString("Evento.Title", "Batata Quente"))
                ));
            }
        }

        this.potatoHolder.sendMessage(ColorUtils.colorize(
                config.getString("Messages.Potato holder", "&cVocê está com a batata por @time segundos!")
                        .replace("@time", String.valueOf(maxTime))
                        .replace("@name", this.config.getString("Evento.Title", "Batata Quente"))
        ));

        final int holderState = this.potatoHolderChanges;

        countdownTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                new Runnable() {
                    int run = 5;

                    @Override
                    public void run() {
                        if (!isHappening()
                                || potatoHolder == null
                                || potatoHolderChanges != holderState
                                || run <= 0) {
                            cancelTask(countdownTask);
                            return;
                        }

                        potatoHolder.sendMessage(ColorUtils.colorize(
                                config.getString("Messages.Countdown", "&cA batata explode em @time...")
                                        .replace("@time", String.valueOf(run))
                                        .replace("@name", config.getString("Evento.Title", "Batata Quente"))
                        ));

                        run--;
                    }
                },
                Math.max(0L, (maxTime - 5) * 20L),
                20L
        );

        explodeTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!isHappening()) {
                        return;
                    }

                    if (!getPlayers().contains(player)) {
                        return;
                    }

                    if (potatoHolderChanges != holderState) {
                        return;
                    }

                    if (getPotatoHolder() == null) {
                        return;
                    }

                    potatoHolder.sendMessage(ColorUtils.colorize(
                            AbsolutEventsPlugin.getInstance()
                                    .getConfig()
                                    .getString("Messages.Eliminated", "&cVocê foi eliminado.")
                    ));

                    Player eliminated = potatoHolder;

                    clearPlayerInventory(eliminated);
                    resetPlayerState(eliminated);
                    syncPlayer(eliminated);

                    remove(eliminated);

                    PlayerLoseEvent lose = new PlayerLoseEvent(
                            eliminated,
                            config.getString("filename", "").replace(".yml", ""),
                            getType()
                    );
                    Bukkit.getPluginManager().callEvent(lose);

                    potatoHolder = null;

                    if (getPlayers().size() == 1) {
                        winner(getPlayers().get(0));
                    } else if (getPlayers().size() > 1) {
                        randomHolder();
                    } else {
                        stop();
                    }
                },
                maxTime * 20L
        );
    }

    private void clearPlayerInventory(Player player) {
        if (player == null) {
            return;
        }

        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
        player.updateInventory();
    }

    private void resetPlayerState(Player player) {
        if (player == null) {
            return;
        }

        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.GLOWING);

        player.setFreezeTicks(0);
        player.setWalkSpeed(0.2F);
        player.setFlySpeed(0.1F);

        if (!player.getAllowFlight()) {
            player.setFlying(false);
        }
    }

    private void syncPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.updateInventory();
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    public Player getPotatoHolder() {
        return potatoHolder;
    }

    public int getPotatoHolderChanges() {
        return potatoHolderChanges;
    }

    public YamlConfiguration getConfig() {
        return config;
    }
}
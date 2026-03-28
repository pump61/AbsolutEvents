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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public final class BatataQuente extends Evento {

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
        AbsolutEventsPlugin.getInstance().getServer().getPluginManager().registerEvents(listener, AbsolutEventsPlugin.getInstance());
        listener.setEvento();

        for (Player player : getPlayers()) {
            resetPlayerState(player);
            clearPlayerInventory(player);
            resyncPlayer(player);
        }

        randomHolder();
    }

    @Override
    public void winner(Player player) {
        for (String message : this.config.getStringList("Messages.Winner")) {
            AbsolutEventsPlugin.getInstance().getServer().broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(
                player.getName(),
                config.getString("Evento.Title")
        );

        this.setWinner(player);

        this.stop();

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
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

        for (Player player : getPlayers()) {
            resetPlayerState(player);
            clearPlayerInventory(player);
            resyncPlayer(player);
        }

        for (Player player : getSpectators()) {
            resetPlayerState(player);
            resyncPlayer(player);
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

            for (Player online : getPlayers()) {
                online.sendMessage(leaveMessage);
            }

            for (Player online : getSpectators()) {
                online.sendMessage(leaveMessage);
            }
        }

        boolean wasHolder = (potatoHolder == player);

        clearPlayerInventory(player);
        resetPlayerState(player);
        resyncPlayer(player);

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

        if (getPlayers().size() <= 0) {
            stop();
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
            return;
        }

        if (wasHolder && getPlayers().size() > 1) {
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

        if (player == null || !getPlayers().contains(player)) {
            return;
        }

        cancelTask(countdownTask);
        cancelTask(explodeTask);

        if (potatoHolder != null && potatoHolder != player) {
            clearPlayerInventory(potatoHolder);
            resetPlayerState(potatoHolder);
            resyncPlayer(potatoHolder);
        }

        potatoHolder = player;
        potatoHolderChanges++;

        resetPlayerState(potatoHolder);
        clearPlayerInventory(potatoHolder);

        potatoHolder.getInventory().setHelmet(new ItemStack(Material.TNT, 1));
        ItemStack potato = XMaterial.POTATO.parseItem();
        if (potato != null) {
            for (int i = 0; i < 9; i++) {
                potatoHolder.getInventory().setItem(i, potato.clone());
            }
        }
        potatoHolder.updateInventory();

        resyncPlayer(potatoHolder);

        Location location = potatoHolder.getLocation();
        Firework firework = potatoHolder.getWorld().spawn(location, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffects(FireworkEffect.builder()
                .withColor(Color.RED)
                .with(FireworkEffect.Type.BALL)
                .build());
        meta.setPower(2);
        firework.setFireworkMeta(meta);

        for (Player online : getPlayers()) {
            for (String message : config.getStringList("Messages.Potato")) {
                online.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@player", potatoHolder.getName())
                                .replace("@name", this.config.getString("Evento.Title"))
                ));
            }
        }

        for (Player online : getSpectators()) {
            for (String message : config.getStringList("Messages.Potato")) {
                online.sendMessage(ColorUtils.colorize(
                        message
                                .replace("@player", potatoHolder.getName())
                                .replace("@name", this.config.getString("Evento.Title"))
                ));
            }
        }

        potatoHolder.sendMessage(ColorUtils.colorize(
                config.getString("Messages.Potato holder", "&cVocê está com a batata por @time segundos!")
                        .replace("@time", String.valueOf(maxTime))
                        .replace("@name", this.config.getString("Evento.Title"))
        ));

        int holderState = potatoHolderChanges;

        countdownTask = Bukkit.getScheduler().runTaskTimer(
                AbsolutEventsPlugin.getInstance(),
                new Runnable() {
                    int run = 5;

                    @Override
                    public void run() {
                        if (!isHappening() || potatoHolder == null || potatoHolderChanges != holderState || run <= 0) {
                            cancelTask(countdownTask);
                            return;
                        }

                        potatoHolder.sendMessage(ColorUtils.colorize(
                                config.getString("Messages.Potato explode", "&cA batata explode em @time...")
                                        .replace("@time", String.valueOf(run))
                                        .replace("@name", config.getString("Evento.Title"))
                        ));

                        run--;
                    }
                },
                Math.max(0L, (maxTime - 5) * 20L),
                20L
        );

        explodeTask = Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
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
                    resyncPlayer(eliminated);

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

    private void resyncPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.teleport(player.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (player.isOnline()) {
                        player.teleport(player.getLocation(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                },
                1L
        );
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
}
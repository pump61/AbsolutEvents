package com.absolutgg.absolutevents.listeners.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.eventos.Sign;
import com.absolutgg.absolutevents.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SignListener implements Listener {

    private final Map<UUID, Location> checkpoints = new HashMap<>();
    private final Map<UUID, Location> lastActivatedCheckpoint = new HashMap<>();
    private final Map<UUID, Long> damageCooldown = new HashMap<>();

    private Sign evento;
    private BukkitTask particleTask;

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        Player player = event.getPlayer();

        if (!signEvent.getPlayers().contains(player)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Location blockLocation = event.getTo().getBlock().getLocation();

        if (isFinish(blockLocation, signEvent)) {
            signEvent.winner(player);
            return;
        }

        Location matchedCheckpoint = getMatchedCheckpoint(blockLocation, signEvent);
        if (matchedCheckpoint == null) {
            return;
        }

        Location lastCheckpoint = lastActivatedCheckpoint.get(player.getUniqueId());
        if (lastCheckpoint != null && sameBlock(lastCheckpoint, matchedCheckpoint)) {
            return;
        }

        checkpoints.put(player.getUniqueId(), matchedCheckpoint.clone());
        lastActivatedCheckpoint.put(player.getUniqueId(), matchedCheckpoint.clone());

        player.sendMessage(ColorUtils.colorize(
                signEvent.getConfig()
                        .getString("Messages.Checkpoint saved", "&aCheckpoint salvo!")
                        .replace("@name", signEvent.getConfig().getString("Evento.Title"))
        ));

        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                player.getLocation().clone().add(0, 1, 0),
                18,
                0.30,
                0.35,
                0.30,
                0.01
        );

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!signEvent.getPlayers().contains(player)) {
            return;
        }

        if (!signEvent.isBackItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);

        if (signEvent.isBackOnCooldown(player)) {
            player.sendMessage(ColorUtils.colorize(
                    signEvent.getConfig()
                            .getString("Messages.Back cooldown", "&cAguarde @time para usar novamente.")
                            .replace("@time", signEvent.getBackRemainingSeconds(player) + "s")
                            .replace("@name", signEvent.getConfig().getString("Evento.Title"))
            ));
            return;
        }

        signEvent.applyBackCooldown(player);
        returnToCheckpointOrStart(player, signEvent);

        String soundName = signEvent.getConfig().getString("Evento.Back item sound", "entity.enderman.teleport");
        Sound sound = parseSound(soundName);
        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        if (!signEvent.getPlayers().contains(event.getPlayer())) {
            return;
        }

        if (signEvent.isBackItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        Player player = event.getPlayer();

        if (signEvent.getPlayers().contains(player) || signEvent.getSpectators().contains(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        Player player = event.getPlayer();

        if (signEvent.getPlayers().contains(player) || signEvent.getSpectators().contains(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        if (!signEvent.getPlayers().contains(player)) {
            return;
        }

        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.VOID) {

            event.setCancelled(true);

            if (signEvent.returnsOnDamage()) {
                returnToCheckpointOrStart(player, signEvent);
            } else {
                signEvent.eliminate(player);
            }

            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Sign signEvent = getEvento();
        if (signEvent == null) {
            return;
        }

        Player player = event.getEntity();

        if (!signEvent.getPlayers().contains(player)) {
            return;
        }

        event.setCancelled(true);
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        if (signEvent.returnsOnDamage()) {
            Bukkit.getScheduler().runTask(AbsolutEventsPlugin.getInstance(), () -> returnToCheckpointOrStart(player, signEvent));
            return;
        }

        Bukkit.getScheduler().runTask(AbsolutEventsPlugin.getInstance(), () -> {
            signEvent.eliminate(player);

            Bukkit.getPluginManager().callEvent(
                    new PlayerLoseEvent(
                            player,
                            signEvent.getConfig().getString("filename", "").replace(".yml", ""),
                            signEvent.getType()
                    )
            );
        });
    }

    private void returnToCheckpointOrStart(Player player, Sign signEvent) {
        Location destination = checkpoints.get(player.getUniqueId());

        player.setFireTicks(0);
        player.setVisualFire(false);

        if (destination != null) {
            Location teleport = destination.clone().add(0.5, 1.0, 0.5);
            teleport.setYaw(player.getLocation().getYaw());
            teleport.setPitch(player.getLocation().getPitch());

            resetPlayerState(player);
            player.teleport(teleport);
            signEvent.giveBackItem(player);

            Bukkit.getScheduler().runTaskLater(AbsolutEventsPlugin.getInstance(), () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.setFireTicks(0);
                player.setVisualFire(false);
            }, 1L);

            player.sendMessage(ColorUtils.colorize(
                    signEvent.getConfig()
                            .getString("Messages.Checkpoint back", "&eVocê voltou ao checkpoint.")
                            .replace("@name", signEvent.getConfig().getString("Evento.Title"))
            ));
            return;
        }

        YamlConfiguration config = signEvent.getConfig();
        String worldName = config.getString("Locations.Entrance.world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);

        if (world == null) {
            return;
        }

        Location entrance = new Location(
                world,
                config.getDouble("Locations.Entrance.x"),
                config.getDouble("Locations.Entrance.y"),
                config.getDouble("Locations.Entrance.z"),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );

        resetPlayerState(player);
        player.teleport(entrance);
        signEvent.giveBackItem(player);

        Bukkit.getScheduler().runTaskLater(AbsolutEventsPlugin.getInstance(), () -> {
            if (!player.isOnline()) {
                return;
            }
            player.setFireTicks(0);
            player.setVisualFire(false);
        }, 1L);

        player.sendMessage(ColorUtils.colorize(
                signEvent.getConfig()
                        .getString("Messages.Back", "&eVocê voltou ao início.")
                        .replace("@name", signEvent.getConfig().getString("Evento.Title"))
        ));
    }

    private Location getMatchedCheckpoint(Location loc, Sign signEvent) {
        ConfigurationSection section = signEvent.getConfig().getConfigurationSection("Checkpoints");
        if (section == null) {
            return null;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection checkpoint = section.getConfigurationSection(key);
            if (checkpoint == null) {
                continue;
            }

            String worldName = checkpoint.getString("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            Location saved = new Location(
                    world,
                    checkpoint.getDouble("x"),
                    checkpoint.getDouble("y"),
                    checkpoint.getDouble("z")
            );

            if (isNearBlock(loc, saved, 1, 2, 1)) {
                return saved;
            }
        }

        return null;
    }

    private boolean isFinish(Location loc, Sign signEvent) {
        ConfigurationSection section = signEvent.getConfig().getConfigurationSection("Finish");
        if (section == null) {
            return false;
        }

        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }

        Location saved = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z")
            );

        return isNearBlock(loc, saved, 1, 2, 1);
    }

    private boolean isNearBlock(Location first, Location second, int rangeX, int rangeY, int rangeZ) {
        if (first.getWorld() == null || second.getWorld() == null) {
            return false;
        }

        if (!first.getWorld().getName().equals(second.getWorld().getName())) {
            return false;
        }

        return Math.abs(first.getBlockX() - second.getBlockX()) <= rangeX
                && Math.abs(first.getBlockY() - second.getBlockY()) <= rangeY
                && Math.abs(first.getBlockZ() - second.getBlockZ()) <= rangeZ;
    }

    private void resetPlayerState(Player player) {
        player.setFireTicks(0);
        player.setVisualFire(false);
        player.setFallDistance(0.0F);
        player.setVelocity(new Vector(0, 0, 0));
        player.setNoDamageTicks(20);
        player.setFreezeTicks(0);
        player.setFoodLevel(20);
        if (player.getHealth() < player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null
                && second.getWorld() != null
                && first.getWorld().getName().equals(second.getWorld().getName())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private void startParticles() {
        stopParticles();

        particleTask = Bukkit.getScheduler().runTaskTimer(AbsolutEventsPlugin.getInstance(), () -> {
            Sign signEvent = getEvento();
            if (signEvent == null || !signEvent.isHappening()) {
                stopParticles();
                return;
            }

            spawnCheckpointParticles(signEvent);
            spawnFinishParticles(signEvent);
        }, 0L, 10L);
    }

    private void spawnCheckpointParticles(Sign signEvent) {
        ConfigurationSection section = signEvent.getConfig().getConfigurationSection("Checkpoints");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection checkpoint = section.getConfigurationSection(key);
            if (checkpoint == null) {
                continue;
            }

            String worldName = checkpoint.getString("world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            Location loc = new Location(
                    world,
                    checkpoint.getDouble("x") + 0.5,
                    checkpoint.getDouble("y") + 0.15,
                    checkpoint.getDouble("z") + 0.5
            );

            world.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    loc,
                    4,
                    0.18, 0.05, 0.18,
                    0.0
            );
        }
    }

    private void spawnFinishParticles(Sign signEvent) {
        ConfigurationSection section = signEvent.getConfig().getConfigurationSection("Finish");
        if (section == null) {
            return;
        }

        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }

        Location loc = new Location(
                world,
                section.getDouble("x") + 0.5,
                section.getDouble("y") + 0.15,
                section.getDouble("z") + 0.5
        );

        world.spawnParticle(
                Particle.END_ROD,
                loc,
                5,
                0.20, 0.08, 0.20,
                0.0
        );
    }

    private Sound parseSound(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);

        NamespacedKey directKey = NamespacedKey.fromString(normalized);
        if (directKey != null) {
            Sound direct = Registry.SOUNDS.get(directKey);
            if (direct != null) {
                return direct;
            }
        }

        String vanillaPath = normalized
                .replace("minecraft:", "")
                .replace('_', '.');

        return Registry.SOUNDS.get(NamespacedKey.minecraft(vanillaPath));
    }

    private void stopParticles() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    public void setEvento() {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() instanceof Sign sign) {
            this.evento = sign;
            startParticles();
        }
    }

    public void clearCheckpoints() {
        checkpoints.clear();
        lastActivatedCheckpoint.clear();
        damageCooldown.clear();
        stopParticles();
    }

    public void removeCheckpoint(Player player) {
        checkpoints.remove(player.getUniqueId());
        lastActivatedCheckpoint.remove(player.getUniqueId());
        damageCooldown.remove(player.getUniqueId());
    }

    private Sign getEvento() {
        if (evento == null) {
            setEvento();
        }
        return evento;
    }
}
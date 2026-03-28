package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.utils.InventoryConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class InventorySerializer {

    private static final Map<UUID, PlayerSnapshot> SNAPSHOT_CACHE = new ConcurrentHashMap<>();
    private static final Set<UUID> RESTORE_GUARD = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Object> LOCKS = new ConcurrentHashMap<>();

    private InventorySerializer() {
    }

    /*
     * =========================
     * COMPATIBILIDADE LEGADA
     * =========================
     */

    public static boolean serialize(@NotNull Player player, @NotNull String eventIdentifier) {
        return saveSnapshot(player, eventIdentifier);
    }

    public static boolean deserialize(@NotNull Player player, @NotNull String eventIdentifier, boolean leaved) {
        return restoreSnapshot(player, eventIdentifier);
    }

    public static boolean deserialize(@NotNull Player player) {
        return restoreSnapshot(player);
    }

    /*
     * =========================
     * API NOVA
     * =========================
     */

    public static boolean saveSnapshot(@NotNull Player player, @NotNull String eventIdentifier) {
        if (eventIdentifier.isBlank()) {
            return false;
        }

        Object lock = lock(player.getUniqueId());

        synchronized (lock) {
            try {
                PlayerSnapshot snapshot = captureSnapshot(player, eventIdentifier);

                if (!hasAnyData(snapshot)) {
                    Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Snapshot vazio não será salvo para " + player.getName() + ".");
                    return false;
                }

                SNAPSHOT_CACHE.put(player.getUniqueId(), snapshot);

                InventoryConfigFile.create(player.getUniqueId().toString());
                InventoryConfigFile.create(player.getUniqueId().toString(), eventIdentifier);

                YamlConfiguration primary = InventoryConfigFile.get(player.getUniqueId().toString());
                writeSnapshot(primary, snapshot);
                InventoryConfigFile.save(primary);

                YamlConfiguration backup = InventoryConfigFile.get(player.getUniqueId().toString(), eventIdentifier);
                writeSnapshot(backup, snapshot);
                InventoryConfigFile.save(backup, eventIdentifier);

                wipePlayerForEvent(player);
                return true;

            } catch (Exception exception) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao salvar snapshot do jogador " + player.getName() + ".");
                exception.printStackTrace();
                return false;
            }
        }
    }

    public static boolean restoreSnapshot(@NotNull Player player) {
        Object lock = lock(player.getUniqueId());

        synchronized (lock) {
            if (RESTORE_GUARD.contains(player.getUniqueId())) {
                return false;
            }

            PlayerSnapshot snapshot = SNAPSHOT_CACHE.get(player.getUniqueId());

            if (snapshot == null) {
                snapshot = loadSnapshot(player.getUniqueId(), null);
            }

            if (!isValidSnapshot(snapshot)) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Snapshot inválido ou vazio para " + player.getName() + ".");
                return false;
            }

            RESTORE_GUARD.add(player.getUniqueId());

            try {
                if (player.isDead()) {
                    player.spigot().respawn();
                }

                applySnapshot(player, snapshot);
                SNAPSHOT_CACHE.put(player.getUniqueId(), snapshot);
                return true;

            } catch (Exception exception) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao restaurar snapshot do jogador " + player.getName() + ".");
                exception.printStackTrace();
                return false;

            } finally {
                RESTORE_GUARD.remove(player.getUniqueId());
            }
        }
    }

    public static boolean restoreSnapshot(@NotNull Player player, @NotNull String eventIdentifier) {
        Object lock = lock(player.getUniqueId());

        synchronized (lock) {
            if (RESTORE_GUARD.contains(player.getUniqueId())) {
                return false;
            }

            PlayerSnapshot snapshot = SNAPSHOT_CACHE.get(player.getUniqueId());

            if (snapshot == null || !eventIdentifier.equalsIgnoreCase(snapshot.eventIdentifier())) {
                snapshot = loadSnapshot(player.getUniqueId(), eventIdentifier);
            }

            if (!isValidSnapshot(snapshot)) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Snapshot inválido ou vazio para " + player.getName() + " no evento " + eventIdentifier + ".");
                return false;
            }

            RESTORE_GUARD.add(player.getUniqueId());

            try {
                if (player.isDead()) {
                    player.spigot().respawn();
                }

                applySnapshot(player, snapshot);
                SNAPSHOT_CACHE.put(player.getUniqueId(), snapshot);
                return true;

            } catch (Exception exception) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao restaurar snapshot do jogador " + player.getName() + ".");
                exception.printStackTrace();
                return false;

            } finally {
                RESTORE_GUARD.remove(player.getUniqueId());
            }
        }
    }

    public static boolean hasSnapshot(@NotNull UUID uniqueId) {
        if (SNAPSHOT_CACHE.containsKey(uniqueId)) {
            return true;
        }

        return InventoryConfigFile.exists(uniqueId.toString());
    }

    public static void removeCache(@NotNull UUID uniqueId) {
        SNAPSHOT_CACHE.remove(uniqueId);
        RESTORE_GUARD.remove(uniqueId);
        LOCKS.remove(uniqueId);
    }

    public static void clearRuntimeCache() {
        SNAPSHOT_CACHE.clear();
        RESTORE_GUARD.clear();
        LOCKS.clear();
    }

    @Nullable
    public static String getSnapshotEventIdentifier(@NotNull UUID uniqueId) {
        PlayerSnapshot snapshot = SNAPSHOT_CACHE.get(uniqueId);

        if (snapshot != null) {
            return snapshot.eventIdentifier();
        }

        PlayerSnapshot loaded = loadSnapshot(uniqueId, null);
        return loaded == null ? null : loaded.eventIdentifier();
    }

    public static boolean deleteSnapshot(@NotNull UUID uniqueId) {
        Object lock = lock(uniqueId);

        synchronized (lock) {
            PlayerSnapshot snapshot = SNAPSHOT_CACHE.get(uniqueId);
            String eventIdentifier = snapshot == null ? null : snapshot.eventIdentifier();
            return deleteSnapshot(uniqueId, eventIdentifier);
        }
    }

    public static boolean deleteSnapshot(@NotNull UUID uniqueId, @Nullable String eventIdentifier) {
        Object lock = lock(uniqueId);

        synchronized (lock) {
            boolean deletedSomething = false;

            try {
                if (InventoryConfigFile.exists(uniqueId.toString())) {
                    YamlConfiguration primary = InventoryConfigFile.get(uniqueId.toString());
                    InventoryConfigFile.delete(primary);
                    deletedSomething = true;
                }

                if (eventIdentifier != null
                        && !eventIdentifier.isBlank()
                        && InventoryConfigFile.exists(uniqueId.toString(), eventIdentifier)) {
                    YamlConfiguration backup = InventoryConfigFile.get(uniqueId.toString(), eventIdentifier);
                    InventoryConfigFile.delete(backup, eventIdentifier);
                    deletedSomething = true;
                }

            } catch (Exception exception) {
                Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao remover arquivos de snapshot do jogador " + uniqueId + ".");
                exception.printStackTrace();
            }

            SNAPSHOT_CACHE.remove(uniqueId);
            RESTORE_GUARD.remove(uniqueId);
            return deletedSomething;
        }
    }

    private static boolean isValidSnapshot(@Nullable PlayerSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }

        if (snapshot.storageBase64() == null || snapshot.storageBase64().isBlank()) {
            return false;
        }

        try {
            itemStackArrayFromBase64(snapshot.storageBase64(), 36);
            itemStackArrayFromBase64(snapshot.armorBase64(), 4);
            itemStackArrayFromBase64(snapshot.extraBase64(), 5);
            itemStackFromBase64(snapshot.offhandBase64());
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private static boolean hasAnyData(@NotNull PlayerSnapshot snapshot) {
        ItemStack[] storage = itemStackArrayFromBase64(snapshot.storageBase64(), 36);
        ItemStack[] armor = itemStackArrayFromBase64(snapshot.armorBase64(), 4);
        ItemStack[] extra = itemStackArrayFromBase64(snapshot.extraBase64(), 5);
        ItemStack offhand = itemStackFromBase64(snapshot.offhandBase64());

        if (containsRealItem(storage) || containsRealItem(armor) || containsRealItem(extra)) {
            return true;
        }

        return offhand != null && offhand.getType() != org.bukkit.Material.AIR;
    }

    private static boolean containsRealItem(@Nullable ItemStack[] items) {
        if (items == null) {
            return false;
        }

        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                return true;
            }
        }

        return false;
    }

    private static void wipePlayerForEvent(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();

        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        inventory.setItemInOffHand(null);

        player.setLevel(0);
        player.setExp(0.0f);
        player.setTotalExperience(0);

        player.updateInventory();
    }

    @NotNull
    private static PlayerSnapshot captureSnapshot(@NotNull Player player, @NotNull String eventIdentifier) throws IOException {
        PlayerInventory inventory = player.getInventory();

        return new PlayerSnapshot(
                eventIdentifier,
                itemStackArrayToBase64(inventory.getStorageContents()),
                itemStackArrayToBase64(inventory.getArmorContents()),
                itemStackArrayToBase64(inventory.getExtraContents()),
                itemStackToBase64(inventory.getItemInOffHand()),
                itemStackArrayToBase64(player.getEnderChest().getContents()),
                player.getGameMode().name(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getExp(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getRemainingAir(),
                player.getFireTicks(),
                safeHealth(player),
                inventory.getHeldItemSlot(),
                serializePotionEffects(player.getActivePotionEffects())
        );
    }

    private static void applySnapshot(@NotNull Player player, @NotNull PlayerSnapshot snapshot) {
        PlayerInventory inventory = player.getInventory();

        ItemStack[] storage = itemStackArrayFromBase64(snapshot.storageBase64(), 36);
        ItemStack[] armor = itemStackArrayFromBase64(snapshot.armorBase64(), 4);
        ItemStack[] extra = itemStackArrayFromBase64(snapshot.extraBase64(), inventory.getExtraContents().length);
        ItemStack offhand = itemStackFromBase64(snapshot.offhandBase64());
        ItemStack[] enderChest = itemStackArrayFromBase64(snapshot.enderChestBase64(), player.getEnderChest().getSize());

        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setExtraContents(new ItemStack[inventory.getExtraContents().length]);
        inventory.setItemInOffHand(null);

        inventory.setStorageContents(storage);
        inventory.setArmorContents(armor);
        inventory.setExtraContents(extra);
        inventory.setItemInOffHand(offhand);

        player.getEnderChest().setContents(enderChest);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        for (PotionEffect effect : deserializePotionEffects(snapshot.potionEffects())) {
            player.addPotionEffect(effect, true);
        }

        player.setGameMode(parseGameMode(snapshot.gameMode()));
        player.setAllowFlight(snapshot.allowFlight());
        player.setFlying(snapshot.flying());

        player.setExp(snapshot.exp());
        player.setLevel(snapshot.level());
        player.setTotalExperience(snapshot.totalExperience());

        player.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel())));
        player.setSaturation(Math.max(0.0F, snapshot.saturation()));
        player.setExhaustion(Math.max(0.0F, snapshot.exhaustion()));
        player.setRemainingAir(Math.max(0, snapshot.remainingAir()));
        player.setFireTicks(Math.max(0, snapshot.fireTicks()));

        double maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        player.setHealth(Math.max(0.5D, Math.min(maxHealth, snapshot.health())));

        inventory.setHeldItemSlot(Math.max(0, Math.min(8, snapshot.heldSlot())));
        player.updateInventory();
    }

    private static void writeSnapshot(@NotNull YamlConfiguration config, @NotNull PlayerSnapshot snapshot) {
        config.set("snapshot.version", 2);
        config.set("snapshot.event", snapshot.eventIdentifier());

        config.set("snapshot.inventory.storage", snapshot.storageBase64());
        config.set("snapshot.inventory.armor", snapshot.armorBase64());
        config.set("snapshot.inventory.extra", snapshot.extraBase64());
        config.set("snapshot.inventory.offhand", snapshot.offhandBase64());
        config.set("snapshot.inventory.enderchest", snapshot.enderChestBase64());

        config.set("snapshot.player.gamemode", snapshot.gameMode());
        config.set("snapshot.player.allow-flight", snapshot.allowFlight());
        config.set("snapshot.player.flying", snapshot.flying());
        config.set("snapshot.player.exp", snapshot.exp());
        config.set("snapshot.player.level", snapshot.level());
        config.set("snapshot.player.total-exp", snapshot.totalExperience());
        config.set("snapshot.player.food", snapshot.foodLevel());
        config.set("snapshot.player.saturation", snapshot.saturation());
        config.set("snapshot.player.exhaustion", snapshot.exhaustion());
        config.set("snapshot.player.remaining-air", snapshot.remainingAir());
        config.set("snapshot.player.fire-ticks", snapshot.fireTicks());
        config.set("snapshot.player.health", snapshot.health());
        config.set("snapshot.player.held-slot", snapshot.heldSlot());
        config.set("snapshot.player.effects", snapshot.potionEffects());
    }

    @Nullable
    private static PlayerSnapshot loadSnapshot(@NotNull UUID uniqueId, @Nullable String eventIdentifier) {
        try {
            YamlConfiguration primary = null;
            if (InventoryConfigFile.exists(uniqueId.toString())) {
                primary = InventoryConfigFile.get(uniqueId.toString());
            }

            if (primary != null && primary.contains("snapshot")) {
                PlayerSnapshot snapshot = readSnapshot(primary);
                if ((eventIdentifier == null || eventIdentifier.equalsIgnoreCase(snapshot.eventIdentifier()))
                        && isValidSnapshot(snapshot)) {
                    SNAPSHOT_CACHE.put(uniqueId, snapshot);
                    return snapshot;
                }
            }

            if (eventIdentifier != null && InventoryConfigFile.exists(uniqueId.toString(), eventIdentifier)) {
                YamlConfiguration backup = InventoryConfigFile.get(uniqueId.toString(), eventIdentifier);
                if (backup.contains("snapshot")) {
                    PlayerSnapshot snapshot = readSnapshot(backup);
                    if (isValidSnapshot(snapshot)) {
                        SNAPSHOT_CACHE.put(uniqueId, snapshot);
                        return snapshot;
                    }
                }
            }

        } catch (Exception exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao carregar snapshot salvo do jogador " + uniqueId + ".");
            exception.printStackTrace();
        }

        return null;
    }

    @NotNull
    private static PlayerSnapshot readSnapshot(@NotNull YamlConfiguration config) {
        return new PlayerSnapshot(
                config.getString("snapshot.event", ""),
                config.getString("snapshot.inventory.storage", ""),
                config.getString("snapshot.inventory.armor", ""),
                config.getString("snapshot.inventory.extra", ""),
                config.getString("snapshot.inventory.offhand", ""),
                config.getString("snapshot.inventory.enderchest", ""),
                config.getString("snapshot.player.gamemode", GameMode.SURVIVAL.name()),
                config.getBoolean("snapshot.player.allow-flight"),
                config.getBoolean("snapshot.player.flying"),
                (float) config.getDouble("snapshot.player.exp", 0.0D),
                config.getInt("snapshot.player.level", 0),
                config.getInt("snapshot.player.total-exp", 0),
                config.getInt("snapshot.player.food", 20),
                (float) config.getDouble("snapshot.player.saturation", 5.0D),
                (float) config.getDouble("snapshot.player.exhaustion", 0.0D),
                config.getInt("snapshot.player.remaining-air", 300),
                config.getInt("snapshot.player.fire-ticks", 0),
                config.getDouble("snapshot.player.health", 20.0D),
                config.getInt("snapshot.player.held-slot", 0),
                castPotionEffectMapList(config.getMapList("snapshot.player.effects"))
        );
    }

    private static double safeHealth(@NotNull Player player) {
        double maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        return Math.max(0.5D, Math.min(maxHealth, player.getHealth()));
    }

    @NotNull
    private static String itemStackArrayToBase64(@NotNull ItemStack[] items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
        }

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    @NotNull
    private static String itemStackToBase64(@Nullable ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
        }

        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    @NotNull
    private static ItemStack[] itemStackArrayFromBase64(@Nullable String data, int expectedSize) {
        if (data == null || data.isBlank()) {
            return new ItemStack[expectedSize];
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));

            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                int length = dataInput.readInt();
                ItemStack[] items = new ItemStack[expectedSize];

                for (int index = 0; index < length; index++) {
                    Object object = dataInput.readObject();

                    if (object instanceof ItemStack item && index < items.length) {
                        items[index] = item;
                    }
                }

                return items;
            }

        } catch (Exception exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Não foi possível decodificar um array de itens salvo.");
            exception.printStackTrace();
            return new ItemStack[expectedSize];
        }
    }

    @Nullable
    private static ItemStack itemStackFromBase64(@Nullable String data) {
        if (data == null || data.isBlank()) {
            return null;
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));

            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
                Object object = dataInput.readObject();
                return object instanceof ItemStack item ? item : null;
            }

        } catch (Exception exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Não foi possível decodificar item salvo.");
            exception.printStackTrace();
            return null;
        }
    }

    @NotNull
    private static List<Map<String, Object>> serializePotionEffects(@NotNull Collection<PotionEffect> effects) {
        List<Map<String, Object>> serialized = new ArrayList<>();

        for (PotionEffect effect : effects) {
            Map<String, Object> map = new HashMap<>();
            map.put("type", effect.getType().getName());
            map.put("duration", effect.getDuration());
            map.put("amplifier", effect.getAmplifier());
            map.put("ambient", effect.isAmbient());
            map.put("particles", effect.hasParticles());
            map.put("icon", effect.hasIcon());
            serialized.add(map);
        }

        return serialized;
    }

    @NotNull
    private static List<PotionEffect> deserializePotionEffects(@Nullable List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        List<PotionEffect> effects = new ArrayList<>();

        for (Map<String, Object> raw : list) {
            try {
                String typeName = String.valueOf(raw.get("type"));
                PotionEffectType type = PotionEffectType.getByName(typeName);

                if (type == null) {
                    continue;
                }

                int duration = ((Number) raw.getOrDefault("duration", 0)).intValue();
                int amplifier = ((Number) raw.getOrDefault("amplifier", 0)).intValue();
                boolean ambient = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("ambient", false)));
                boolean particles = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("particles", true)));
                boolean icon = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("icon", true)));

                effects.add(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
            } catch (Exception ignored) {
            }
        }

        return effects;
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castPotionEffectMapList(@NotNull List<Map<?, ?>> rawList) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<?, ?> raw : rawList) {
            Map<String, Object> converted = new HashMap<>();

            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            result.add(converted);
        }

        return result;
    }

    @NotNull
    private static GameMode parseGameMode(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return GameMode.SURVIVAL;
        }

        try {
            return GameMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return GameMode.SURVIVAL;
        }
    }

    @NotNull
    private static Object lock(@NotNull UUID uniqueId) {
        return LOCKS.computeIfAbsent(uniqueId, ignored -> new Object());
    }

    private record PlayerSnapshot(
            String eventIdentifier,
            String storageBase64,
            String armorBase64,
            String extraBase64,
            String offhandBase64,
            String enderChestBase64,
            String gameMode,
            boolean allowFlight,
            boolean flying,
            float exp,
            int level,
            int totalExperience,
            int foodLevel,
            float saturation,
            float exhaustion,
            int remainingAir,
            int fireTicks,
            double health,
            int heldSlot,
            List<Map<String, Object>> potionEffects
    ) {
    }
}
package com.absolutgg.absolutevents.utils;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class Utils {

    private static final BlockFace[] CARDINAL = {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
    };

    private static final BlockFace[] RADIAL = {
            BlockFace.NORTH,
            BlockFace.NORTH_EAST,
            BlockFace.EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST,
            BlockFace.NORTH_WEST
    };

    private Utils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Verifica se o jogador possui qualquer item no inventário ou armadura.
     */
    public static boolean isInventoryFull(@NotNull Player player) {
        PlayerInventory inventory = player.getInventory();

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }

        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return true;
            }
        }

        return false;
    }

    /**
     * Converte yaw para direção.
     */
    @NotNull
    public static BlockFace yawToFace(float yaw, boolean useSubCardinalDirections) {

        yaw = yaw % 360;

        if (useSubCardinalDirections) {
            return RADIAL[Math.round(yaw / 45f) & 0x7].getOppositeFace();
        }

        return CARDINAL[Math.round(yaw / 90f) & 0x3].getOppositeFace();
    }

    /**
     * Retorna todas as chaves de um Map que possuem determinado valor.
     */
    @NotNull
    public static <K, V> Set<K> getKeysByValue(@NotNull Map<K, V> map, V value) {
        return map.entrySet()
                .stream()
                .filter(entry -> Objects.equals(entry.getValue(), value))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
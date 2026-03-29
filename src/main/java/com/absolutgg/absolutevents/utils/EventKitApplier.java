package com.absolutgg.absolutevents.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EventKitApplier {

    private EventKitApplier() {
    }

    public static void apply(Player player, ConfigurationSection itensSection) {
        if (player == null || itensSection == null) {
            return;
        }

        ItemStack helmet = CustomItemResolver.resolve(itensSection.getConfigurationSection("Helmet"));
        if (helmet != null) {
            player.getInventory().setHelmet(helmet);
        }

        ItemStack chestplate = CustomItemResolver.resolve(itensSection.getConfigurationSection("Chestplate"));
        if (chestplate != null) {
            player.getInventory().setChestplate(chestplate);
        }

        ItemStack leggings = CustomItemResolver.resolve(itensSection.getConfigurationSection("Leggings"));
        if (leggings != null) {
            player.getInventory().setLeggings(leggings);
        }

        ItemStack boots = CustomItemResolver.resolve(itensSection.getConfigurationSection("Boots"));
        if (boots != null) {
            player.getInventory().setBoots(boots);
        }

        ItemStack offhand = CustomItemResolver.resolve(itensSection.getConfigurationSection("Offhand"));
        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }

        ConfigurationSection inventorySection = itensSection.getConfigurationSection("Inventory");
        if (inventorySection != null) {
            for (String slotKey : inventorySection.getKeys(false)) {
                ConfigurationSection itemSection = inventorySection.getConfigurationSection(slotKey);
                if (itemSection == null) {
                    continue;
                }

                int slot;
                try {
                    slot = Integer.parseInt(slotKey);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                ItemStack item = CustomItemResolver.resolve(itemSection);
                if (item == null) {
                    continue;
                }

                player.getInventory().setItem(slot, item);
            }
        }

        player.updateInventory();
    }
}
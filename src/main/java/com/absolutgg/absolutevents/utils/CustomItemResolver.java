package com.absolutgg.absolutevents.utils;

import com.cryptomorin.xseries.XItemStack;
import dev.lone.itemsadder.api.CustomStack;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class CustomItemResolver {

    private CustomItemResolver() {
    }

    public static ItemStack resolve(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String type = section.getString("Type", "").trim().toUpperCase();
        String id = section.getString("Id", "").trim();
        int amount = Math.max(1, section.getInt("Amount", 1));

        if (type.isEmpty()) {
            ItemStack legacy = resolveLegacy(section, amount);
            if (legacy == null) {
                Bukkit.getLogger().warning("[AbsolutEvents] Não foi possível resolver item legado em: " + section.getCurrentPath());
            }
            return legacy;
        }

        ItemStack result = switch (type) {
            case "VANILLA" -> resolveVanilla(id, amount);
            case "MMOITEMS" -> resolveMMOItems(id, amount);
            case "ITEMSADDER" -> resolveItemsAdder(id, amount);
            default -> resolveLegacy(section, amount);
        };

        if (result == null) {
            Bukkit.getLogger().warning("[AbsolutEvents] Falha ao resolver item custom. Path: "
                    + section.getCurrentPath() + " | Type: " + type + " | Id: " + id);
        }

        return result;
    }

    private static ItemStack resolveLegacy(ConfigurationSection section, int amount) {
        try {
            String materialName = section.getString("material");
            if (materialName != null && !materialName.isBlank()) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    return new ItemStack(material, amount);
                }
            }

            ItemStack item = XItemStack.deserialize(section);
            if (item != null) {
                item = item.clone();
                item.setAmount(amount);
            }
            return item;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("[AbsolutEvents] Erro ao resolver item legado em "
                    + section.getCurrentPath() + ": " + ex.getMessage());
            return null;
        }
    }

    private static ItemStack resolveVanilla(String id, int amount) {
        if (id == null || id.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(id);
        if (material == null) {
            Bukkit.getLogger().warning("[AbsolutEvents] Material vanilla inválido: " + id);
            return null;
        }

        return new ItemStack(material, amount);
    }

    private static ItemStack resolveMMOItems(String id, int amount) {
        if (id == null || id.isBlank() || !id.contains(":")) {
            Bukkit.getLogger().warning("[AbsolutEvents] Id MMOItems inválido: " + id);
            return null;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("MMOItems");
        if (plugin == null || !plugin.isEnabled()) {
            Bukkit.getLogger().warning("[AbsolutEvents] MMOItems não está carregado.");
            return null;
        }

        String[] split = id.split(":", 2);
        if (split.length != 2) {
            Bukkit.getLogger().warning("[AbsolutEvents] Id MMOItems mal formatado: " + id);
            return null;
        }

        Type itemType = MMOItems.plugin.getTypes().get(split[0].toUpperCase());
        if (itemType == null) {
            Bukkit.getLogger().warning("[AbsolutEvents] Tipo MMOItems não encontrado: " + split[0]);
            return null;
        }

        ItemStack item = MMOItems.plugin.getItem(itemType, split[1]);
        if (item == null) {
            Bukkit.getLogger().warning("[AbsolutEvents] Item MMOItems não encontrado: " + id);
            return null;
        }

        item = item.clone();
        item.setAmount(amount);
        return item;
    }

    private static ItemStack resolveItemsAdder(String id, int amount) {
        if (id == null || id.isBlank()) {
            Bukkit.getLogger().warning("[AbsolutEvents] Id ItemsAdder vazio.");
            return null;
        }

        Plugin plugin = Bukkit.getPluginManager().getPlugin("ItemsAdder");
        if (plugin == null || !plugin.isEnabled()) {
            Bukkit.getLogger().warning("[AbsolutEvents] ItemsAdder não está carregado.");
            return null;
        }

        CustomStack customStack = CustomStack.getInstance(id);
        if (customStack == null) {
            Bukkit.getLogger().warning("[AbsolutEvents] Item ItemsAdder não encontrado: " + id);
            return null;
        }

        ItemStack item = customStack.getItemStack().clone();
        item.setAmount(amount);
        return item;
    }
}
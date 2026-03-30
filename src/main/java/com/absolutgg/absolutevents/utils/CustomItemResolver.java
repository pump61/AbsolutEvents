package com.absolutgg.absolutevents.utils;

import com.cryptomorin.xseries.XItemStack;
import dev.lone.itemsadder.api.CustomStack;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

        ItemStack result;

        if (type.isEmpty()) {
            result = resolveLegacy(section, amount);
            if (result == null) {
                Bukkit.getLogger().warning("[AbsolutEvents] Não foi possível resolver item legado em: " + section.getCurrentPath());
                return null;
            }
        } else {
            result = switch (type) {
                case "VANILLA" -> resolveVanilla(id, amount);
                case "MMOITEMS" -> resolveMMOItems(id, amount);
                case "ITEMSADDER" -> resolveItemsAdder(id, amount);
                default -> resolveLegacy(section, amount);
            };

            if (result == null) {
                Bukkit.getLogger().warning("[AbsolutEvents] Falha ao resolver item custom. Path: "
                        + section.getCurrentPath() + " | Type: " + type + " | Id: " + id);
                return null;
            }
        }

        applyExtraData(result, section);
        return result;
    }

    private static ItemStack resolveLegacy(ConfigurationSection section, int amount) {
        try {
            String materialName = section.getString("material");
            if (materialName != null && !materialName.isBlank()) {
                Material material = Material.matchMaterial(materialName);
                if (material != null) {
                    ItemStack item = new ItemStack(material, amount);
                    applyExtraData(item, section);
                    return item;
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

    private static void applyExtraData(ItemStack item, ConfigurationSection section) {
        if (item == null || section == null) {
            return;
        }

        int configuredAmount = section.contains("Amount")
                ? section.getInt("Amount", item.getAmount())
                : section.getInt("amount", item.getAmount());

        item.setAmount(Math.max(1, configuredAmount));

        applyEnchants(item, section);
        applyFlags(item, section);
        applyUnbreakable(item, section);
    }

    private static void applyEnchants(ItemStack item, ConfigurationSection section) {
        ConfigurationSection enchants = section.getConfigurationSection("enchants");
        if (enchants == null) {
            enchants = section.getConfigurationSection("Enchants");
        }

        if (enchants == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        for (String enchantKey : enchants.getKeys(false)) {
            Enchantment enchantment = parseEnchantment(enchantKey);
            if (enchantment == null) {
                Bukkit.getLogger().warning("[AbsolutEvents] Encantamento inválido: " + enchantKey
                        + " em " + section.getCurrentPath());
                continue;
            }

            int level = enchants.getInt(enchantKey, 1);
            meta.addEnchant(enchantment, level, true);
        }

        item.setItemMeta(meta);
    }

    private static void applyFlags(ItemStack item, ConfigurationSection section) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        if (section.getBoolean("hide enchants", false) || section.getBoolean("Hide enchants", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        if (section.getBoolean("hide attributes", false) || section.getBoolean("Hide attributes", false)) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        }

        if (section.getBoolean("hide unbreakable", false) || section.getBoolean("Hide unbreakable", false)) {
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        }

        item.setItemMeta(meta);
    }

    private static void applyUnbreakable(ItemStack item, ConfigurationSection section) {
        if (!section.contains("unbreakable") && !section.contains("Unbreakable")) {
            return;
        }

        boolean unbreakable = section.getBoolean("unbreakable", section.getBoolean("Unbreakable", false));

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setUnbreakable(unbreakable);
        item.setItemMeta(meta);
    }

    private static Enchantment parseEnchantment(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Enchantment enchantment = Enchantment.getByName(name.toUpperCase());
        if (enchantment != null) {
            return enchantment;
        }

        for (Enchantment e : Enchantment.values()) {
            if (e.getKey().getKey().equalsIgnoreCase(name)
                    || e.getKey().toString().equalsIgnoreCase(name)) {
                return e;
            }
        }

        return null;
    }
}
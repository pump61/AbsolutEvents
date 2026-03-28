package com.absolutgg.absolutevents.inventory.utils;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SimpleItemParser {

    private SimpleItemParser() {
    }

    public static ItemStack parse(ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(org.bukkit.Material.STONE);
        }

        String materialName = section.getString("Material", "STONE");
        XMaterial xMaterial = XMaterial.matchXMaterial(materialName).orElse(XMaterial.STONE);

        ItemStack item = xMaterial.parseItem();
        if (item == null) {
            item = new ItemStack(org.bukkit.Material.STONE);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = section.getString("Name");
        if (name != null) {
            meta.setDisplayName(applyPlaceholders(name, placeholders).replace("&", "§"));
        }

        if (section.contains("Lore")) {
            List<String> lore = new ArrayList<>();
            for (String line : section.getStringList("Lore")) {
                lore.add(applyPlaceholders(line, placeholders).replace("&", "§"));
            }
            meta.setLore(lore);
        }

        if (section.getBoolean("Glow")) {
            if (XEnchantment.UNBREAKING.getEnchant() != null) {
                meta.addEnchant(XEnchantment.UNBREAKING.getEnchant(), 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        item.setItemMeta(meta);

        if (xMaterial == XMaterial.PLAYER_HEAD) {
            ItemMeta currentMeta = item.getItemMeta();
            if (currentMeta instanceof SkullMeta skullMeta) {
                if (section.getBoolean("Custom head")) {
                    String headData = section.getString("Head data", "");
                    applyCustomTexture(skullMeta, "http://textures.minecraft.net/texture/" + headData);
                } else {
                    String owner = applyPlaceholders(section.getString("Head data", ""), placeholders);
                    if (!owner.isBlank()) {
                        skullMeta.setOwner(owner);
                    }
                }
                item.setItemMeta(skullMeta);
            }
        }

        return item;
    }

    private static String applyPlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) {
            return "";
        }

        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                text = text.replace(entry.getKey(), entry.getValue());
            }
        }

        return text;
    }

    private static void applyCustomTexture(SkullMeta meta, String url) {
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), null);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(url));
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (MalformedURLException exception) {
            exception.printStackTrace();
        }
    }
}
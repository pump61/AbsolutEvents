package com.absolutgg.absolutevents.utils.converters.config;

import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class LegacySerializerConverter {

    private LegacySerializerConverter() {
    }

    public static void convertFight(YamlConfiguration config) {
        if (config.getConfigurationSection("Itens") != null) {
            return;
        }

        if (config.getString("Items.Normal.Armor.Helmet.Material") == null) {

            List<String> normalItems = new ArrayList<>();

            for (String s : config.getStringList("Items.Normal.Inventory")) {
                String[] separated = s.split("-");
                separated[0] = resolveLegacyMaterialName(separated[0]);
                normalItems.add(String.join("-", separated));
            }

            config.set("Items.Normal.Inventory", normalItems);

            convertArmor(
                    config,
                    "Items.Normal.Armor.Helmet",
                    "Items.Normal.Armor.Chestplate",
                    "Items.Normal.Armor.Legging",
                    "Items.Normal.Armor.Boots"
            );

            List<String> lastItems = new ArrayList<>();

            for (String s : config.getStringList("Items.Last fight.Inventory")) {
                String[] separated = s.split("-");
                separated[0] = resolveLegacyMaterialName(separated[0]);
                lastItems.add(String.join("-", separated));
            }

            config.set("Items.Last fight.Inventory", lastItems);

            convertArmor(
                    config,
                    "Items.Last fight.Armor.Helmet",
                    "Items.Last fight.Armor.Chestplate",
                    "Items.Last fight.Armor.Legging",
                    "Items.Last fight.Armor.Boots"
            );

            saveConfig(config);
        }
    }

    public static void convertSpleef(YamlConfiguration config) {
        if (config.getConfigurationSection("Itens") != null) {
            return;
        }

        List<String> items = config.getStringList("Items");
        if (items == null || items.isEmpty()) {
            return;
        }

        try {
            Integer.parseInt(items.get(0).split("-")[0]);

            List<String> newItems = new ArrayList<>();

            for (String item : items) {
                String[] separated = item.split("-");
                separated[0] = resolveLegacyMaterialName(separated[0]);
                newItems.add(String.join("-", separated));
            }

            config.set("Items", newItems);
            saveConfig(config);

        } catch (NumberFormatException ignored) {
        }
    }

    private static void convertArmor(YamlConfiguration config, String... paths) {
        for (String path : paths) {
            String value = config.getString(path);

            if (value != null && !value.isBlank()) {
                String[] separated = value.split("-");
                config.set(path + ".Material", resolveLegacyMaterialName(separated[0]));
            } else {
                config.set(path + ".Material", "AIR");
            }

            config.set(path + ".Data", 0);
        }
    }

    private static String resolveLegacyMaterialName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Material.STONE.name();
        }

        Optional<XMaterial> direct = XMaterial.matchXMaterial(raw);
        if (direct.isPresent()) {
            Material material = direct.get().parseMaterial();
            return material != null ? material.name() : Material.STONE.name();
        }

        try {
            Integer.parseInt(raw);
            return Material.STONE.name();
        } catch (NumberFormatException ignored) {
        }

        Material material = Material.matchMaterial(raw, true);
        return material != null ? material.name() : Material.STONE.name();
    }

    private static void saveConfig(YamlConfiguration config) {
        try {
            EventoConfigFile.save(config);
        } catch (IOException exception) {
            Bukkit.getConsoleSender().sendMessage(
                    "§c[AbsolutEvents] Não foi possível converter o arquivo de configuração."
            );
            exception.printStackTrace();
        }
    }
}
package com.absolutgg.absolutevents.utils.converters.config;

import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.cryptomorin.xseries.XItemStack;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("deprecation")
public final class SerializerConverter {

    private SerializerConverter() {}

    public static void convert(YamlConfiguration config) {

        if (config.getConfigurationSection("Itens") != null) return;

        if (config.getConfigurationSection("Itens.Helmet.damage") == null) {

            Bukkit.getConsoleSender().sendMessage(
                    "§e[AbsolutEvents] §aConvertendo config §f" + config.getString("filename") + " §apara o novo formato..."
            );

            if (config.getConfigurationSection("Items.Normal.Armor") != null) {

                convertArmor(config);
                convertInventories(config);

            } else {

                convertSimpleInventory(config);

            }

            config.set("Items", null);

            try {

                EventoConfigFile.save(config);

                Bukkit.getConsoleSender().sendMessage(
                        "§e[AbsolutEvents] §aArquivo §f" + config.getString("filename") + " §aconvertido com sucesso!"
                );

            } catch (IOException exception) {

                Bukkit.getConsoleSender().sendMessage(
                        "§c[AbsolutEvents] Não foi possível converter o arquivo."
                );

                exception.printStackTrace();
            }
        }
    }

    private static void convertArmor(YamlConfiguration config) {

        ConfigurationSection normal = config.getConfigurationSection("Items.Normal.Armor");

        if (normal == null) return;

        convertArmorPiece(config, normal, "Helmet", "Itens.Normal.Armor.Helmet");
        convertArmorPiece(config, normal, "Chestplate", "Itens.Normal.Armor.Chestplate");
        convertArmorPiece(config, normal, "Legging", "Itens.Normal.Armor.Leggings");
        convertArmorPiece(config, normal, "Boots", "Itens.Normal.Armor.Boots");

        config.set("Items.Normal.Armor", null);
    }

    private static void convertArmorPiece(YamlConfiguration config,
                                          ConfigurationSection section,
                                          String part,
                                          String path) {

        if ("null".equals(section.getString(part + ".Material"))) return;

        Optional<XMaterial> material = XMaterial.matchXMaterial(section.getString(part + ".Material"));

        if (material.isEmpty()) return;

        ItemStack item = new ItemStack(
                Objects.requireNonNull(material.get().parseMaterial()),
                1,
                (byte) section.getInt(part + ".Data")
        );

        config.set(path + ".a", "tmp");
        XItemStack.serialize(item, config.getConfigurationSection(path));
        config.set(path + ".a", null);
    }

    private static void convertInventories(YamlConfiguration config) {

        List<String> normalItems = config.getStringList("Items.Normal.Inventory");
        config.set("Items.Normal.Inventory", null);
        config.set("Itens.Normal.Inventory", "");

        serializeInventory(normalItems, config, "Itens.Normal.Inventory");

        List<String> lastItems = config.getStringList("Items.Last fight.Inventory");
        config.set("Items.Last fight.Inventory", null);
        config.set("Itens.Last fight.Inventory", "");

        serializeInventory(lastItems, config, "Itens.Last fight.Inventory");
    }

    private static void convertSimpleInventory(YamlConfiguration config) {

        List<String> items = config.getStringList("Items");

        config.set("Itens.Inventory", "");

        serializeInventory(items, config, "Itens.Inventory");
    }

    private static void serializeInventory(List<String> items, YamlConfiguration config, String path) {

        for (int i = 0; i < items.size(); i++) {

            String s = items.get(i);
            String[] separated = s.split("-");

            ItemStack item;

            Optional<XMaterial> material = XMaterial.matchXMaterial(separated[0]);

            if (material.isEmpty()) continue;

            if (separated.length == 3) {

                item = new ItemStack(
                        Objects.requireNonNull(material.get().parseMaterial()),
                        Integer.parseInt(separated[2]),
                        (byte) Integer.parseInt(separated[1])
                );

            } else {

                item = new ItemStack(
                        Objects.requireNonNull(material.get().parseMaterial()),
                        Integer.parseInt(separated[1])
                );
            }

            config.set(path + "." + i + ".a", "tmp");

            XItemStack.serialize(item,
                    config.getConfigurationSection(path + "." + i));

            config.set(path + "." + i + ".a", null);
        }
    }
}
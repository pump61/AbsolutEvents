package com.absolutgg.absolutevents.inventory;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.inventory.utils.SimpleItemParser;
import com.absolutgg.absolutevents.manager.InventoryManager;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.absolutgg.absolutevents.utils.MenuConfigFile;
import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.simple.SimpleInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.ViewerConfiguration;
import com.henryfabio.minecraft.inventoryapi.viewer.impl.simple.SimpleViewer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventoMainInventory extends SimpleInventory {

    private YamlConfiguration config = MenuConfigFile.get("main");

    public EventoMainInventory() {
        super(
                "absolutevents.inventory.main",
                "&8Eventos",
                27
        );
    }

    @Override
    protected void configureViewer(SimpleViewer viewer) {
        ViewerConfiguration configuration = viewer.getConfiguration();
        configuration.titleInventory(
                config.getString("Menu.Name", "&8Eventos")
                        .replace("@jogador", viewer.getName())
                        .replace("&", "§")
        );
        configuration.inventorySize(config.getInt("Menu.Size", 27));
    }

    @Override
    protected void configureInventory(Viewer viewer, InventoryEditor editor) {
        int totalWins = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerWins(viewer.getPlayer())
                .values().stream().reduce(0, Integer::sum);

        int totalParticipations = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerParticipations(viewer.getPlayer())
                .values().stream().reduce(0, Integer::sum);

        int playerTopWinsPosition = 0;
        int playerTopParticipationsPosition = 0;

        if (totalWins > 0) {
            playerTopWinsPosition = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerTopWinsPosition(viewer.getPlayer());
        }

        if (totalParticipations > 0) {
            playerTopParticipationsPosition = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerTopParticipationsPosition(viewer.getPlayer());
        }

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("@player", viewer.getName());
        placeholders.put("@wins", String.valueOf(totalWins));
        placeholders.put("@participations", String.valueOf(totalParticipations));
        placeholders.put("@wins_position", String.valueOf(playerTopWinsPosition));
        placeholders.put("@participations_position", String.valueOf(playerTopParticipationsPosition));

        if (config.getBoolean("Menu.Items.Profile.Enabled")) {
            ItemStack item = SimpleItemParser.parse(config.getConfigurationSection("Menu.Items.Profile"), placeholders);
            if (item != null) {
                ItemMeta meta = item.getItemMeta();

                if (meta != null && config.getBoolean("Eventos.Enabled")) {
                    List<String> lore = meta.getLore();

                    if (lore != null) {
                        boolean hasWinOrParticipation = false;

                        for (String line : config.getStringList("Eventos.List")) {
                            String[] separated = line.split(":");
                            if (separated.length < 2 || !EventoConfigFile.exists(separated[0])) {
                                continue;
                            }

                            Map<String, Integer> playerWins = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerWins(viewer.getPlayer());
                            Map<String, Integer> playerParticipations = AbsolutEventsPlugin.getInstance().getCacheManager().getPlayerParticipations(viewer.getPlayer());

                            int wins = playerWins.getOrDefault(separated[0], 0);
                            int participations = playerParticipations.getOrDefault(separated[0], 0);

                            if (config.getBoolean("Eventos.Only with wins") && wins == 0 && participations == 0) {
                                continue;
                            }

                            hasWinOrParticipation = true;
                            lore.add(
                                    config.getString("Eventos.Format", "&7@evento_name: @evento_wins / @evento_participations")
                                            .replace("@evento_name", separated[1])
                                            .replace("@evento_wins", String.valueOf(wins))
                                            .replace("@evento_participations", String.valueOf(participations))
                                            .replace("&", "§")
                            );
                        }

                        if (!hasWinOrParticipation) {
                            lore.add(config.getString("Eventos.Empty", "&7Nenhum dado.").replace("&", "§"));
                        }

                        if (config.getBoolean("Eventos.New line")) {
                            lore.add("");
                        }

                        meta.setLore(lore);
                    }
                }

                item.setItemMeta(meta);
                editor.setItem(config.getInt("Menu.Items.Profile.Slot", 13), InventoryItem.of(item));
            }
        }

        if (config.getBoolean("Menu.Items.Eventos.Enabled")) {
            InventoryItem item = InventoryItem.of(
                    SimpleItemParser.parse(config.getConfigurationSection("Menu.Items.Eventos"), placeholders)
            );
            item.defaultCallback(event -> InventoryManager.openEventoListInventory(viewer.getPlayer()));
            editor.setItem(config.getInt("Menu.Items.Eventos.Slot", 11), item);
        }

        if (config.getBoolean("Menu.Items.Top.Enabled")) {
            InventoryItem item = InventoryItem.of(
                    SimpleItemParser.parse(config.getConfigurationSection("Menu.Items.Top"), placeholders)
            );
            item.defaultCallback(event -> InventoryManager.openEventoTopInventory(viewer.getPlayer()));
            editor.setItem(config.getInt("Menu.Items.Top.Slot", 15), item);
        }
    }

    public void updateConfig() {
        config = MenuConfigFile.get("main");
    }
}
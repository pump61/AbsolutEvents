package com.absolutgg.absolutevents.inventory;

import com.absolutgg.absolutevents.inventory.utils.SimpleItemParser;
import com.absolutgg.absolutevents.manager.InventoryManager;
import com.absolutgg.absolutevents.utils.MenuConfigFile;
import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.paged.PagedInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.item.supplier.InventoryItemSupplier;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.impl.ViewerConfigurationImpl;
import com.henryfabio.minecraft.inventoryapi.viewer.impl.paged.PagedViewer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedList;
import java.util.List;

public final class EventoListInventory extends PagedInventory {

    private YamlConfiguration config = MenuConfigFile.get("eventos");

    public EventoListInventory() {
        super(
                "absolutevents.inventory.eventolist",
                "&8Eventos",
                27
        );
    }

    @Override
    protected void configureViewer(PagedViewer viewer) {
        ViewerConfigurationImpl.Paged configuration = viewer.getConfiguration();
        configuration.titleInventory(
                config.getString("Menu.Name", "&8Eventos")
                        .replace("@jogador", viewer.getName())
                        .replace("&", "§")
        );
        configuration.inventorySize(config.getInt("Menu.Size", 27));
        configuration.nextPageSlot(config.getInt("Menu.Next page slot", 26));
        configuration.previousPageSlot(config.getInt("Menu.Previous page slot", 18));
        configuration.emptyPageSlot(config.getInt("Menu.Empty page slot", 22));
        configuration.itemPageLimit(config.getInt("Menu.Item page limit", 21));
    }

    @Override
    protected List<InventoryItemSupplier> createPageItems(PagedViewer viewer) {
        List<InventoryItemSupplier> itemSuppliers = new LinkedList<>();

        ConfigurationSection section = config.getConfigurationSection("Menu.Items.Eventos");
        if (section == null) {
            return itemSuppliers;
        }

        for (String key : section.getKeys(false)) {
            InventoryItem item = InventoryItem.of(
                    SimpleItemParser.parse(section.getConfigurationSection(key), null)
            );
            itemSuppliers.add(() -> item);
        }

        return itemSuppliers;
    }

    @Override
    protected void configureInventory(Viewer viewer, InventoryEditor editor) {
        if (config.getBoolean("Menu.Items.Back.Enabled")) {
            InventoryItem item = InventoryItem.of(
                    SimpleItemParser.parse(config.getConfigurationSection("Menu.Items.Back"), null)
            );
            item.defaultCallback(event -> InventoryManager.openMainInventory(viewer.getPlayer()));
            editor.setItem(config.getInt("Menu.Items.Back.Slot", 18), item);
        }
    }

    public void updateConfig() {
        config = MenuConfigFile.get("eventos");
    }
}
package com.absolutgg.absolutevents.inventory;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.MenuConfigFile;
import com.cryptomorin.xseries.XMaterial;
import com.henryfabio.minecraft.inventoryapi.editor.InventoryEditor;
import com.henryfabio.minecraft.inventoryapi.inventory.impl.paged.PagedInventory;
import com.henryfabio.minecraft.inventoryapi.item.InventoryItem;
import com.henryfabio.minecraft.inventoryapi.item.supplier.InventoryItemSupplier;
import com.henryfabio.minecraft.inventoryapi.viewer.Viewer;
import com.henryfabio.minecraft.inventoryapi.viewer.configuration.impl.ViewerConfigurationImpl;
import com.henryfabio.minecraft.inventoryapi.viewer.impl.paged.PagedViewer;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class EventoTopInventory extends PagedInventory {

    private YamlConfiguration config = MenuConfigFile.get("top_players");
    private final Map<String, Integer> playerFilter = new java.util.HashMap<>();

    public EventoTopInventory() {
        super(
                "absolutevents.inventory.eventotop",
                "&8Eventos",
                27
        );
    }

    @Override
    protected void configureViewer(PagedViewer viewer) {
        ViewerConfigurationImpl.Paged configuration = viewer.getConfiguration();
        configuration.inventorySize(config.getInt("Menu.Size", 54));
        configuration.nextPageSlot(config.getInt("Menu.Next page slot", 50));
        configuration.previousPageSlot(config.getInt("Menu.Previous page slot", 48));
        configuration.emptyPageSlot(config.getInt("Menu.Empty page slot", 49));
        configuration.itemPageLimit(config.getInt("Menu.Item page limit", 28));

        AtomicInteger currentFilter = new AtomicInteger(playerFilter.getOrDefault(viewer.getName(), -1));
        if (currentFilter.get() == -1) {
            configuration.titleInventory(
                    ColorUtils.colorize(
                            config.getString("Filter.Names.Wins", "&8Top Vitórias")
                                    .replace("@jogador", viewer.getName())
                    )
            );
        }
        if (currentFilter.get() == 0) {
            configuration.titleInventory(
                    ColorUtils.colorize(
                            config.getString("Filter.Names.Participations", "&8Top Participações")
                                    .replace("@jogador", viewer.getName())
                    )
            );
        }
    }

    @Override
    protected List<InventoryItemSupplier> createPageItems(PagedViewer viewer) {
        List<InventoryItemSupplier> itemSuppliers = new LinkedList<>();
        AtomicInteger currentFilter = new AtomicInteger(playerFilter.getOrDefault(viewer.getName(), -1));

        if (currentFilter.get() == -1) {
            for (OfflinePlayer player : AbsolutEventsPlugin.getInstance().getCacheManager().getTopWinsMenuItems().keySet()) {
                if (player == null || player.getName() == null) {
                    continue;
                }

                ItemStack item = XMaterial.PLAYER_HEAD.parseItem();
                if (item == null) {
                    continue;
                }

                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta == null) {
                    continue;
                }

                meta.setOwner(player.getName());
                meta.setDisplayName(
                        ColorUtils.colorize(
                                config.getString("Menu.Items.Player.Name", "&e@top_player")
                                        .replace("@top_player", player.getName())
                        )
                );
                meta.setLore(AbsolutEventsPlugin.getInstance().getCacheManager().getTopWinsMenuItems().get(player));
                item.setItemMeta(meta);

                itemSuppliers.add(() -> InventoryItem.of(item));
            }
        }

        if (currentFilter.get() == 0) {
            for (OfflinePlayer player : AbsolutEventsPlugin.getInstance().getCacheManager().getTopParticipationsMenuItems().keySet()) {
                if (player == null || player.getName() == null) {
                    continue;
                }

                ItemStack item = XMaterial.PLAYER_HEAD.parseItem();
                if (item == null) {
                    continue;
                }

                SkullMeta meta = (SkullMeta) item.getItemMeta();
                if (meta == null) {
                    continue;
                }

                meta.setOwner(player.getName());
                meta.setDisplayName(
                        ColorUtils.colorize(
                                config.getString("Menu.Items.Player.Name", "&e@top_player")
                                        .replace("@top_player", player.getName())
                        )
                );
                meta.setLore(AbsolutEventsPlugin.getInstance().getCacheManager().getTopParticipationsMenuItems().get(player));
                item.setItemMeta(meta);

                itemSuppliers.add(() -> InventoryItem.of(item));
            }
        }

        return itemSuppliers;
    }

    @Override
    protected void configureInventory(Viewer viewer, InventoryEditor editor) {
        if (!config.getBoolean("Filter.Enabled")) {
            return;
        }

        AtomicInteger currentFilter = new AtomicInteger(playerFilter.getOrDefault(viewer.getName(), -1));

        List<String> lore = new ArrayList<>();
        ItemStack item = XMaterial.HOPPER.parseItem();
        if (item == null) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        meta.setDisplayName(ColorUtils.colorize("§6Filtro de ranking"));
        lore.add(ColorUtils.colorize("§7Selecione qual ranking você quer ver."));
        lore.add("");
        lore.add(getFilterFormatting(currentFilter.get(), -1) + " Vitórias");
        lore.add(getFilterFormatting(currentFilter.get(), 0) + " Participações");
        lore.add("");
        lore.add(ColorUtils.colorize("§aClique para mudar o filtro!"));

        meta.setLore(lore);
        item.setItemMeta(meta);

        editor.setItem(
                40,
                InventoryItem.of(item).defaultCallback(event -> {
                    playerFilter.put(viewer.getName(), currentFilter.incrementAndGet() > 0 ? -1 : currentFilter.get());
                    event.updateInventory();
                })
        );
    }

    @Override
    protected void update(Viewer viewer, InventoryEditor editor) {
        super.update(viewer, editor);
        configureViewer((PagedViewer) viewer);
        configureInventory(viewer, viewer.getEditor());
    }

    private String getFilterFormatting(int currentFilter, int loopFilter) {
        return currentFilter == loopFilter ? " §b▶" : "§8";
    }

    public void updateConfig() {
        config = MenuConfigFile.get("top_players");
    }
}
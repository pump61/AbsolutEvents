package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.inventory.EventoListInventory;
import com.absolutgg.absolutevents.inventory.EventoMainInventory;
import com.absolutgg.absolutevents.inventory.EventoTopInventory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class InventoryManager {

    private static EventoMainInventory mainInventory;
    private static EventoListInventory listInventory;
    private static EventoTopInventory topInventory;

    private InventoryManager() {
    }

    public static void setup() {
        mainInventory = new EventoMainInventory().init();
        listInventory = new EventoListInventory().init();
        topInventory = new EventoTopInventory().init();
    }

    public static void openMainInventory(@NotNull Player player) {
        ensureInitialized();
        mainInventory.openInventory(player);
    }

    public static void openEventoListInventory(@NotNull Player player) {
        ensureInitialized();
        listInventory.openInventory(player);
    }

    public static void openEventoTopInventory(@NotNull Player player) {
        ensureInitialized();
        topInventory.openInventory(player);
    }

    public static void reload() {
        setup();
    }

    private static void ensureInitialized() {
        if (mainInventory == null || listInventory == null || topInventory == null) {
            setup();
        }
    }
}
package com.absolutgg.absolutevents.utils;

import org.bukkit.entity.Player;

public class ActionBarUtils {

    public static void send(Player player, String message) {
        player.sendActionBar(MessageUtils.component(message));
    }
}
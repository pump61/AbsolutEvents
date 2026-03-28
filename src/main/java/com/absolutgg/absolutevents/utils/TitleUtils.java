package com.absolutgg.absolutevents.utils;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.time.Duration;

public class TitleUtils {

    public static void sendTitle(Player player, String title, String subtitle) {
        Component titleComponent = MessageUtils.component(title);
        Component subtitleComponent = MessageUtils.component(subtitle);

        Title.Times times = Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofMillis(2500),
                Duration.ofMillis(500)
        );

        player.showTitle(Title.title(titleComponent, subtitleComponent, times));
    }
}
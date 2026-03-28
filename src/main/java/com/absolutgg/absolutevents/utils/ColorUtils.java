package com.absolutgg.absolutevents.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([A-F0-9]{6})");
    private static final Pattern ANGLE_HEX_PATTERN = Pattern.compile("(?i)<#([A-F0-9]{6})>");

    private ColorUtils() {
    }

    @NotNull
    public static String colorize(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String colored = applyHex(text);
        return colored.replace('&', '§');
    }

    @NotNull
    public static List<String> colorize(@Nullable List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(colorize(line));
        }
        return colored;
    }

    @NotNull
    public static String strip(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        return colorize(text).replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }

    @NotNull
    private static String applyHex(@NotNull String text) {
        String result = replaceHexPattern(text, HEX_PATTERN);
        result = replaceHexPattern(result, ANGLE_HEX_PATTERN);
        return result;
    }

    @NotNull
    private static String replaceHexPattern(@NotNull String text, @NotNull Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            String replacement = toLegacyHex(hex);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @NotNull
    private static String toLegacyHex(@NotNull String hex) {
        StringBuilder builder = new StringBuilder("§x");
        for (char c : hex.toCharArray()) {
            builder.append('§').append(Character.toLowerCase(c));
        }
        return builder.toString();
    }
}
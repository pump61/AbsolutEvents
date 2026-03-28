package com.absolutgg.absolutevents.utils;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NumberFormatter {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    private static final Pattern REGEX_PATTERN = Pattern.compile("^(\\d+\\.?\\d*)(\\D+)");

    private NumberFormatter() {}

    private static List<String> getChars() {
        FileConfiguration config = AbsolutEventsPlugin.getInstance().getConfig();
        return config.getStringList("Formatter.Letters");
    }

    public static String decimalFormat(double number) {
        return DECIMAL_FORMAT.format(number);
    }

    public static String letterFormat(double value) {

        List<String> chars = getChars();

        int index = 0;
        double tmp;

        while ((tmp = value / 1000) >= 1 && index < chars.size() - 1) {
            value = tmp;
            index++;
        }

        return DECIMAL_FORMAT.format(value) + chars.get(index);
    }

    public static String parse(double value) {

        String type = AbsolutEventsPlugin
                .getInstance()
                .getConfig()
                .getString("Formatter.Type", "letter");

        if (type.equalsIgnoreCase("decimal")) {
            return decimalFormat(value);
        }

        return letterFormat(value);
    }

    public static double parseLetter(String text) {

        Matcher matcher = REGEX_PATTERN.matcher(text);

        if (!matcher.find()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        double amount = Double.parseDouble(matcher.group(1));
        String suffix = matcher.group(2);

        List<String> chars = getChars();

        int index = chars.indexOf(suffix.toUpperCase());

        if (index == -1) {
            return -1;
        }

        double value = amount * Math.pow(1000, index);

        return isNumberInvalid(value) ? 0 : value;
    }

    private static boolean isNumberInvalid(double value) {
        return value < 0 || Double.isInfinite(value) || Double.isNaN(value);
    }
}
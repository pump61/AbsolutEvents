package com.absolutgg.absolutevents.utils;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack itemStack;

    public ItemBuilder(@NotNull Material material) {
        this(material, 1);
    }

    public ItemBuilder(@NotNull Material material, int amount) {
        this.itemStack = new ItemStack(material, Math.max(1, amount));
    }

    public ItemBuilder(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack.clone();
    }

    @NotNull
    public static ItemBuilder of(@NotNull Material material) {
        return new ItemBuilder(material);
    }

    @NotNull
    public static ItemBuilder of(@NotNull Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    @NotNull
    public static ItemBuilder of(@NotNull ItemStack itemStack) {
        return new ItemBuilder(itemStack);
    }

    @NotNull
    public ItemBuilder amount(int amount) {
        this.itemStack.setAmount(Math.max(1, amount));
        return this;
    }

    @NotNull
    public ItemBuilder name(@Nullable String name) {
        editMeta(meta -> meta.setDisplayName(ColorUtils.colorize(name)));
        return this;
    }

    @NotNull
    public ItemBuilder lore(@Nullable List<String> lore) {
        editMeta(meta -> meta.setLore(ColorUtils.colorize(lore)));
        return this;
    }

    @NotNull
    public ItemBuilder lore(@Nullable String... lore) {
        return lore(lore == null ? null : Arrays.asList(lore));
    }

    @NotNull
    public ItemBuilder customModelData(int customModelData) {
        editMeta(meta -> meta.setCustomModelData(customModelData));
        return this;
    }

    @NotNull
    public ItemBuilder unbreakable(boolean unbreakable) {
        editMeta(meta -> meta.setUnbreakable(unbreakable));
        return this;
    }

    @NotNull
    public ItemBuilder enchant(@NotNull Enchantment enchantment, int level) {
        editMeta(meta -> meta.addEnchant(enchantment, level, true));
        return this;
    }

    @NotNull
    public ItemBuilder flag(@NotNull ItemFlag... flags) {
        editMeta(meta -> meta.addItemFlags(flags));
        return this;
    }

    @NotNull
    public ItemBuilder flags(@NotNull List<ItemFlag> flags) {
        editMeta(meta -> meta.addItemFlags(flags.toArray(new ItemFlag[0])));
        return this;
    }

    @NotNull
    public ItemBuilder removeFlag(@NotNull ItemFlag... flags) {
        editMeta(meta -> meta.removeItemFlags(flags));
        return this;
    }

    @NotNull
    public ItemBuilder glow() {
        editMeta(meta -> {
            Enchantment enchantment = Enchantment.UNBREAKING;
            meta.addEnchant(enchantment, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
        return this;
    }

    @NotNull
    public ItemBuilder type(@NotNull Material material) {
        this.itemStack.setType(material);
        return this;
    }

    @NotNull
    public ItemStack build() {
        return this.itemStack.clone();
    }

    private void editMeta(@NotNull java.util.function.Consumer<ItemMeta> consumer) {
        ItemMeta meta = this.itemStack.getItemMeta();
        if (meta == null) {
            return;
        }

        consumer.accept(meta);
        this.itemStack.setItemMeta(meta);
    }
}
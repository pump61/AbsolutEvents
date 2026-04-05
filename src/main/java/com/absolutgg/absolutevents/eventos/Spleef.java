package com.absolutgg.absolutevents.eventos;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.events.PlayerLoseEvent;
import com.absolutgg.absolutevents.discord.DiscordWebhookManager;
import com.absolutgg.absolutevents.listeners.eventos.SpleefListener;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.cryptomorin.xseries.XItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class Spleef extends Evento {

    private final YamlConfiguration config;
    private final SpleefListener listener = new SpleefListener();

    private final int delay;
    private boolean canBreak;
    private boolean ending;

    public Spleef(YamlConfiguration config) {
        super(config);
        this.config = config;
        this.delay = config.getInt("Evento.Delay");
        this.canBreak = false;
        this.ending = false;
    }

    @Override
    public void start() {
        ending = false;
        canBreak = false;

        listener.getLastMove().clear();

        Bukkit.getPluginManager().registerEvents(listener, AbsolutEventsPlugin.getInstance());
        listener.setEvento();

        rebuildArena();

        for (Player player : new ArrayList<>(getPlayers())) {
            applyConfiguredItems(player);
        }

        sendMessagesToParticipantsAndSpectators(
                config.getStringList("Messages.Enabling breaking"),
                "@time",
                String.valueOf(delay)
        );

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                () -> {
                    if (!isHappening() || ending) {
                        return;
                    }

                    allowBreakBlocks();

                    for (Player player : getPlayers()) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.HASTE,
                                Integer.MAX_VALUE,
                                2,
                                false,
                                false,
                                false
                        ));
                    }

                    sendMessagesToParticipantsAndSpectators(
                            config.getStringList("Messages.Breaking allowed"),
                            null,
                            null
                    );
                },
                Math.max(1L, delay * 20L)
        );
    }

    @Override
    public void winner(Player player) {
        if (ending) {
            return;
        }

        ending = true;

        List<Player> losers = new ArrayList<>(getPlayers());
        losers.removeIf(target -> target.getUniqueId().equals(player.getUniqueId()));

        for (String message : config.getStringList("Messages.Winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message
                            .replace("@winner", player.getName())
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }

        DiscordWebhookManager.sendPlayerWinner(player.getName(), config.getString("Evento.Title"));

        setWinner(player);

        TournamentStatsManager.getInstance().addWin(player.getUniqueId());

        if (AbsolutEventsPlugin.getInstance().getLeagueManager() != null) {
            AbsolutEventsPlugin.getInstance().getLeagueManager().handleSoloWin(
                    player,
                    losers,
                    "spleef"
            );
        }

        for (String command : config.getStringList("Rewards.Commands")) {
            executeConsoleCommand(player, command.replace("@winner", player.getName()));
        }

        stop();
    }

    public void noWinner() {
        if (ending) {
            return;
        }

        ending = true;

        for (String message : config.getStringList("Messages.No winner")) {
            Bukkit.broadcastMessage(ColorUtils.colorize(
                    message.replace("@name", config.getString("Evento.Title"))
            ));
        }

        stop();
    }

    @Override
    public void stop() {
        canBreak = false;
        ending = false;

        rebuildArena();

        for (Player player : new ArrayList<>(getPlayers())) {
            clearConfiguredItems(player);
            player.removePotionEffect(PotionEffectType.HASTE);
        }

        listener.getLastMove().clear();

        HandlerList.unregisterAll(listener);
        removePlayers();
    }

    public void eliminate(Player player) {
        if (!getPlayers().contains(player) || ending) {
            return;
        }

        player.sendMessage(ColorUtils.colorize(
                AbsolutEventsPlugin.getInstance()
                        .getConfig()
                        .getString("Messages.Eliminated", "&cVocê foi eliminado.")
        ));

        remove(player);
        listener.getLastMove().remove(player.getUniqueId());

        Bukkit.getPluginManager().callEvent(
                new PlayerLoseEvent(
                        player,
                        config.getString("filename", "").replace(".yml", ""),
                        getType()
                )
        );

        checkWinState();
    }

    private void checkWinState() {
        if (!isHappening() || ending) {
            return;
        }

        if (getPlayers().isEmpty()) {
            noWinner();
            return;
        }

        if (getPlayers().size() == 1) {
            winner(getPlayers().get(0));
        }
    }

    private void rebuildArena() {
        fillArena();

        Bukkit.getScheduler().runTaskLater(
                AbsolutEventsPlugin.getInstance(),
                this::fillArena,
                2L
        );
    }

    private void fillArena() {
        World world = Bukkit.getWorld(config.getString("Locations.Pos1.world"));
        if (world == null) {
            return;
        }

        int x1 = (int) config.getDouble("Locations.Pos1.x");
        int y1 = (int) config.getDouble("Locations.Pos1.y");
        int z1 = (int) config.getDouble("Locations.Pos1.z");

        int x2 = (int) config.getDouble("Locations.Pos2.x");
        int y2 = (int) config.getDouble("Locations.Pos2.y");
        int z2 = (int) config.getDouble("Locations.Pos2.z");

        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(Material.SNOW_BLOCK, false);
                }
            }
        }
    }

    private void applyConfiguredItems(Player player) {
        clearConfiguredItems(player);

        ItemStack helmet = getItem("Itens.Helmet");
        ItemStack chestplate = getItem("Itens.Chestplate");
        ItemStack leggings = getItem("Itens.Leggings");
        ItemStack boots = getItem("Itens.Boots");

        if (helmet != null) {
            player.getInventory().setHelmet(helmet);
        }

        if (chestplate != null) {
            player.getInventory().setChestplate(chestplate);
        }

        if (leggings != null) {
            player.getInventory().setLeggings(leggings);
        }

        if (boots != null) {
            player.getInventory().setBoots(boots);
        }

        ConfigurationSection inv = config.getConfigurationSection("Itens.Inventory");
        if (inv != null) {
            for (String slot : inv.getKeys(false)) {
                ItemStack item = getItem("Itens.Inventory." + slot);

                if (item != null && item.getType().name().contains("SHOVEL")) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
                        item.setItemMeta(meta);
                    }
                }

                if (item != null) {
                    player.getInventory().setItem(Integer.parseInt(slot), item);
                }
            }
        }

        player.updateInventory();
    }

    private ItemStack getItem(String path) {
        if (!config.isConfigurationSection(path)) {
            return null;
        }

        ConfigurationSection sec = config.getConfigurationSection(path);
        if (sec == null || sec.getKeys(false).isEmpty()) {
            return null;
        }

        ItemStack item = XItemStack.deserialize(sec);

        if (item == null || item.getType() == Material.AIR || item.getType() == Material.BARRIER) {
            return null;
        }

        return item;
    }

    private void clearConfiguredItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.updateInventory();
    }

    private void sendMessagesToParticipantsAndSpectators(List<String> messages, String ph, String value) {
        for (Player p : getPlayers()) {
            send(p, messages, ph, value);
        }

        for (Player p : getSpectators()) {
            send(p, messages, ph, value);
        }
    }

    private void send(Player p, List<String> msgs, String ph, String value) {
        for (String msg : msgs) {
            String parsed = msg.replace("@name", config.getString("Evento.Title"));

            if (ph != null && value != null) {
                parsed = parsed.replace(ph, value);
            }

            p.sendMessage(ColorUtils.colorize(parsed));
        }
    }

    public boolean canNotBreakBlocks() {
        return !canBreak;
    }

    public void allowBreakBlocks() {
        canBreak = true;
    }

    public double getArenaMinY() {
        return Math.min(
                config.getDouble("Locations.Pos1.y"),
                config.getDouble("Locations.Pos2.y")
        );
    }

    @Override
    public YamlConfiguration getConfig() {
        return config;
    }
}
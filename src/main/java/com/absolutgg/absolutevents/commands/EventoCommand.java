package com.absolutgg.absolutevents.commands;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.hooks.BungeecordHook;
import com.absolutgg.absolutevents.manager.InventoryManager;
import com.absolutgg.absolutevents.manager.InventorySerializer;
import com.absolutgg.absolutevents.manager.UpdateChecker;
import com.absolutgg.absolutevents.manager.UpdateDownloader;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.absolutgg.absolutevents.utils.NumberFormatter;
import com.cryptomorin.xseries.XItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class EventoCommand implements CommandExecutor, TabCompleter {

    private static final Map<Player, YamlConfiguration> SETUP = new HashMap<>();

    private static final List<String> PLAYER_COMMANDS = Arrays.asList(
            "assistir",
            "camarote",
            "spectate",
            "sair",
            "leave",
            "ajuda",
            "help"
    );

    private static final List<String> ADMIN_COMMANDS = Arrays.asList(
            "iniciar",
            "start",
            "parar",
            "cancelar",
            "stop",
            "forcestart",
            "kick",
            "criarconfig",
            "createconfig",
            "setup",
            "reload",
            "backup",
            "backupinfo",
            "update"
    );

    private static final List<String> SETUP_ACTIONS = Arrays.asList(
            "entrada",
            "entrance",
            "saida",
            "exit",
            "espera",
            "fila",
            "lobby",
            "camarote",
            "assistir",
            "spectator",
            "pos",
            "pos1",
            "pos2",
            "pos3",
            "pos4",
            "pos5",
            "pos6",
            "centro",
            "center",
            "kit",
            "item",
            "itens",
            "startpos1",
            "startpos2",
            "finishpos1",
            "finishpos2",
            "checkpoint",
            "finish",
            "sair",
            "leave",
            "quit"
    );

    private static final List<String> KIT_TYPES = Arrays.asList(
            "normal",
            "last",
            "lastfight"
    );

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("evento")) {
            return false;
        }

        if (args.length == 0) {
            return handleDefault(sender);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "sair":
            case "leave":
                return handleLeave(sender);

            case "assistir":
            case "camarote":
            case "spectate":
                return handleSpectate(sender);

            case "parar":
            case "cancelar":
            case "stop":
                return handleStop(sender);

            case "reload":
                return handleReload(sender);

            case "iniciar":
            case "start":
                return handleStart(sender, args);

            case "forcestart":
                return handleForceStart(sender);

            case "ajuda":
            case "help":
                return handleHelp(sender);

            case "kick":
                return handleKick(sender, args);

            case "criarconfig":
            case "createconfig":
                return handleCreateConfig(sender, args);

            case "setup":
                return handleSetup(sender, args);

            case "backup":
                return handleBackup(sender, args);

            case "backupinfo":
                return handleBackupInfo(sender, args);

            case "update":
                return handleUpdate(sender, args);

            default:
                return handleChatEventCommand(sender, args);
        }
    }

    private boolean handleDefault(CommandSender sender) {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() == null) {
            if (sender instanceof Player player
                    && AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Enable GUI")) {
                InventoryManager.openMainInventory(player);

                if (!AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Show commands")) {
                    return true;
                }
            }

            sendHelp(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();

        if (!evento.isOpen()) {
            sender.sendMessage(color(message("Messages.Closed")));
            return true;
        }

        if (!player.hasPermission(evento.getPermission())) {
            sender.sendMessage(color(message("Messages.Not allowed")));
            return true;
        }

        if (evento.getPlayers().contains(player)) {
            sender.sendMessage(color(message("Messages.Already joined")));
            return true;
        }

        if (evento.getSpectators().contains(player)) {
            sender.sendMessage(color(message("Messages.Already spectator")));
            return true;
        }

        if (evento.requireEmptyInventory()) {
            if (!isCompletelyEmpty(player)) {
                sender.sendMessage(color(message("Messages.Empty inventory")));
                return true;
            }
        }

        evento.joinBungeecord(player);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            sender.sendMessage(color(message("Messages.No event")));
            return true;
        }

        if (!evento.getPlayers().contains(player) && !evento.getSpectators().contains(player)) {
            sender.sendMessage(color(message("Messages.Not joined")));
            return true;
        }

        evento.leaveBungeecord(player);
        return true;
    }

    private boolean handleSpectate(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            sender.sendMessage(color(message("Messages.No event")));
            return true;
        }

        if (!player.hasPermission(evento.getPermission())) {
            sender.sendMessage(color(message("Messages.Not allowed")));
            return true;
        }

        if (!evento.isSpectatorAllowed()) {
            sender.sendMessage(color(message("Messages.No spectator")));
            return true;
        }

        if (evento.getPlayers().contains(player)) {
            sender.sendMessage(color(message("Messages.Already joined")));
            return true;
        }

        if (evento.getSpectators().contains(player)) {
            sender.sendMessage(color(message("Messages.Already spectator")));
            return true;
        }

        if (!isCompletelyEmpty(player)) {
            sender.sendMessage(color(message("Messages.Empty inventory")));
            return true;
        }

        evento.spectateBungeecord(player);
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        var eventoChat = AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento();

        if (evento == null && eventoChat == null) {
            sender.sendMessage(color(message("Messages.No event")));
            return true;
        }

        YamlConfiguration config;

        if (evento != null) {
            config = evento.getConfig();
            evento.stop();

            if (AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Enabled")
                    && config.getString("Locations.Server") != null) {
                BungeecordHook.stopEvento("cancelled");
            }
        } else {
            config = eventoChat.getConfig();
            eventoChat.stop();
        }

        for (String msg : config.getStringList("Messages.Cancelled")) {
            Bukkit.broadcastMessage(color(msg.replace("@name", config.getString("Evento.Title"))));
        }

        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        AbsolutEventsPlugin.getInstance().reloadConfig();
        AbsolutEventsPlugin.getInstance().getCacheManager().updateCache();
        InventoryManager.reload();

        sender.sendMessage(color(message("Messages.Reloaded")));
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length == 1) {
            sender.sendMessage(color(
                    message("Messages.Missing arguments").replace("@args", "iniciar <evento>")
            ));
            return true;
        }

        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() != null
                || AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento() != null) {
            sender.sendMessage(color(message("Messages.Already happening")));
            return true;
        }

        String eventName = args[1].toLowerCase(Locale.ROOT);
        if (!EventoConfigFile.exists(eventName)) {
            sender.sendMessage(color(message("Messages.Invalid event")));
            return true;
        }

        YamlConfiguration config = EventoConfigFile.get(eventName);
        EventoType type = EventoType.getEventoType(config.getString("Evento.Type"));

        double reward = -1;
        if (args.length >= 3) {
            reward = NumberFormatter.parseLetter(args[2]);
        }

        if (EventoType.isEventoChat(type)) {
            boolean started = AbsolutEventsPlugin.getInstance()
                    .getEventoChatManager()
                    .startEvento(type, config, reward);

            if (!started) {
                sender.sendMessage(color(
                        message("Messages.Missing dependency").replace("@dependency", "Vault")
                ));
            }
        } else {
            boolean started = AbsolutEventsPlugin.getInstance()
                    .getEventoManager()
                    .startEvento(type, config, reward);

            if (!started) {
                sender.sendMessage(color(
                        message("Messages.Not configurated").replace("@name", eventName)
                ));
            }
        }

        return true;
    }

    private boolean handleForceStart(CommandSender sender) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        var eventoChat = AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento();

        if (evento == null) {
            if (eventoChat == null) {
                sender.sendMessage(color(message("Messages.No event")));
            } else {
                eventoChat.forceStart();
            }
            return true;
        }

        if (!evento.isOpen()) {
            sender.sendMessage(color(message("Messages.Closed")));
            return true;
        }

        evento.forceStart();
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sendHelp(sender);
        return true;
    }

    private boolean handleKick(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length == 1) {
            sender.sendMessage(color(
                    message("Messages.Missing arguments").replace("@args", "kick <jogador>")
            ));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color(message("Messages.Offline")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        var eventoChat = AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento();

        if (evento == null) {
            if (eventoChat == null) {
                sender.sendMessage(color(message("Messages.No event")));
                return true;
            }

            if (!eventoChat.getPlayers().contains(target)) {
                sender.sendMessage(color(message("Messages.Player not joined")));
                return true;
            }

            eventoChat.leave(target);
            sender.sendMessage(color(message("Messages.Player kicked")));
            target.sendMessage(color(message("Messages.Kicked")));
            return true;
        }

        if (!evento.getPlayers().contains(target)) {
            sender.sendMessage(color(message("Messages.Player not joined")));
            return true;
        }

        evento.leave(target);
        sender.sendMessage(color(message("Messages.Player kicked")));
        target.sendMessage(color(message("Messages.Kicked")));
        return true;
    }

    private boolean handleCreateConfig(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length == 1) {
            sender.sendMessage(color(
                    message("Messages.Missing arguments").replace("@args", "criarconfig <evento>")
            ));
            return true;
        }

        String eventName = args[1].toLowerCase(Locale.ROOT);

        if (AbsolutEventsPlugin.getInstance().getResource("eventos/" + eventName + ".yml") == null) {
            sender.sendMessage(color(message("Messages.Invalid event")));
            return true;
        }

        if (EventoConfigFile.exists(eventName)) {
            sender.sendMessage(color(message("Messages.Configuration already exists")));
            return true;
        }

        EventoConfigFile.create(eventName);
        AbsolutEventsPlugin.getInstance().refreshCaches();

        sender.sendMessage(color(
                message("Messages.Configuration created").replace("@file", eventName + ".yml")
        ));
        return true;
    }

    private boolean handleSetup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length == 1) {
            if (SETUP.containsKey(player)) {
                YamlConfiguration current = SETUP.get(player);
                if (isMontaria(current)) {
                    sendMontariaSetupHelp(player, current);
                } else if (isSign(current)) {
                    sendSignSetupHelp(player, current);
                } else if (isQuiz(current)) {
                    sendQuizSetupHelp(player, current);
                } else if (isBattleRoyale(current)) {
                    sendBattleRoyaleSetupHelp(player, current);
                } else {
                    sendDefaultSetupHelp(player, current);
                }
            } else {
                sender.sendMessage(color(
                        message("Messages.Missing arguments").replace("@args", "setup <evento>")
                ));
            }
            return true;
        }

        if (!SETUP.containsKey(player)) {
            String eventName = args[1].toLowerCase(Locale.ROOT);

            if (!EventoConfigFile.exists(eventName)) {
                sender.sendMessage(color(message("Messages.Invalid event")));
                return true;
            }

            YamlConfiguration config = EventoConfigFile.get(eventName);
            SETUP.put(player, config);

            if (isMontaria(config)) {
                sendMontariaSetupHelp(player, config);
            } else if (isSign(config)) {
                sendSignSetupHelp(player, config);
            } else if (isQuiz(config)) {
                sendQuizSetupHelp(player, config);
            } else {
                sendDefaultSetupHelp(player, config);
            }
            return true;
        }

        YamlConfiguration settings = SETUP.get(player);
        String action = args[1].toLowerCase(Locale.ROOT);

        String currentType = settings.getString("Evento.Type", "").toLowerCase(Locale.ROOT);
        String currentFileName = settings.getString("filename", "").replace(".yml", "").toLowerCase(Locale.ROOT);

        if (action.equals(currentFileName) || action.equals(currentType)) {
            if (isMontaria(settings)) {
                sendMontariaSetupHelp(player, settings);
            } else if (isSign(settings)) {
                sendSignSetupHelp(player, settings);
            } else if (isQuiz(settings)) {
                sendQuizSetupHelp(player, settings);
            } else {
                sendDefaultSetupHelp(player, settings);
            }
            return true;
        }

        switch (action) {
            case "entrada":
            case "entrance":
                savePlayerLocation(settings, player, "Entrance");
                return true;

            case "centro":
            case "center":
                savePlayerLocation(settings, player, "Center");
                return true;

            case "saida":
            case "exit":
                savePlayerLocation(settings, player, "Exit");
                return true;

            case "espera":
            case "fila":
            case "lobby":
                savePlayerLocation(settings, player, "Lobby");
                return true;

            case "camarote":
            case "assistir":
            case "spectator":
                savePlayerLocation(settings, player, "Spectator");
                return true;

            case "pos":
                return handleSetupPosTool(sender, player, settings);

            case "kit":
            case "item":
            case "itens":
                return handleSetupItems(sender, player, settings, args);

            case "startpos1":
                return handleMontariaPosition(sender, player, settings, "Race.StartPos1", "StartPos1", true);

            case "startpos2":
                return handleMontariaPosition(sender, player, settings, "Race.StartPos2", "StartPos2", true);

            case "finishpos1":
                return handleMontariaPosition(sender, player, settings, "Race.FinishPos1", "FinishPos1", false);

            case "finishpos2":
                return handleMontariaPosition(sender, player, settings, "Race.FinishPos2", "FinishPos2", false);

            case "checkpoint":
                if (isMontaria(settings)) {
                    return handleMontariaCheckpoint(sender, player, settings, args);
                }
                if (isSign(settings)) {
                    return handleSignCheckpoint(sender, player, settings);
                }
                sender.sendMessage(color(message("Messages.Unknown argument")));
                return true;

            case "finish":
                if (isSign(settings)) {
                    return handleSignFinish(sender, player, settings);
                }
                sender.sendMessage(color(message("Messages.Unknown argument")));
                return true;

            case "sair":
            case "leave":
            case "quit":
                SETUP.remove(player);
                sender.sendMessage(color(
                        message("Messages.Exit setup").replace("@event", settings.getString("Evento.Title"))
                ));
                return true;

            default:
                if (action.startsWith("pos")) {
                    return handleDirectPosition(sender, player, settings, action);
                }

                sender.sendMessage(color(message("Messages.Unknown argument")));
                return true;
        }
    }

    private boolean handleSetupPosTool(CommandSender sender, Player player, YamlConfiguration settings) {
        if (!SETUP.get(player).isSet("Locations.Pos1")
                && EventoType.getEventoType(settings.getString("Evento.Type")) != EventoType.BATTLE_ROYALE) {
            sender.sendMessage(color(
                    message("Messages.Not needed").replace("@name", settings.getString("Evento.Title"))
            ));
            return true;
        }

        givePosTools(player, settings);

        sender.sendMessage(color(
                message("Messages.Give axe").replace("@name", settings.getString("Evento.Title"))
        ));
        return true;
    }

    private boolean handleDirectPosition(CommandSender sender, Player player, YamlConfiguration settings, String action) {
        String position = "Pos" + action.replace("pos", "");

        if (!SETUP.get(player).isSet("Locations." + position)
                && EventoType.getEventoType(settings.getString("Evento.Type")) != EventoType.BATTLE_ROYALE) {
            sender.sendMessage(color(
                    message("Messages.Not needed").replace("@name", settings.getString("Evento.Title"))
            ));
            return true;
        }

        settings.set("Locations." + position, "");
        settings.set("Locations." + position + ".world", player.getLocation().getWorld().getName());
        settings.set("Locations." + position + ".x", player.getLocation().getX());
        settings.set("Locations." + position + ".y", player.getLocation().getY());
        settings.set("Locations." + position + ".z", player.getLocation().getZ());
        settings.set("Locations." + position + ".Yaw", player.getLocation().getYaw());
        settings.set("Locations." + position + ".Pitch", player.getLocation().getPitch());

        return saveSetupConfig(sender, player, settings, position);
    }

    private boolean handleSetupItems(CommandSender sender, Player player, YamlConfiguration settings, String[] args) {
        if (!SETUP.get(player).isSet("Itens")) {
            sender.sendMessage(color(
                    message("Messages.Not needed kit").replace("@name", settings.getString("Evento.Title"))
            ));
            return true;
        }

        if (args.length == 2 && SETUP.get(player).isSet("Itens.Normal")) {
            sender.sendMessage(color(
                    message("Messages.Multiple kits").replace("@name", settings.getString("Evento.Title"))
            ));
            return true;
        }

        if (args.length >= 3 && SETUP.get(player).isSet("Itens.Normal")) {
            if (args[2].equalsIgnoreCase("normal")) {
                saveKit(player, settings, "Itens.Normal");
                return saveSetupKit(sender, settings);
            }

            if (args[2].equalsIgnoreCase("last") || args[2].equalsIgnoreCase("lastfight")) {
                saveKit(player, settings, "Itens.Last fight");
                return saveSetupKit(sender, settings);
            }
        }

        saveDefaultKit(player, settings);
        return saveSetupKit(sender, settings);
    }

    private boolean handleMontariaPosition(CommandSender sender, Player player, YamlConfiguration settings, String path, String displayName, boolean withYawPitch) {
        if (!isMontaria(settings)) {
            sender.sendMessage(color(message("Messages.Unknown argument")));
            return true;
        }

        settings.set(path + ".world", player.getLocation().getWorld().getName());
        settings.set(path + ".x", player.getLocation().getX());
        settings.set(path + ".y", player.getLocation().getY());
        settings.set(path + ".z", player.getLocation().getZ());

        if (withYawPitch) {
            settings.set(path + ".Yaw", player.getLocation().getYaw());
            settings.set(path + ".Pitch", player.getLocation().getPitch());
        }

        try {
            EventoConfigFile.save(settings);
            SETUP.put(player, settings);
        } catch (IOException exception) {
            sender.sendMessage(color(
                    message("Messages.Error")
                            .replace("@name", settings.getString("Evento.Title"))
            ));
            exception.printStackTrace();
            return true;
        }

        sender.sendMessage(color("&aPosição &f" + displayName + " &asalva no evento &f" + settings.getString("Evento.Title")));
        return true;
    }

    private boolean handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length >= 2 && args[1].equalsIgnoreCase("confirm")) {
            UpdateDownloader.downloadLatestRelease(sender);
            return true;
        }

        sender.sendMessage("§e[AbsolutEvents] Verificando updates no GitHub...");

        UpdateChecker.getUpdateInfoAsync(true).thenAccept(info -> {
            if (info == null) {
                sender.sendMessage("§c[AbsolutEvents] Não foi possível verificar atualizações.");
                return;
            }

            UpdateChecker.sendUpdateInfo(sender, info);
        }).exceptionally(throwable -> {
            sender.sendMessage("§c[AbsolutEvents] Ocorreu um erro ao verificar atualizações.");
            AbsolutEventsPlugin.getInstance().getLogger().warning(
                    "Falha ao executar /evento update: " + throwable.getMessage()
            );
            return null;
        });

        return true;
    }

    private boolean isBattleRoyale(YamlConfiguration config) {
        return config.getString("Evento.Type", "").equalsIgnoreCase("battleroyale");
    }

    private void sendBattleRoyaleSetupHelp(Player player, YamlConfiguration config) {
        player.sendMessage(color("&6[BattleRoyale Setup] &f" + config.getString("Evento.Title")));
        player.sendMessage(color("&f1. Defina o lobby: &e/evento setup lobby"));
        player.sendMessage(color("&f2. Defina a entrada: &e/evento setup entrance"));
        player.sendMessage(color("&f3. Defina o spectator: &e/evento setup spectator"));
        player.sendMessage(color("&f4. Defina a saída: &e/evento setup exit"));
        player.sendMessage(color("&f5. Defina o centro da zona: &e/evento setup centro"));
        player.sendMessage(color("&f6. Se usar refill de baús, defina a área dos baús: &e/evento setup pos1 &7e &e/evento setup pos2"));
        player.sendMessage(color("&f7. Se usar múltiplos spawns, os jogadores começarão em &epos3+ &fquando refill estiver ativo."));
        player.sendMessage(color("&f8. Sair do setup: &e/evento setup sair"));
    }

    private boolean handleMontariaCheckpoint(CommandSender sender, Player player, YamlConfiguration settings, String[] args) {
        if (!isMontaria(settings)) {
            sender.sendMessage(color(message("Messages.Unknown argument")));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(color("&cUse: /evento setup checkpoint <add|pos1|pos2> <nome>"));
            return true;
        }

        String sub = args[2].toLowerCase(Locale.ROOT);

        if (sub.equals("add")) {
            if (args.length < 4) {
                sender.sendMessage(color("&cUse: /evento setup checkpoint add <nome>"));
                return true;
            }

            String checkpointName = args[3];
            settings.set("Checkpoints." + checkpointName, new HashMap<>());

            try {
                EventoConfigFile.save(settings);
                SETUP.put(player, settings);
            } catch (IOException exception) {
                sender.sendMessage(color(
                        message("Messages.Error")
                                .replace("@name", settings.getString("Evento.Title"))
                ));
                exception.printStackTrace();
                return true;
            }

            sender.sendMessage(color("&aCheckpoint &f" + checkpointName + " &acriado."));
            return true;
        }

        if (sub.equals("pos1") || sub.equals("pos2")) {
            if (args.length < 4) {
                sender.sendMessage(color("&cUse: /evento setup checkpoint " + sub + " <nome>"));
                return true;
            }

            String checkpointName = args[3];
            String base = "Checkpoints." + checkpointName + "." + (sub.equals("pos1") ? "Pos1" : "Pos2");

            settings.set(base + ".world", player.getLocation().getWorld().getName());
            settings.set(base + ".x", player.getLocation().getX());
            settings.set(base + ".y", player.getLocation().getY());
            settings.set(base + ".z", player.getLocation().getZ());

            try {
                EventoConfigFile.save(settings);
                SETUP.put(player, settings);
            } catch (IOException exception) {
                sender.sendMessage(color(
                        message("Messages.Error")
                                .replace("@name", settings.getString("Evento.Title"))
                ));
                exception.printStackTrace();
                return true;
            }

            sender.sendMessage(color("&a" + sub.toUpperCase(Locale.ROOT) + " do checkpoint &f" + checkpointName + " &asalva."));
            return true;
        }

        sender.sendMessage(color("&cUse: /evento setup checkpoint <add|pos1|pos2> <nome>"));
        return true;
    }

    private boolean handleSignCheckpoint(CommandSender sender, Player player, YamlConfiguration settings) {
        if (!isSign(settings)) {
            sender.sendMessage(color(message("Messages.Unknown argument")));
            return true;
        }

        ConfigurationSection section = settings.getConfigurationSection("Checkpoints");
        int nextId = 1;
        if (section != null) {
            nextId = section.getKeys(false).size() + 1;
        }

        String base = "Checkpoints." + nextId;
        settings.set(base + ".world", player.getLocation().getWorld().getName());
        settings.set(base + ".x", player.getLocation().getBlockX());
        settings.set(base + ".y", player.getLocation().getBlockY());
        settings.set(base + ".z", player.getLocation().getBlockZ());

        try {
            EventoConfigFile.save(settings);
            SETUP.put(player, settings);
        } catch (IOException exception) {
            sender.sendMessage(color(
                    message("Messages.Error")
                            .replace("@name", settings.getString("Evento.Title"))
            ));
            exception.printStackTrace();
            return true;
        }

        sender.sendMessage(color("&aCheckpoint &f#" + nextId + " &asalvo no evento &f" + settings.getString("Evento.Title")));
        return true;
    }

    private boolean handleSignFinish(CommandSender sender, Player player, YamlConfiguration settings) {
        if (!isSign(settings)) {
            sender.sendMessage(color(message("Messages.Unknown argument")));
            return true;
        }

        settings.set("Finish.world", player.getLocation().getWorld().getName());
        settings.set("Finish.x", player.getLocation().getBlockX());
        settings.set("Finish.y", player.getLocation().getBlockY());
        settings.set("Finish.z", player.getLocation().getBlockZ());

        try {
            EventoConfigFile.save(settings);
            SETUP.put(player, settings);
        } catch (IOException exception) {
            sender.sendMessage(color(
                    message("Messages.Error")
                            .replace("@name", settings.getString("Evento.Title"))
            ));
            exception.printStackTrace();
            return true;
        }

        sender.sendMessage(color("&aFinal salvo no evento &f" + settings.getString("Evento.Title")));
        return true;
    }

    private void savePlayerLocation(YamlConfiguration settings, Player player, String locationKey) {
        settings.set("Locations." + locationKey + ".world", player.getLocation().getWorld().getName());
        settings.set("Locations." + locationKey + ".x", player.getLocation().getX());
        settings.set("Locations." + locationKey + ".y", player.getLocation().getY());
        settings.set("Locations." + locationKey + ".z", player.getLocation().getZ());
        settings.set("Locations." + locationKey + ".Yaw", player.getLocation().getYaw());
        settings.set("Locations." + locationKey + ".Pitch", player.getLocation().getPitch());

        try {
            EventoConfigFile.save(settings);
            SETUP.put(player, settings);

            player.sendMessage(color(
                    message("Messages.Saved")
                            .replace("@name", settings.getString("Evento.Title"))
                            .replace("@position", locationKey)
            ));
        } catch (IOException exception) {
            player.sendMessage(color(
                    message("Messages.Error")
                            .replace("@name", settings.getString("Evento.Title"))
            ));
            exception.printStackTrace();
        }
    }

    private boolean saveSetupConfig(CommandSender sender, Player player, YamlConfiguration settings, String posName) {
        try {
            EventoConfigFile.save(settings);
            SETUP.put(player, settings);
        } catch (IOException exception) {
            sender.sendMessage(color(
                    message("Messages.Error")
                            .replace("@name", settings.getString("Evento.Title"))
            ));
            exception.printStackTrace();
            return true;
        }

        sender.sendMessage(color(
                message("Messages.Saved")
                        .replace("@name", settings.getString("Evento.Title"))
                        .replace("@position", posName)
        ));
        return true;
    }

    private boolean saveSetupKit(CommandSender sender, YamlConfiguration settings) {
        try {
            EventoConfigFile.save(settings);
        } catch (IOException exception) {
            sender.sendMessage(color(
                    message("Messages.Error")
                            .replace("@name", settings.getString("Evento.Title"))
            ));
            exception.printStackTrace();
            return true;
        }

        sender.sendMessage(color(
                message("Messages.Saved kit")
                        .replace("@name", settings.getString("Evento.Title"))
        ));
        return true;
    }

    private void givePosTools(Player player, YamlConfiguration settings) {
        ItemStack axe = new ItemStack(Material.STONE_AXE, 1);
        ItemMeta axeMeta = axe.getItemMeta();
        if (axeMeta != null) {
            axeMeta.setDisplayName("§6Machado de Posições");
            axeMeta.setLore(Arrays.asList(
                    "§7* Clique esquerdo para definir a primeira posição.",
                    "§7* Clique direito para definir a segunda posição."
            ));
            axe.setItemMeta(axeMeta);
        }
        player.getInventory().addItem(axe);

        if (settings.isSet("Locations.Pos3")
                && EventoType.getEventoType(settings.getString("Evento.Type")) != EventoType.BATTLE_ROYALE) {

            ItemStack hoe = new ItemStack(Material.STONE_HOE, 1);
            ItemMeta hoeMeta = hoe.getItemMeta();
            if (hoeMeta != null) {
                hoeMeta.setDisplayName("§6Enxada de Posições");
                hoeMeta.setLore(Arrays.asList(
                        "§7* Clique esquerdo para definir a terceira posição.",
                        "§7* Clique direito para definir a quarta posição."
                ));
                hoe.setItemMeta(hoeMeta);
            }
            player.getInventory().addItem(hoe);
        }
    }

    private void saveKit(Player player, YamlConfiguration settings, String root) {
        settings.set(root + ".Armor.Helmet", "");
        settings.set(root + ".Armor.Helmet.a", "temp");
        settings.set(root + ".Armor.Chestplate", "");
        settings.set(root + ".Armor.Chestplate.a", "temp");
        settings.set(root + ".Armor.Leggings", "");
        settings.set(root + ".Armor.Leggings.a", "temp");
        settings.set(root + ".Armor.Boots", "");
        settings.set(root + ".Armor.Boots.a", "temp");

        ConfigurationSection helmet = settings.getConfigurationSection(root + ".Armor.Helmet");
        ConfigurationSection chest = settings.getConfigurationSection(root + ".Armor.Chestplate");
        ConfigurationSection legs = settings.getConfigurationSection(root + ".Armor.Leggings");
        ConfigurationSection boots = settings.getConfigurationSection(root + ".Armor.Boots");

        if (player.getInventory().getHelmet() != null && helmet != null) {
            XItemStack.serialize(player.getInventory().getHelmet(), helmet);
        }
        if (player.getInventory().getChestplate() != null && chest != null) {
            XItemStack.serialize(player.getInventory().getChestplate(), chest);
        }
        if (player.getInventory().getLeggings() != null && legs != null) {
            XItemStack.serialize(player.getInventory().getLeggings(), legs);
        }
        if (player.getInventory().getBoots() != null && boots != null) {
            XItemStack.serialize(player.getInventory().getBoots(), boots);
        }

        settings.set(root + ".Armor.Helmet.a", null);
        settings.set(root + ".Armor.Chestplate.a", null);
        settings.set(root + ".Armor.Leggings.a", null);
        settings.set(root + ".Armor.Boots.a", null);

        settings.set(root + ".Inventory", "");

        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) {
                continue;
            }

            settings.set(root + ".Inventory." + i + ".a", "temp");
            ConfigurationSection section = settings.getConfigurationSection(root + ".Inventory." + i);
            if (section != null) {
                XItemStack.serialize(item, section);
            }
            settings.set(root + ".Inventory." + i + ".a", null);
        }
    }

    private void saveDefaultKit(Player player, YamlConfiguration settings) {
        switch (EventoType.getEventoType(settings.getString("Evento.Type"))) {
            case PAINTBALL:
            case HUNTER:
            case NEXUS:
                break;

            default:
                settings.set("Itens.Helmet", "");
                settings.set("Itens.Helmet.a", "temp");
                settings.set("Itens.Chestplate", "");
                settings.set("Itens.Chestplate.a", "temp");
                settings.set("Itens.Leggings", "");
                settings.set("Itens.Leggings.a", "temp");
                settings.set("Itens.Boots", "");
                settings.set("Itens.Boots.a", "temp");

                ConfigurationSection helmet = settings.getConfigurationSection("Itens.Helmet");
                ConfigurationSection chest = settings.getConfigurationSection("Itens.Chestplate");
                ConfigurationSection legs = settings.getConfigurationSection("Itens.Leggings");
                ConfigurationSection boots = settings.getConfigurationSection("Itens.Boots");

                if (player.getInventory().getHelmet() != null && helmet != null) {
                    XItemStack.serialize(player.getInventory().getHelmet(), helmet);
                }
                if (player.getInventory().getChestplate() != null && chest != null) {
                    XItemStack.serialize(player.getInventory().getChestplate(), chest);
                }
                if (player.getInventory().getLeggings() != null && legs != null) {
                    XItemStack.serialize(player.getInventory().getLeggings(), legs);
                }
                if (player.getInventory().getBoots() != null && boots != null) {
                    XItemStack.serialize(player.getInventory().getBoots(), boots);
                }

                settings.set("Itens.Helmet.a", null);
                settings.set("Itens.Chestplate.a", null);
                settings.set("Itens.Leggings.a", null);
                settings.set("Itens.Boots.a", null);
                break;
        }

        settings.set("Itens.Inventory", "");

        for (int i = 0; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null) {
                continue;
            }

            settings.set("Itens.Inventory." + i + ".a", "temp");
            ConfigurationSection section = settings.getConfigurationSection("Itens.Inventory." + i);
            if (section != null) {
                XItemStack.serialize(item, section);
            }
            settings.set("Itens.Inventory." + i + ".a", null);
        }
    }

    private boolean handleChatEventCommand(CommandSender sender, String[] args) {
        var evento = AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento();
        if (evento == null) {
            sender.sendMessage(color(message("Messages.Unknown command")));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        if (!player.hasPermission(evento.getPermission())) {
            sender.sendMessage(color(message("Messages.Not allowed")));
            return true;
        }

        evento.parseCommand(player, args);
        return true;
    }

    private boolean handleBackup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&cUse: /evento backup <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(color("&cJogador offline."));
            return true;
        }

        if (!InventorySerializer.hasSnapshot(target.getUniqueId())) {
            sender.sendMessage(color("&cEsse jogador não possui backup."));
            return true;
        }

        String eventIdentifier = InventorySerializer.getSnapshotEventIdentifier(target.getUniqueId());
        boolean restored = InventorySerializer.restoreSnapshot(target);

        if (restored) {
            if (eventIdentifier != null && !eventIdentifier.isBlank()) {
                InventorySerializer.deleteSnapshot(target.getUniqueId(), eventIdentifier);
            } else {
                InventorySerializer.deleteSnapshot(target.getUniqueId());
            }

            sender.sendMessage(color("&aBackup restaurado para &f" + target.getName()));
            target.sendMessage(color("&aSeu inventário foi restaurado por um administrador."));
        } else {
            sender.sendMessage(color("&cErro ao restaurar backup."));
        }

        return true;
    }

    private boolean handleBackupInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&cUse: /evento backupinfo <player>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);

        if (target == null) {
            sender.sendMessage(color("&cJogador offline."));
            return true;
        }

        if (!InventorySerializer.hasSnapshot(target.getUniqueId())) {
            sender.sendMessage(color("&cEsse jogador não possui backup."));
            return true;
        }

        String evento = InventorySerializer.getSnapshotEventIdentifier(target.getUniqueId());

        sender.sendMessage(color("&aBackup encontrado:"));
        sender.sendMessage(color("&7Jogador: &f" + target.getName()));
        sender.sendMessage(color("&7Evento: &f" + (evento != null ? evento : "Desconhecido")));

        return true;
    }

    private void sendHelp(CommandSender sender) {
        if (sender.hasPermission("absolutevents.admin")) {
            for (String msg : AbsolutEventsPlugin.getInstance().getConfig().getStringList("Messages.DefaultAdmin")) {
                sender.sendMessage(color(msg));
            }
        } else {
            for (String msg : AbsolutEventsPlugin.getInstance().getConfig().getStringList("Messages.Default")) {
                sender.sendMessage(color(msg));
            }
        }
    }

    private void sendDefaultSetupHelp(CommandSender sender, YamlConfiguration config) {
        for (String msg : AbsolutEventsPlugin.getInstance().getConfig().getStringList("Messages.Setup")) {
            sender.sendMessage(color(
                    msg.replace("@event", config.getString("Evento.Title"))
            ));
        }
    }

    private void sendMontariaSetupHelp(Player player, YamlConfiguration config) {
        player.sendMessage(color("&6[Montaria Setup] &f" + config.getString("Evento.Title")));
        player.sendMessage(color("&f1. Defina o lobby: &e/evento setup lobby"));
        player.sendMessage(color("&f2. Defina a entrada: &e/evento setup entrance"));
        player.sendMessage(color("&f3. Defina o spectator: &e/evento setup spectator"));
        player.sendMessage(color("&f4. Defina a saída: &e/evento setup exit"));
        player.sendMessage(color("&f5. Defina a largada 1: &e/evento setup startpos1"));
        player.sendMessage(color("&f6. Defina a largada 2: &e/evento setup startpos2"));
        player.sendMessage(color("&f7. Defina a chegada 1: &e/evento setup finishpos1"));
        player.sendMessage(color("&f8. Defina a chegada 2: &e/evento setup finishpos2"));
        player.sendMessage(color("&f9. Adicione checkpoint: &e/evento setup checkpoint add <nome>"));
        player.sendMessage(color("&f10. Checkpoint pos1: &e/evento setup checkpoint pos1 <nome>"));
        player.sendMessage(color("&f11. Checkpoint pos2: &e/evento setup checkpoint pos2 <nome>"));
        player.sendMessage(color("&f12. Sair do setup: &e/evento setup sair"));
    }

    private void sendSignSetupHelp(Player player, YamlConfiguration config) {
        player.sendMessage(color("&6[Parkour Setup] &f" + config.getString("Evento.Title")));
        player.sendMessage(color("&f1. Defina o lobby: &e/evento setup lobby"));
        player.sendMessage(color("&f2. Defina a entrada: &e/evento setup entrance"));
        player.sendMessage(color("&f3. Defina o spectator: &e/evento setup spectator"));
        player.sendMessage(color("&f4. Defina a saída: &e/evento setup exit"));
        player.sendMessage(color("&f5. Adicione checkpoint: &e/evento setup checkpoint"));
        player.sendMessage(color("&f6. Defina o final: &e/evento setup finish"));
        player.sendMessage(color("&f7. Sair do setup: &e/evento setup sair"));
    }

    private void sendQuizSetupHelp(Player player, YamlConfiguration config) {
        player.sendMessage(color("&6[Quiz Setup] &f" + config.getString("Evento.Title")));
        player.sendMessage(color("&f1. Defina o lobby: &e/evento setup lobby"));
        player.sendMessage(color("&f2. Defina a entrada: &e/evento setup entrance"));
        player.sendMessage(color("&f3. Defina o spectator: &e/evento setup spectator"));
        player.sendMessage(color("&f4. Defina a saída: &e/evento setup exit"));
        player.sendMessage(color("&f5. Plataforma VERDADEIRO pos1: &e/evento setup pos1"));
        player.sendMessage(color("&f6. Plataforma VERDADEIRO pos2: &e/evento setup pos2"));
        player.sendMessage(color("&f7. Plataforma FALSO pos3: &e/evento setup pos3"));
        player.sendMessage(color("&f8. Plataforma FALSO pos4: &e/evento setup pos4"));
        player.sendMessage(color("&f9. Plataforma MEIO pos5: &e/evento setup pos5"));
        player.sendMessage(color("&f10. Plataforma MEIO pos6: &e/evento setup pos6"));
        player.sendMessage(color("&f11. Sair do setup: &e/evento setup sair"));
    }

    private boolean isMontaria(YamlConfiguration config) {
        return config.getString("Evento.Type", "").equalsIgnoreCase("montaria");
    }

    private boolean isSign(YamlConfiguration config) {
        return config.getString("Evento.Type", "").equalsIgnoreCase("sign");
    }

    private boolean isQuiz(YamlConfiguration config) {
        return config.getString("Evento.Type", "").equalsIgnoreCase("quiz");
    }

    private String message(String path) {
        return AbsolutEventsPlugin.getInstance().getConfig().getString(path, "&cMensagem não configurada.");
    }

    private String color(String text) {
        return ColorUtils.colorize(text);
    }

    public static Map<Player, YamlConfiguration> getSetupList() {
        return SETUP;
    }

    private boolean isCompletelyEmpty(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        return cursor == null || cursor.getType() == Material.AIR;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("evento")) {
            return Collections.emptyList();
        }

        boolean admin = sender.hasPermission("absolutevents.admin");

        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(PLAYER_COMMANDS);

            if (admin) {
                suggestions.addAll(ADMIN_COMMANDS);
            }

            if (AbsolutEventsPlugin.getInstance().getEventoChatManager().getEvento() != null) {
                suggestions.add("entrar");
                suggestions.add("join");
            }

            return filter(suggestions, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "iniciar":
                case "start":
                case "criarconfig":
                case "createconfig":
                    if (!admin) {
                        return Collections.emptyList();
                    }
                    return filter(AbsolutEventsPlugin.getInstance().getAvailableEventNames(), args[1]);

                case "kick":
                case "backup":
                case "backupinfo":
                    if (!admin) {
                        return Collections.emptyList();
                    }
                    return filter(
                            Bukkit.getOnlinePlayers()
                                    .stream()
                                    .map(Player::getName)
                                    .sorted(String.CASE_INSENSITIVE_ORDER)
                                    .collect(Collectors.toList()),
                            args[1]
                    );

                case "setup":
                    if (!admin) {
                        return Collections.emptyList();
                    }

                    if (!(sender instanceof Player player)) {
                        return Collections.emptyList();
                    }

                    if (!SETUP.containsKey(player)) {
                        return filter(AbsolutEventsPlugin.getInstance().getAvailableEventNames(), args[1]);
                    }

                    return filter(SETUP_ACTIONS, args[1]);

                case "update":
                    if (!admin) {
                        return Collections.emptyList();
                    }
                    return filter(Collections.singletonList("confirm"), args[1]);

                default:
                    return Collections.emptyList();
            }
        }

        if (args.length == 3) {
            if (sub.equals("setup") && admin && sender instanceof Player player && SETUP.containsKey(player)) {
                String action = args[1].toLowerCase(Locale.ROOT);

                if (action.equals("kit") || action.equals("item") || action.equals("itens")) {
                    return filter(KIT_TYPES, args[2]);
                }

                if (action.equals("checkpoint")) {
                    YamlConfiguration settings = SETUP.get(player);
                    if (isMontaria(settings)) {
                        return filter(Arrays.asList("add", "pos1", "pos2"), args[2]);
                    }
                }
            }
        }

        if (args.length == 4) {
            if (sub.equals("setup") && admin && sender instanceof Player player && SETUP.containsKey(player)) {
                String action = args[1].toLowerCase(Locale.ROOT);
                String subAction = args[2].toLowerCase(Locale.ROOT);

                if (action.equals("checkpoint") && (subAction.equals("pos1") || subAction.equals("pos2"))) {
                    YamlConfiguration settings = SETUP.get(player);
                    ConfigurationSection section = settings.getConfigurationSection("Checkpoints");
                    if (section == null) {
                        return Collections.emptyList();
                    }
                    return filter(section.getKeys(false), args[3]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(Collection<String> values, String input) {
        String lower = input.toLowerCase(Locale.ROOT);

        return values.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }
}
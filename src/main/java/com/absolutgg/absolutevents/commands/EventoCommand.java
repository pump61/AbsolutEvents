package com.absolutgg.absolutevents.commands;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.eventos.SuperSmackers;
import com.absolutgg.absolutevents.hooks.BungeecordHook;
import com.absolutgg.absolutevents.manager.ConnectionManager;
import com.absolutgg.absolutevents.manager.InventoryManager;
import com.absolutgg.absolutevents.manager.InventorySerializer;
import com.absolutgg.absolutevents.manager.LeagueManager;
import com.absolutgg.absolutevents.manager.TournamentStatsManager;
import com.absolutgg.absolutevents.manager.UpdateChecker;
import com.absolutgg.absolutevents.manager.UpdateDownloader;
import com.absolutgg.absolutevents.utils.ColorUtils;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.absolutgg.absolutevents.utils.NumberFormatter;
import com.cryptomorin.xseries.XItemStack;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
            "help",
            "dupla",
            "aceitar",
            "accept",
            "recusar",
            "decline"
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
            "update",
            "resettournamentwins",
            "league"
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
            "arena",
            "spawn1",
            "spawn2",
            "display",
            "sair",
            "leave",
            "quit"
    );

    private static final List<String> LEAGUE_SUBCOMMANDS = Arrays.asList(
            "help",
            "info",
            "points",
            "rank",
            "top",
            "addpoints",
            "removepoints",
            "setpoints",
            "setrank",
            "resetseason"
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

            case "dupla":
                return handleDuoInvite(sender, args);

            case "aceitar":
            case "accept":
                return handleDuoAccept(sender);

            case "recusar":
            case "decline":
                return handleDuoDecline(sender);

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

            case "resettournamentwins":
                return handleResetTournamentWins(sender);

            case "league":
                return handleLeague(sender, args);

            default:
                return handleChatEventCommand(sender, args);
        }
    }

    private boolean handleDefault(CommandSender sender) {
        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(color(message("Messages.Console")));
                return true;
            }

            if (AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Enable GUI")) {
                InventoryManager.openMainInventory(player);
                return true;
            }

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

    private boolean handleResetTournamentWins(CommandSender sender) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        TournamentStatsManager.getInstance().resetAll();
        sender.sendMessage(color("&aVitórias do torneio resetadas com sucesso."));
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

    private boolean handleDuoInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&cUse: /evento dupla <jogador>"));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (!(evento instanceof SuperSmackers smackers)) {
            sender.sendMessage(color("&cEsse comando só pode ser usado no Super Smackers."));
            return true;
        }

        if (!smackers.isDuoMode()) {
            sender.sendMessage(color("&cEsse comando só pode ser usado no modo duo."));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color("&cJogador offline."));
            return true;
        }

        smackers.inviteDuo(player, target);
        return true;
    }

    private boolean handleDuoAccept(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (!(evento instanceof SuperSmackers smackers)) {
            sender.sendMessage(color("&cEsse comando só pode ser usado no Super Smackers."));
            return true;
        }

        if (!smackers.isDuoMode()) {
            sender.sendMessage(color("&cEsse comando só pode ser usado no modo duo."));
            return true;
        }

        smackers.acceptDuo(player);
        return true;
    }

    private boolean handleDuoDecline(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(message("Messages.Console")));
            return true;
        }

        var evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (!(evento instanceof SuperSmackers smackers)) {
            sender.sendMessage(color("&cEsse comando só pode ser usado no Super Smackers."));
            return true;
        }

        if (!smackers.isDuoMode()) {
            sender.sendMessage(color("&cEsse comando só pode ser usado no modo duo."));
            return true;
        }

        smackers.declineDuo(player);
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

            for (String msg : config.getStringList("Messages.Cancelled")) {
                Bukkit.broadcastMessage(color(
                        msg.replace("@name", config.getString("Evento.Title", "Evento"))
                ));
            }

            if (AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Enabled")
                    && config.getString("Locations.Server") != null) {
                BungeecordHook.stopEvento("cancelled");
            }

            evento.stop();
        } else {
            config = eventoChat.getConfig();

            for (String msg : config.getStringList("Messages.Cancelled")) {
                Bukkit.broadcastMessage(color(
                        msg.replace("@name", config.getString("Evento.Title", "Evento"))
                ));
            }

            eventoChat.stop();
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
                } else if (isSuperSmackers(current)) {
                    sendSuperSmackersSetupHelp(player, current);
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
            } else if (isBattleRoyale(config)) {
                sendBattleRoyaleSetupHelp(player, config);
            } else if (isSuperSmackers(config)) {
                sendSuperSmackersSetupHelp(player, config);
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
            } else if (isBattleRoyale(settings)) {
                sendBattleRoyaleSetupHelp(player, settings);
            } else if (isSuperSmackers(settings)) {
                sendSuperSmackersSetupHelp(player, settings);
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
                if (isSuperSmackers(settings) && args.length >= 3) {
                    return handleSuperSmackersArenaSpawn(sender, player, settings, args, "Spectator");
                }
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

            case "arena":
                if (isSuperSmackers(settings)) {
                    return handleSuperSmackersArena(sender, player, settings, args);
                }
                sender.sendMessage(color(message("Messages.Unknown argument")));
                return true;

            case "spawn1":
                if (isSuperSmackers(settings)) {
                    return handleSuperSmackersArenaSpawn(sender, player, settings, args, "Spawn1");
                }
                sender.sendMessage(color(message("Messages.Unknown argument")));
                return true;

            case "spawn2":
                if (isSuperSmackers(settings)) {
                    return handleSuperSmackersArenaSpawn(sender, player, settings, args, "Spawn2");
                }
                sender.sendMessage(color(message("Messages.Unknown argument")));
                return true;

            case "display":
                if (isSuperSmackers(settings)) {
                    return handleSuperSmackersArenaDisplay(sender, player, settings, args);
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
        if (isSuperSmackers(settings)) {
            sender.sendMessage(color("&cO Super Smackers não usa /evento setup itens."));
            sender.sendMessage(color("&cConfigure o kit manualmente na seção &fItens &cdo arquivo."));
            return true;
        }

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

    private boolean handleLeague(CommandSender sender, String[] args) {
        if (!sender.hasPermission("absolutevents.admin")) {
            sender.sendMessage(color(message("Messages.No permission")));
            return true;
        }

        LeagueManager league = AbsolutEventsPlugin.getInstance().getLeagueManager();
        ConnectionManager connectionManager = AbsolutEventsPlugin.getInstance().getConnectionManager();

        if (league == null || connectionManager == null || !league.isEnabled()) {
            sender.sendMessage(color("&cA liga não está ativada no servidor."));
            return true;
        }

        if (args.length < 2) {
            sendLeagueHelp(sender);
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help":
                sendLeagueHelp(sender);
                return true;

            case "resetseason":
                league.resetSeasonForAll();
                sender.sendMessage(color("&aTemporada da liga resetada com sucesso."));
                return true;

            case "info":
            case "points":
            case "rank": {
                if (args.length < 3) {
                    sender.sendMessage(color("&cUse: /evento league " + sub + " <player>"));
                    return true;
                }

                OfflinePlayer target = resolveOfflinePlayer(args[2]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(color("&cJogador não encontrado."));
                    return true;
                }

                league.initializePlayer(target.getUniqueId(), target.getName());

                if (sub.equals("points")) {
                    sender.sendMessage(color("&aPontos de &f" + safeName(target) + "&a: &f" + league.getPoints(target.getUniqueId())));
                    return true;
                }

                if (sub.equals("rank")) {
                    sender.sendMessage(color("&aRank de &f" + safeName(target) + "&a: &f" + league.getRankDisplay(target.getUniqueId())
                            + " &7(" + league.getRank(target.getUniqueId()) + ")"));
                    return true;
                }

                sender.sendMessage(color("&6[Liga] &f" + safeName(target)));
                sender.sendMessage(color("&7Pontos: &f" + league.getPoints(target.getUniqueId())));
                sender.sendMessage(color("&7Rank: &f" + league.getRankDisplay(target.getUniqueId()) + " &7(" + league.getRank(target.getUniqueId()) + ")"));
                sender.sendMessage(color("&7Insígnia: &f" + league.getBadge(target.getUniqueId())));
                sender.sendMessage(color("&7Posição: &f#" + league.getPosition(target.getUniqueId())));
                sender.sendMessage(color("&7Vitórias: &f" + league.getWins(target.getUniqueId())));
                sender.sendMessage(color("&7Derrotas: &f" + league.getLosses(target.getUniqueId())));
                sender.sendMessage(color("&7Partidas: &f" + league.getPlayed(target.getUniqueId())));
                sender.sendMessage(color("&7Próximo rank: &f" + league.getNextRank(target.getUniqueId())));
                sender.sendMessage(color("&7Faltam: &f" + league.getPointsToNextRank(target.getUniqueId()) + " pontos"));
                return true;
            }

            case "addpoints":
            case "removepoints":
            case "setpoints": {
                if (args.length < 4) {
                    sender.sendMessage(color("&cUse: /evento league " + sub + " <player> <amount>"));
                    return true;
                }

                OfflinePlayer target = resolveOfflinePlayer(args[2]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(color("&cJogador não encontrado."));
                    return true;
                }

                Integer amount = parseInteger(args[3]);
                if (amount == null) {
                    sender.sendMessage(color("&cValor inválido."));
                    return true;
                }

                league.initializePlayer(target.getUniqueId(), target.getName());

                int current = league.getPoints(target.getUniqueId());
                int updated;

                if (sub.equals("addpoints")) {
                    updated = current + amount;
                } else if (sub.equals("removepoints")) {
                    updated = current - amount;
                } else {
                    updated = amount;
                }

                updated = Math.max(updated, 0);

                connectionManager.setLeaguePoints(target.getUniqueId(), updated);

                String newRank = league.resolveRankByPoints(updated);
                connectionManager.setLeagueRank(target.getUniqueId(), newRank);

                ConnectionManager.LeagueData data = connectionManager.getLeagueData(target.getUniqueId());
                if (data != null) {
                    String peakRank = data.peakRank();
                    if (getRankPoints(league, newRank) > getRankPoints(league, peakRank)) {
                        connectionManager.setLeaguePeakRank(target.getUniqueId(), newRank);
                    }
                } else {
                    connectionManager.setLeaguePeakRank(target.getUniqueId(), newRank);
                }

                sender.sendMessage(color("&aPontos de &f" + safeName(target) + "&aatualizados para &f" + updated + "&a."));
                sender.sendMessage(color("&7Novo rank: &f" + league.getRankDisplay(target.getUniqueId()) + " &7(" + newRank + ")"));

                Player online = target.getPlayer();
                if (online != null && online.isOnline()) {
                    online.sendMessage(color("&aSeus pontos da liga foram atualizados para &f" + updated + "&a."));
                }
                return true;
            }

            case "setrank": {
                if (args.length < 4) {
                    sender.sendMessage(color("&cUse: /evento league setrank <player> <rank>"));
                    return true;
                }

                OfflinePlayer target = resolveOfflinePlayer(args[2]);
                if (target == null || target.getUniqueId() == null) {
                    sender.sendMessage(color("&cJogador não encontrado."));
                    return true;
                }

                String rank = args[3].toUpperCase(Locale.ROOT);
                if (!isValidLeagueRank(rank)) {
                    sender.sendMessage(color("&cRank inválido."));
                    sender.sendMessage(color("&7Ranks: &f" + String.join("&7, &f", getLeagueRanks())));
                    return true;
                }

                league.initializePlayer(target.getUniqueId(), target.getName());

                int rankPoints = getRankPoints(league, rank);
                connectionManager.setLeaguePoints(target.getUniqueId(), rankPoints);
                connectionManager.setLeagueRank(target.getUniqueId(), rank);

                ConnectionManager.LeagueData data = connectionManager.getLeagueData(target.getUniqueId());
                if (data != null) {
                    String peakRank = data.peakRank();
                    if (getRankPoints(league, rank) > getRankPoints(league, peakRank)) {
                        connectionManager.setLeaguePeakRank(target.getUniqueId(), rank);
                    }
                } else {
                    connectionManager.setLeaguePeakRank(target.getUniqueId(), rank);
                }

                sender.sendMessage(color("&aRank de &f" + safeName(target) + "&aatualizado para &f" + rank + "&a."));
                return true;
            }

            case "top": {
                int limit = 10;

                if (args.length >= 3) {
                    Integer parsed = parseInteger(args[2]);
                    if (parsed != null && parsed > 0) {
                        limit = Math.min(parsed, 50);
                    }
                }

                List<ConnectionManager.LeagueData> ranking = league.getTopRanking(limit);

                if (ranking.isEmpty()) {
                    sender.sendMessage(color("&cAinda não há jogadores no ranking da liga."));
                    return true;
                }

                sender.sendMessage(color("&6[Top Liga] &fTop " + ranking.size()));

                for (int i = 0; i < ranking.size(); i++) {
                    ConnectionManager.LeagueData data = ranking.get(i);
                    String name = data.username() == null || data.username().isBlank() ? data.uuid().toString() : data.username();
                    String rankDisplay = league.getRankDisplay(data.uuid());
                    String badge = league.getBadge(data.uuid());

                    sender.sendMessage(color(
                            "&e#" + (i + 1) +
                                    " &f" + name +
                                    " &8- " + badge + " " + rankDisplay +
                                    " &8- &f" + data.points() + " pontos"
                    ));
                }

                return true;
            }

            default:
                sendLeagueHelp(sender);
                return true;
        }
    }

    private void sendLeagueHelp(CommandSender sender) {
        sender.sendMessage(color("&6[Liga] &fComandos disponíveis:"));
        sender.sendMessage(color("&e/evento league help"));
        sender.sendMessage(color("&e/evento league info <player>"));
        sender.sendMessage(color("&e/evento league points <player>"));
        sender.sendMessage(color("&e/evento league rank <player>"));
        sender.sendMessage(color("&e/evento league addpoints <player> <amount>"));
        sender.sendMessage(color("&e/evento league removepoints <player> <amount>"));
        sender.sendMessage(color("&e/evento league setpoints <player> <amount>"));
        sender.sendMessage(color("&e/evento league setrank <player> <rank>"));
        sender.sendMessage(color("&e/evento league resetseason"));
    }

    private OfflinePlayer resolveOfflinePlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }

        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
        for (OfflinePlayer offline : offlinePlayers) {
            if (offline.getName() != null && offline.getName().equalsIgnoreCase(input)) {
                return offline;
            }
        }

        return null;
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() != null ? player.getName() : player.getUniqueId().toString();
    }

    private Integer parseInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private int getRankPoints(LeagueManager league, String rank) {
        if (rank == null || rank.isBlank()) {
            return 0;
        }

        return league.resolveRankByPoints(Integer.MAX_VALUE).equalsIgnoreCase(rank)
                ? getRankPointsFromConfig(rank)
                : getRankPointsFromConfig(rank);
    }

    private int getRankPointsFromConfig(String rank) {
        return AbsolutEventsPlugin.getInstance()
                .getLeagueConfig()
                .getInt("League.Ranks." + rank + ".Points", 0);
    }

    private boolean isValidLeagueRank(String rank) {
        return getLeagueRanks().contains(rank.toUpperCase(Locale.ROOT));
    }

    private List<String> getLeagueRanks() {
        ConfigurationSection section = AbsolutEventsPlugin.getInstance()
                .getLeagueConfig()
                .getConfigurationSection("League.Ranks");

        if (section == null) {
            return Collections.emptyList();
        }

        List<String> ranks = new ArrayList<>(section.getKeys(false));
        ranks.sort(String.CASE_INSENSITIVE_ORDER);
        return ranks;
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
            axeMeta.displayName(net.kyori.adventure.text.Component.text("§6Machado de Posições"));
            axeMeta.lore(Arrays.asList(
                    net.kyori.adventure.text.Component.text("§7* Clique esquerdo para definir a primeira posição."),
                    net.kyori.adventure.text.Component.text("§7* Clique direito para definir a segunda posição.")
            ));
            axe.setItemMeta(axeMeta);
        }
        player.getInventory().addItem(axe);

        if (settings.isSet("Locations.Pos3")
                && EventoType.getEventoType(settings.getString("Evento.Type")) != EventoType.BATTLE_ROYALE) {

            ItemStack hoe = new ItemStack(Material.STONE_HOE, 1);
            ItemMeta hoeMeta = hoe.getItemMeta();
            if (hoeMeta != null) {
                hoeMeta.displayName(net.kyori.adventure.text.Component.text("§6Enxada de Posições"));
                hoeMeta.lore(Arrays.asList(
                        net.kyori.adventure.text.Component.text("§7* Clique esquerdo para definir a terceira posição."),
                        net.kyori.adventure.text.Component.text("§7* Clique direito para definir a quarta posição.")
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

    private boolean isSuperSmackers(YamlConfiguration config) {
        return config.getString("Evento.Type", "").equalsIgnoreCase("supersmackers");
    }

    private void sendSuperSmackersSetupHelp(Player player, YamlConfiguration config) {
        player.sendMessage(color("&6[Super Smackers Setup] &f" + config.getString("Evento.Title")));
        player.sendMessage(color("&f1. Defina o lobby global: &e/evento setup lobby"));
        player.sendMessage(color("&f2. Defina a entrada global: &e/evento setup entrance"));
        player.sendMessage(color("&f3. Defina o spectator global: &e/evento setup spectator"));
        player.sendMessage(color("&f4. Defina a saída global: &e/evento setup exit"));
        player.sendMessage(color("&f5. Crie uma arena: &e/evento setup arena add <id>"));
        player.sendMessage(color("&f6. Defina o nome da arena: &e/evento setup display <id> <nome>"));
        player.sendMessage(color("&f7. Defina o spawn 1 da arena: &e/evento setup spawn1 <id>"));
        player.sendMessage(color("&f8. Defina o spawn 2 da arena: &e/evento setup spawn2 <id>"));
        player.sendMessage(color("&f9. Defina o spectator da arena: &e/evento setup spectator <id>"));
        player.sendMessage(color("&f10. O kit do evento deve ser configurado manualmente no YAML em &eItens"));
        player.sendMessage(color("&f11. Sair do setup: &e/evento setup sair"));
    }

    private boolean handleSuperSmackersArena(CommandSender sender, Player player, YamlConfiguration settings, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(color("&cUse: /evento setup arena add <id>"));
            return true;
        }

        String sub = args[2].toLowerCase(Locale.ROOT);
        String arenaId = args[3];

        if (!sub.equals("add")) {
            sender.sendMessage(color("&cUse: /evento setup arena add <id>"));
            return true;
        }

        String base = "Arenas." + arenaId;

        if (settings.contains(base)) {
            sender.sendMessage(color("&cEssa arena já existe."));
            return true;
        }

        settings.set(base + ".Display", arenaId);

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

        sender.sendMessage(color("&aArena &f" + arenaId + " &acriada com sucesso."));
        return true;
    }

    private boolean handleSuperSmackersArenaSpawn(CommandSender sender, Player player, YamlConfiguration settings, String[] args, String node) {
        if (args.length < 3) {
            sender.sendMessage(color("&cUse: /evento setup " + node.toLowerCase(Locale.ROOT) + " <id>"));
            return true;
        }

        String arenaId = args[2];
        String base = "Arenas." + arenaId + "." + node;

        if (!settings.contains("Arenas." + arenaId)) {
            sender.sendMessage(color("&cEssa arena não existe. Use /evento setup arena add <id>"));
            return true;
        }

        settings.set(base + ".world", player.getLocation().getWorld().getName());
        settings.set(base + ".x", player.getLocation().getX());
        settings.set(base + ".y", player.getLocation().getY());
        settings.set(base + ".z", player.getLocation().getZ());
        settings.set(base + ".Yaw", player.getLocation().getYaw());
        settings.set(base + ".Pitch", player.getLocation().getPitch());

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

        sender.sendMessage(color("&a" + node + " da arena &f" + arenaId + " &asalvo com sucesso."));
        return true;
    }

    private boolean handleSuperSmackersArenaDisplay(CommandSender sender, Player player, YamlConfiguration settings, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(color("&cUse: /evento setup display <id> <nome>"));
            return true;
        }

        String arenaId = args[2];

        if (!settings.contains("Arenas." + arenaId)) {
            sender.sendMessage(color("&cEssa arena não existe. Use /evento setup arena add <id>"));
            return true;
        }

        String display = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        settings.set("Arenas." + arenaId + ".Display", display);

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

        sender.sendMessage(color("&aNome da arena &f" + arenaId + " &aatualizado para &f" + display));
        return true;
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

                case "dupla":
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

                case "league":
                    if (!admin) {
                        return Collections.emptyList();
                    }
                    return filter(LEAGUE_SUBCOMMANDS, args[1]);

                default:
                    return Collections.emptyList();
            }
        }

        if (sub.equals("league") && admin) {
            if (args.length == 3) {
                String action = args[1].toLowerCase(Locale.ROOT);

                if (Arrays.asList("info", "points", "rank", "addpoints", "removepoints", "setpoints", "setrank").contains(action)) {
                    List<String> names = new ArrayList<>();

                    for (Player online : Bukkit.getOnlinePlayers()) {
                        names.add(online.getName());
                    }

                    for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
                        if (offline.getName() != null) {
                            names.add(offline.getName());
                        }
                    }

                    return filter(names, args[2]);
                }

                return Collections.emptyList();
            }

            if (args.length == 4) {
                String action = args[1].toLowerCase(Locale.ROOT);

                if (Arrays.asList("addpoints", "removepoints", "setpoints").contains(action)) {
                    return filter(Arrays.asList("10", "25", "50", "100", "250", "500"), args[3]);
                }

                if (action.equals("setrank")) {
                    return filter(getLeagueRanks(), args[3]);
                }
            }

            if (args.length == 3) {
                String action = args[1].toLowerCase(Locale.ROOT);

                if (action.equals("top")) {
                    return filter(Arrays.asList("10", "15", "20", "30", "50"), args[2]);
                }
            }
        }

        if (sub.equals("setup") && admin && sender instanceof Player player && SETUP.containsKey(player)) {
            YamlConfiguration settings = SETUP.get(player);

            if (isSuperSmackers(settings)) {
                String action = args[1].toLowerCase(Locale.ROOT);

                if (action.equals("arena")) {
                    return filter(Collections.singletonList("add"), args[2]);
                }

                if (action.equals("spawn1") || action.equals("spawn2") || action.equals("spectator") || action.equals("display")) {
                    ConfigurationSection arenas = settings.getConfigurationSection("Arenas");
                    if (arenas == null) {
                        return Collections.emptyList();
                    }
                    return filter(new ArrayList<>(arenas.getKeys(false)), args[2]);
                }
            }
        }

        if (args.length == 4) {
            if (sub.equals("setup") && admin && sender instanceof Player player && SETUP.containsKey(player)) {
                YamlConfiguration settings = SETUP.get(player);

                if (isSuperSmackers(settings)) {
                    String action = args[1].toLowerCase(Locale.ROOT);

                    if (action.equals("display")) {
                        return Collections.emptyList();
                    }
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
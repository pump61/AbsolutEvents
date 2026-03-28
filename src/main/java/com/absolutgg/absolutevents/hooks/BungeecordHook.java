package com.absolutgg.absolutevents.hooks;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.iridium.iridiumcolorapi.IridiumColorAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BungeecordHook implements PluginMessageListener {

    private static final String CHANNEL = "aeventos:channel";

    private static final List<String> JOIN_QUEUE = new CopyOnWriteArrayList<>();
    private static final List<String> SPECTATE_QUEUE = new CopyOnWriteArrayList<>();

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String subchannel = in.readUTF();

            switch (subchannel.toLowerCase()) {
                case "start":
                    handleStart(in);
                    break;

                case "starting":
                    handleStarting(in);
                    break;

                case "stop":
                    handleStop(in);
                    break;

                case "join":
                    handleJoin(in);
                    break;

                case "leave":
                    handleLeave(in);
                    break;

                case "spectate":
                    handleSpectate(in);
                    break;

                case "execute":
                    handleExecute(in);
                    break;

                default:
                    Bukkit.getConsoleSender().sendMessage("§e[AbsolutEvents] Subcanal Bungee desconhecido: " + subchannel);
                    break;
            }

        } catch (Exception exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao processar mensagem do canal Bungee.");
            exception.printStackTrace();
        }
    }

    private void handleStart(DataInputStream in) throws IOException {
        String type = in.readUTF();
        String configName = in.readUTF();

        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() != null) {
            return;
        }

        YamlConfiguration config = EventoConfigFile.get(configName);
        AbsolutEventsPlugin.getInstance().getEventoManager().startEvento(EventoType.getEventoType(type), config);
    }

    private void handleStarting(DataInputStream in) throws IOException {
        String type = in.readUTF();
        String configName = in.readUTF();

        if (AbsolutEventsPlugin.getInstance().getEventoManager().getEvento() == null) {
            YamlConfiguration config = EventoConfigFile.get(configName);
            AbsolutEventsPlugin.getInstance().getEventoManager().startEvento(EventoType.getEventoType(type), config);
        }

        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento != null) {
            evento.startBungeecord();
        }
    }

    private void handleStop(DataInputStream in) throws IOException {
        String reason = in.readUTF();

        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        YamlConfiguration config = evento.getConfig();
        evento.stop();

        switch (reason.toLowerCase()) {
            case "cancelled":
                broadcastConfigMessages(config, "Messages.Cancelled");
                break;

            case "noplayers":
                broadcastConfigMessages(config, "Messages.No players");
                break;

            case "noguilds":
                broadcastConfigMessages(config, "Messages.No guilds");
                break;

            default:
                break;
        }
    }

    private void handleJoin(DataInputStream in) throws IOException {
        String playerName = in.readUTF();

        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        Player bukkitPlayer = Bukkit.getPlayerExact(playerName);

        if (bukkitPlayer == null) {
            if (!JOIN_QUEUE.contains(playerName) && evento.isOpen()) {
                JOIN_QUEUE.add(playerName);
            }
            return;
        }

        if (evento.getPlayers().contains(bukkitPlayer)) {
            return;
        }

        if (evento.isOpen()) {
            evento.join(bukkitPlayer);
        }
    }

    private void handleLeave(DataInputStream in) throws IOException {
        String playerName = in.readUTF();

        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        Player bukkitPlayer = Bukkit.getPlayerExact(playerName);
        if (bukkitPlayer == null) {
            JOIN_QUEUE.remove(playerName);
            SPECTATE_QUEUE.remove(playerName);
            return;
        }

        if (!evento.getPlayers().contains(bukkitPlayer) && !evento.getSpectators().contains(bukkitPlayer)) {
            return;
        }

        evento.leave(bukkitPlayer);
    }

    private void handleSpectate(DataInputStream in) throws IOException {
        String playerName = in.readUTF();

        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        Player bukkitPlayer = Bukkit.getPlayerExact(playerName);

        if (bukkitPlayer == null) {
            if (!SPECTATE_QUEUE.contains(playerName)) {
                SPECTATE_QUEUE.add(playerName);
            }
            return;
        }

        if (evento.getPlayers().contains(bukkitPlayer) || evento.getSpectators().contains(bukkitPlayer)) {
            return;
        }

        evento.spectate(bukkitPlayer);
    }

    private void handleExecute(DataInputStream in) throws IOException {
        String command = in.readUTF();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    public static void startEvento(String type, String config) {
        sendPluginMessage(out -> {
            out.writeUTF("start");
            out.writeUTF(type);
            out.writeUTF(config);
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getStringList("Bungeecord.Servers").toString());
        });
    }

    public static void startingEvento(String type, String config) {
        sendPluginMessage(out -> {
            out.writeUTF("starting");
            out.writeUTF(type);
            out.writeUTF(config);
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getStringList("Bungeecord.Servers").toString());
        });
    }

    public static void stopEvento(String reason) {
        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        sendPluginMessage(out -> {
            out.writeUTF("stop");
            out.writeUTF(evento.getConfig().getString("Locations.Server"));
            out.writeUTF(reason);
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getStringList("Bungeecord.Servers").toString());
            out.writeBoolean(AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Send to default"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getString("Bungeecord.Default"));
        });
    }

    public static void joinEvento(String player) {
        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        sendPluginMessage(out -> {
            out.writeUTF("join");
            out.writeUTF(player);
            out.writeUTF(evento.getConfig().getString("Locations.Server"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getStringList("Bungeecord.Servers").toString());
            out.writeBoolean(AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Send to default"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getString("Bungeecord.Default"));
        });
    }

    public static void leaveEvento(String player) {
        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        sendPluginMessage(out -> {
            out.writeUTF("leave");
            out.writeUTF(player);
            out.writeUTF(evento.getConfig().getString("Locations.Server"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getStringList("Bungeecord.Servers").toString());
            out.writeBoolean(AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Send to default"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getString("Bungeecord.Default"));
        });
    }

    public static void spectateEvento(String player) {
        Evento evento = AbsolutEventsPlugin.getInstance().getEventoManager().getEvento();
        if (evento == null) {
            return;
        }

        sendPluginMessage(out -> {
            out.writeUTF("spectate");
            out.writeUTF(player);
            out.writeUTF(evento.getConfig().getString("Locations.Server"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getStringList("Bungeecord.Servers").toString());
            out.writeBoolean(AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Send to default"));
            out.writeUTF(AbsolutEventsPlugin.getInstance().getConfig().getString("Bungeecord.Default"));
        });
    }

    public static void executeCommand(String player, String command, String server) {
        sendPluginMessage(out -> {
            out.writeUTF("execute");
            out.writeUTF(player);
            out.writeUTF(command);
            out.writeBoolean(AbsolutEventsPlugin.getInstance().getConfig().getBoolean("Bungeecord.Commands on default"));
            out.writeUTF(String.valueOf(AbsolutEventsPlugin.getInstance().getConfig().getString("Bungeecord.Default")));
        });
    }

    public static List<String> getJoin() {
        return JOIN_QUEUE;
    }

    public static List<String> getSpectate() {
        return SPECTATE_QUEUE;
    }

    private static void broadcastConfigMessages(YamlConfiguration config, String path) {
        for (String message : config.getStringList(path)) {
            Bukkit.broadcastMessage(IridiumColorAPI.process(
                    message.replace("&", "§")
                            .replace("@name", config.getString("Evento.Title"))
            ));
        }
    }

    private static void sendPluginMessage(DataWriter writer) {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(stream)) {

            writer.write(out);
            AbsolutEventsPlugin.getInstance().getServer().sendPluginMessage(
                    AbsolutEventsPlugin.getInstance(),
                    CHANNEL,
                    stream.toByteArray()
            );

        } catch (IOException exception) {
            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao enviar mensagem para o BungeeCord.");
            exception.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface DataWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
package com.absolutgg.absolutevents.manager;

import com.absolutgg.absolutevents.AbsolutEventsPlugin;
import com.absolutgg.absolutevents.api.Evento;
import com.absolutgg.absolutevents.api.EventoType;
import com.absolutgg.absolutevents.eventos.*;
import com.iridium.iridiumcolorapi.IridiumColorAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class EventosManager {

    private Evento evento;

    public boolean startEvento(EventoType type, YamlConfiguration config) {

        if (type == null || type == EventoType.NONE) {
            this.evento = null;
            return true;
        }

        if (config == null || evento != null) {
            return false;
        }

        if (!verify(config, type)) {
            return false;
        }

        Evento novoEvento = createEvento(type, config);

        if (novoEvento == null) {
            return false;
        }

        this.evento = novoEvento;

        try {
            evento.startCall();
            return true;

        } catch (Exception exception) {

            Bukkit.getConsoleSender().sendMessage("§c[AbsolutEvents] Erro ao iniciar evento " + type.name());

            try {
                evento.stop();
            } catch (Exception ignored) {}

            evento = null;
            exception.printStackTrace();
            return false;
        }
    }

    public boolean startEvento(EventoType type, YamlConfiguration config, double reward) {

        if (config == null) {
            return false;
        }

        if (reward != -1) {
            config.set("custom_reward", reward);
        }

        return startEvento(type, config);
    }

    private Evento createEvento(EventoType type, YamlConfiguration config) {

        return switch (type) {

            case SIGN -> new Sign(config);
            case CAMPO_MINADO -> new CampoMinado(config);
            case SPLEEF -> new Spleef(config);
            case SEMAFORO -> new Semaforo(config);
            case BATATA_QUENTE -> new BatataQuente(config);
            case FROG -> new Frog(config);
            case FIGHT -> new Fight(config);
            case KILLER -> new Killer(config);
            case SUMO -> new Sumo(config);
            case FALL -> new Fall(config);
            case PAINTBALL -> new Paintball(config);
            case HUNTER -> new Hunter(config);
            case QUIZ -> new Quiz(config);
            case ANVIL -> new Anvil(config);
            case GUERRA -> new Guerra(config);
            case NEXUS -> new Nexus(config);
            case THOR -> new Thor(config);
            case BATTLE_ROYALE -> new BattleRoyale(config);
            case CORRIDA_ARMADA -> new CorridaArmada(config);
            case BLOCK_PARTY -> new BlockParty(config);
            case KILLER_PONTO -> new KillerPonto(config);
            case KOTH -> new Koth(config);
            case MONTARIA -> new Montaria(config);
            case MORTE_SUBITA -> new MorteSubita(config);
            case RAINBOW_RUN -> new RainbowRun(config);
            case SPLEGG -> new Splegg(config);
            case TEAM_DEATHMATCH -> new TeamDeathmatch(config);
            case TNT_RUN -> new TNTRun(config);
            case TORNEIO -> new Torneio(config);

            default -> null;
        };
    }

    private boolean verify(YamlConfiguration config, EventoType type) {

        boolean requirePos12 = false;
        boolean requirePos34 = false;

        switch (type) {

            case CAMPO_MINADO, SPLEEF, FROG, FIGHT, PAINTBALL, HUNTER,
                    QUIZ, ANVIL, NEXUS, BLOCK_PARTY, RAINBOW_RUN,
                    SPLEGG, MORTE_SUBITA, TEAM_DEATHMATCH, TNT_RUN, TORNEIO ->
                    requirePos12 = true;

            case KILLER_PONTO, KOTH, CORRIDA_ARMADA -> {
                requirePos12 = true;
                requirePos34 = true;
            }

            case BATTLE_ROYALE -> {
                if (config.getBoolean("Evento.Refill chests")) {
                    requirePos12 = true;
                }
            }

            default -> {
            }
        }

        // Empty inventory NÃO pode bloquear o evento.
        // Agora ele serve apenas para decidir a regra de entrada do jogador.

        if (!config.contains("Locations.Lobby")
                || !config.contains("Locations.Entrance")
                || !config.contains("Locations.Exit")) {
            return false;
        }

        if (config.getBoolean("Evento.Spectator mode")
                && !config.contains("Locations.Spectator")) {
            return false;
        }

        if (requirePos12 && (!config.contains("Locations.Pos1") || !config.contains("Locations.Pos2"))) {
            return false;
        }

        if (requirePos34 && (!config.contains("Locations.Pos3") || !config.contains("Locations.Pos4"))) {
            return false;
        }

        return true;
    }

    // Pode manter esse método por compatibilidade futura,
    // mas ele não deve mais ser usado para bloquear start de evento.
    private boolean requiresEmptyInventory(EventoType type) {

        return switch (type) {
            case BATATA_QUENTE, SEMAFORO, PAINTBALL, HUNTER,
                    CORRIDA_ARMADA, BLOCK_PARTY, KILLER_PONTO,
                    KOTH, MORTE_SUBITA, RAINBOW_RUN,
                    SPLEGG, TEAM_DEATHMATCH, TNT_RUN, TORNEIO -> true;

            default -> false;
        };
    }

    private void sendAdminWarning(String path) {

        String raw = AbsolutEventsPlugin.getInstance()
                .getConfig()
                .getString(path, "&cConfiguração inválida.");

        String message = IridiumColorAPI.process(raw.replace("&", "§"));

        Bukkit.getConsoleSender().sendMessage(message);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("absolutevents.admin")) {
                player.sendMessage(message);
            }
        }
    }

    public Evento getEvento() {
        return evento;
    }

    public boolean hasEventoRunning() {
        return evento != null;
    }

    public void stopEvento() {

        if (evento == null) {
            return;
        }

        Evento atual = evento;
        evento = null;

        atual.stop();
    }

    public void clearEvento() {
        evento = null;
    }
}
package com.absolutgg.absolutevents.api;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum EventoType {
    SIGN("sign"),
    CAMPO_MINADO("campominado"),
    SPLEEF("spleef"),
    SEMAFORO("semaforo"),
    BATATA_QUENTE("batataquente"),
    FROG("frog"),
    FIGHT("fight"),
    KILLER("killer"),
    SUMO("sumo"),
    FALL("fall"),
    PAINTBALL("paintball"),
    VOTACAO("votacao"),
    HUNTER("hunter"),
    QUIZ("quiz"),
    ANVIL("anvil"),
    LOTERIA("loteria"),
    BOLAO("bolao"),
    GUERRA("guerra"),
    MATEMATICA("matematica"),
    PALAVRA("palavra"),
    FAST_CLICK("fastclick"),
    NEXUS("nexus"),
    SORTEIO("sorteio"),
    THOR("thor"),
    BATTLE_ROYALE("battleroyale"),

    BLOCK_PARTY("blockparty"),
    KILLER_PONTO("killerponto"),
    KOTH("koth"),
    MONTARIA("montaria"),
    MORTE_SUBITA("mortesubita"),
    RAINBOW_RUN("rainbowrun"),
    SPLEGG("splegg"),
    CORRIDA_ARMADA("corridaarmada"),
    TEAM_DEATHMATCH("teamdeathmatch"),
    TNT_RUN("tntrun"),
    TORNEIO("torneio"),
    SUPERSMACKERS("supersmackers"),

    NONE("none");

    private static final Map<String, EventoType> BY_NAME = new HashMap<>();

    private static final Set<EventoType> CHAT_EVENTS = Set.of(
            VOTACAO, LOTERIA, BOLAO, MATEMATICA, PALAVRA, FAST_CLICK, SORTEIO
    );

    private static final Set<EventoType> GUILD_EVENTS = Set.of(
            GUERRA
    );

    static {
        for (EventoType type : values()) {
            register(type.key, type);

            register(type.name(), type);
            register(type.name().toLowerCase(Locale.ROOT), type);
            register(type.name().replace("_", "").toLowerCase(Locale.ROOT), type);
            register(type.name().replace("_", "-").toLowerCase(Locale.ROOT), type);
            register(type.name().replace("_", " ").toLowerCase(Locale.ROOT), type);
        }

        register("campo_minado", CAMPO_MINADO);
        register("campo-minado", CAMPO_MINADO);

        register("batata_quente", BATATA_QUENTE);
        register("batata-quente", BATATA_QUENTE);

        register("fast_click", FAST_CLICK);
        register("fast-click", FAST_CLICK);

        register("battle_royale", BATTLE_ROYALE);
        register("battle-royale", BATTLE_ROYALE);
        register("battle royale", BATTLE_ROYALE);

        register("block_party", BLOCK_PARTY);
        register("block-party", BLOCK_PARTY);
        register("block party", BLOCK_PARTY);

        register("killer_ponto", KILLER_PONTO);
        register("killer-ponto", KILLER_PONTO);
        register("killer ponto", KILLER_PONTO);
        register("killerdeathmatch", KILLER_PONTO);
        register("killer-deathmatch", KILLER_PONTO);
        register("killer deathmatch", KILLER_PONTO);

        register("kingofthehill", KOTH);
        register("king-of-the-hill", KOTH);
        register("king of the hill", KOTH);

        register("morte_subita", MORTE_SUBITA);
        register("morte-subita", MORTE_SUBITA);
        register("morte subita", MORTE_SUBITA);

        register("rainbow_run", RAINBOW_RUN);
        register("rainbow-run", RAINBOW_RUN);
        register("rainbow run", RAINBOW_RUN);

        register("corrida_armada", CORRIDA_ARMADA);
        register("corrida-armada", CORRIDA_ARMADA);
        register("corrida armada", CORRIDA_ARMADA);

        register("team_deathmatch", TEAM_DEATHMATCH);
        register("team-deathmatch", TEAM_DEATHMATCH);
        register("team deathmatch", TEAM_DEATHMATCH);
        register("tdm", TEAM_DEATHMATCH);

        register("tnt_run", TNT_RUN);
        register("tnt-run", TNT_RUN);
        register("tnt run", TNT_RUN);

        register("super smackers", SUPERSMACKERS);
        register("super-smackers", SUPERSMACKERS);
        register("super_smackers", SUPERSMACKERS);
    }

    private final String key;

    EventoType(String key) {
        this.key = key;
    }

    public static EventoType getEventoType(String type) {
        if (type == null || type.isBlank()) {
            return NONE;
        }

        String normalized = normalize(type);
        return BY_NAME.getOrDefault(normalized, NONE);
    }

    public static String getString(EventoType type) {
        return type == null ? NONE.key : type.key;
    }

    public static boolean isEventoChat(EventoType type) {
        return CHAT_EVENTS.contains(type);
    }

    public static boolean isEventoGuild(EventoType type) {
        return GUILD_EVENTS.contains(type);
    }

    public String getKey() {
        return key;
    }

    private static void register(String alias, EventoType type) {
        BY_NAME.put(normalize(alias), type);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
package com.absolutgg.absolutevents;

import com.absolutgg.absolutevents.commands.EventoCommand;
import com.absolutgg.absolutevents.hooks.BungeecordHook;
import com.absolutgg.absolutevents.hooks.PlaceholderAPIHook;
import com.absolutgg.absolutevents.listeners.EventoListener;
import com.absolutgg.absolutevents.manager.AutoStarter;
import com.absolutgg.absolutevents.manager.CacheManager;
import com.absolutgg.absolutevents.manager.ConnectionManager;
import com.absolutgg.absolutevents.manager.ConversorConnectionManager;
import com.absolutgg.absolutevents.manager.EventosChatManager;
import com.absolutgg.absolutevents.manager.EventosManager;
import com.absolutgg.absolutevents.manager.InventorySerializer;
import com.absolutgg.absolutevents.manager.LeagueManager;
import com.absolutgg.absolutevents.manager.UpdateChecker;
import com.absolutgg.absolutevents.utils.ConfigUpdater;
import com.absolutgg.absolutevents.utils.EventoConfigFile;
import com.absolutgg.absolutevents.utils.MenuConfigFile;
import com.absolutgg.absolutevents.utils.QuitCache;
import net.milkbowl.vault.economy.Economy;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class AbsolutEventsPlugin extends JavaPlugin {

    private ConnectionManager connectionManager;
    private YamlConfiguration leagueConfig;
    private LeagueManager leagueManager;

    private final EventosManager eventosManager = new EventosManager();
    private final EventosChatManager eventosChatManager = new EventosChatManager();
    private final CacheManager cacheManager = new CacheManager();
    private final AutoStarter autoStarter = new AutoStarter();

    private final EventoListener eventoListener = new EventoListener();

    private SimpleClans simpleClans;
    private Economy economy;

    private boolean reloaded = true;

    @Override
    public void onLoad() {
        reloaded = false;
    }

    @Override
    public void onEnable() {
        logInfo("Iniciando plugin...");

        if (reloaded) {
            logWarning("Este plugin não é compatível com /reload, PlugMan ou similares.");
            logWarning("Para recarregar arquivos de configuração, use /evento reload.");
        }

        setupConfigFiles();
        setupLeague();

        QuitCache.init(getDataFolder());

        this.connectionManager = new ConnectionManager();

        if (!connectionManager.setup()) {
            logSevere("Não foi possível iniciar a conexão principal do plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (runConverterIfNeeded()) {
            return;
        }

        if (getConfig().getBoolean("UpdateChecker")) {
            UpdateChecker.verify();
        }

        registerListeners();
        setupAddons();

        cacheManager.updateCache();
        autoStarter.setup();

        setupInventoryApiSafely();

        registerBungeeChannelIfEnabled();
        registerCommands();

        setupMetrics();

        if (leagueManager != null) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                try {
                    leagueManager.initializePlayer(player);
                } catch (Exception exception) {
                    getLogger().log(Level.WARNING, "Erro ao inicializar jogador na liga: " + player.getName(), exception);
                }
            });
        }

        logInfo("Plugin iniciado com sucesso!");
    }

    @Override
    public void onDisable() {
        reloaded = false;

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (InventorySerializer.hasSnapshot(player.getUniqueId())) {
                    InventorySerializer.restoreSnapshot(player);
                }
            } catch (Exception exception) {
                getLogger().log(
                        Level.SEVERE,
                        "Erro ao restaurar o inventário do jogador " + player.getName() + " durante o disable.",
                        exception
                );
            }
        }

        stopCurrentEvents();
        autoStarter.stop();
        unregisterListeners();

        Bukkit.getScheduler().cancelTasks(this);

        if (connectionManager != null) {
            connectionManager.close();
        }

        logInfo("Plugin desativado com sucesso!");
    }

    private void setupMetrics() {
        int pluginId = 30438;
        Metrics metrics = new Metrics(this, pluginId);

        metrics.addCustomChart(new SimplePie("update_checker", () ->
                getConfig().getBoolean("UpdateChecker") ? "enabled" : "disabled"
        ));

        metrics.addCustomChart(new SimplePie("bungeecord", () ->
                getConfig().getBoolean("Bungeecord.Enabled") ? "enabled" : "disabled"
        ));

        metrics.addCustomChart(new SimplePie("database_type", () ->
                getConfig().getBoolean("MySQL.Enabled") ? "mysql" : "sqlite"
        ));

        metrics.addCustomChart(new SimplePie("league_enabled", () ->
                leagueConfig != null && leagueConfig.getBoolean("League.Enabled") ? "enabled" : "disabled"
        ));
    }

    private void setupConfigFiles() {
        File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            createDefaultEventConfigs();
        }

        File mainMenuFile = new File(getDataFolder(), "menus/main.yml");

        if (!mainMenuFile.exists()) {
            createDefaultMenuConfigs();
        }

        saveDefaultConfig();

        try {
            ConfigUpdater.update(this, "menus/main.yml", mainMenuFile, Collections.singletonList("none"));
            ConfigUpdater.update(
                    this,
                    "menus/top_players.yml",
                    new File(getDataFolder(), "menus/top_players.yml"),
                    Collections.singletonList("none")
            );
        } catch (IOException exception) {
            logSevere("Não foi possível atualizar os arquivos de configuração.");
            getLogger().log(Level.SEVERE, "Erro ao atualizar arquivos de configuração.", exception);
        }

        ConfigUpdater.updateEventos();
    }

    private void setupLeague() {
        try {
            saveResource("league.yml", false);
            File file = new File(getDataFolder(), "league.yml");
            this.leagueConfig = YamlConfiguration.loadConfiguration(file);
            this.leagueManager = new LeagueManager(leagueConfig);
            logInfo("Sistema de liga carregado com sucesso!");
        } catch (Exception exception) {
            logSevere("Erro ao carregar sistema de liga.");
            getLogger().log(Level.SEVERE, "Falha ao carregar league.yml.", exception);
            this.leagueConfig = new YamlConfiguration();
            this.leagueManager = new LeagueManager(this.leagueConfig);
        }
    }

    private void createDefaultEventConfigs() {
        EventoConfigFile.create("parkour");
        EventoConfigFile.create("campominado");
        EventoConfigFile.create("spleef");
        EventoConfigFile.create("corrida");
        EventoConfigFile.create("semaforo");
        EventoConfigFile.create("batataquente");
        EventoConfigFile.create("frog");
        EventoConfigFile.create("fight");
        EventoConfigFile.create("killer");
        EventoConfigFile.create("sumo");
        EventoConfigFile.create("astronauta");
        EventoConfigFile.create("paintball");
        EventoConfigFile.create("votacao");
        EventoConfigFile.create("hunter");
        EventoConfigFile.create("quiz");
        EventoConfigFile.create("anvil");
        EventoConfigFile.create("loteria");
        EventoConfigFile.create("bolao");
        EventoConfigFile.create("gladiador");
        EventoConfigFile.create("matematica");
        EventoConfigFile.create("palavra");
        EventoConfigFile.create("fastclick");
        EventoConfigFile.create("nexus");
        EventoConfigFile.create("sorteio");
        EventoConfigFile.create("thor");
        EventoConfigFile.create("battleroyale");
        EventoConfigFile.create("blockparty");
        EventoConfigFile.create("killerponto");
        EventoConfigFile.create("koth");
        EventoConfigFile.create("montaria");
        EventoConfigFile.create("mortesubita");
        EventoConfigFile.create("rainbowrun");
        EventoConfigFile.create("splegg");
        EventoConfigFile.create("corridaarmada");
    }

    private void createDefaultMenuConfigs() {
        MenuConfigFile.create("main");
        MenuConfigFile.create("eventos");
        MenuConfigFile.create("top_players");
    }

    private boolean runConverterIfNeeded() {
        if (!getConfig().getBoolean("Conversor.Enabled")) {
            return false;
        }

        logInfo("Iniciando conversão...");

        if (!getConnectionManager().isEmpty()) {
            logSevere("A database do AbsolutEvents não está vazia. Desativando plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return true;
        }

        ConversorConnectionManager conversor = new ConversorConnectionManager();
        conversor.setup();

        boolean converted;
        String pluginName = getConfig().getString("Conversor.Plugin", "").toLowerCase();

        switch (pluginName) {
            case "heventos":
                converted = conversor.convertHEventos();
                break;

            case "yeventos":
                converted = conversor.convertYEventos();
                break;

            default:
                logSevere("Conversor para o plugin '" + pluginName + "' não encontrado. Desativando plugin...");
                conversor.close();
                getServer().getPluginManager().disablePlugin(this);
                return true;
        }

        if (!converted) {
            conversor.close();
            getServer().getPluginManager().disablePlugin(this);
            return true;
        }

        conversor.close();
        logInfo("Conversão concluída com sucesso. Desative o modo conversão para iniciar o plugin.");
        getServer().getPluginManager().disablePlugin(this);
        return true;
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(eventoListener, this);
    }

    private void unregisterListeners() {
        HandlerList.unregisterAll(eventoListener);
        eventosChatManager.unregisterListener();
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(
                getCommand("evento"),
                "O comando 'evento' não foi definido no plugin.yml."
        );

        EventoCommand eventoCommand = new EventoCommand();
        command.setExecutor(eventoCommand);
        command.setTabCompleter(eventoCommand);
    }

    private void registerBungeeChannelIfEnabled() {
        if (!getConfig().getBoolean("Bungeecord.Enabled")) {
            return;
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, "aeventos:channel");
        getServer().getMessenger().registerIncomingPluginChannel(this, "aeventos:channel", new BungeecordHook());
    }

    private void setupAddons() {
        if (!setupSimpleClans()) {
            logWarning("SimpleClans não encontrado.");
        }

        if (!setupEconomy()) {
            logWarning("Vault não encontrado.");
        }

        if (!setupPlaceholderAPI()) {
            logWarning("PlaceholderAPI não encontrado.");
        }
    }

    private boolean setupSimpleClans() {
        Plugin plugin = getServer().getPluginManager().getPlugin("SimpleClans");
        if (!(plugin instanceof SimpleClans simpleClansPlugin)) {
            return false;
        }

        this.simpleClans = simpleClansPlugin;
        return true;
    }

    private boolean setupPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return false;
        }

        new PlaceholderAPIHook(this).register();
        getLogger().info("PlaceholderAPIHook registrado com sucesso.");
        return true;
    }

    private boolean setupEconomy() {
        Plugin plugin = getServer().getPluginManager().getPlugin("Vault");
        if (plugin == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> provider =
                getServer().getServicesManager().getRegistration(Economy.class);

        if (provider == null) {
            return false;
        }

        this.economy = provider.getProvider();
        return this.economy != null;
    }

    private void setupInventoryApiSafely() {
        try {
            Class<?> inventoryManagerClass = Class.forName(
                    "com.absolutgg.absolutevents.libs.inventoryapi.manager.InventoryManager"
            );

            try {
                Method method = inventoryManagerClass.getMethod("enable", JavaPlugin.class);
                method.invoke(null, this);
                logInfo("InventoryAPI iniciada com sucesso via enable(JavaPlugin).");
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method method = inventoryManagerClass.getMethod("enable", Plugin.class);
                method.invoke(null, this);
                logInfo("InventoryAPI iniciada com sucesso via enable(Plugin).");
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method method = inventoryManagerClass.getMethod("init", JavaPlugin.class);
                method.invoke(null, this);
                logInfo("InventoryAPI iniciada com sucesso via init(JavaPlugin).");
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method method = inventoryManagerClass.getMethod("init", Plugin.class);
                method.invoke(null, this);
                logInfo("InventoryAPI iniciada com sucesso via init(Plugin).");
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method method = inventoryManagerClass.getMethod("enable");
                method.invoke(null);
                logInfo("InventoryAPI iniciada com sucesso via enable().");
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Method method = inventoryManagerClass.getMethod("init");
                method.invoke(null);
                logInfo("InventoryAPI iniciada com sucesso via init().");
                return;
            } catch (NoSuchMethodException ignored) {
            }

            logWarning("InventoryAPI encontrada, mas nenhum método compatível de inicialização foi localizado.");
        } catch (ClassNotFoundException exception) {
            logWarning("InventoryAPI não encontrada. Menus baseados nessa biblioteca podem não funcionar.");
        } catch (Throwable throwable) {
            getLogger().log(Level.WARNING, "Falha ao iniciar InventoryAPI.", throwable);
        }
    }

    private void stopCurrentEvents() {
        try {
            if (eventosManager.getEvento() != null) {
                eventosManager.stopEvento();
            }
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Erro ao parar evento normal durante o disable.", exception);
            eventosManager.clearEvento();
        }

        try {
            if (eventosChatManager.getEvento() != null) {
                eventosChatManager.stopEvento();
            } else {
                eventosChatManager.unregisterListener();
            }
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Erro ao parar evento chat durante o disable.", exception);
            eventosChatManager.clearEvento();
        }
    }

    private void logInfo(String message) {
        getLogger().info(message);
    }

    private void logWarning(String message) {
        getLogger().warning(message);
    }

    private void logSevere(String message) {
        getLogger().severe(message);
    }

    public static AbsolutEventsPlugin getInstance() {
        return JavaPlugin.getPlugin(AbsolutEventsPlugin.class);
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public LeagueManager getLeagueManager() {
        return leagueManager;
    }

    public YamlConfiguration getLeagueConfig() {
        return leagueConfig;
    }

    public EventosManager getEventoManager() {
        return eventosManager;
    }

    public EventosChatManager getEventoChatManager() {
        return eventosChatManager;
    }

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    public SimpleClans getSimpleClans() {
        return simpleClans;
    }

    public Economy getEconomy() {
        return economy;
    }

    public boolean isHookedMassiveFactions() {
        return false;
    }

    public boolean isHookedYClans() {
        return false;
    }

    public void refreshCaches() {
        cacheManager.updateCache();
    }

    public List<String> getAvailableEventNames() {
        List<File> files = EventoConfigFile.getAllFiles();
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        return files.stream()
                .map(File::getName)
                .filter(name -> name.endsWith(".yml"))
                .map(name -> name.substring(0, name.length() - 4))
                .filter(name -> !name.equalsIgnoreCase("old"))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public boolean restoreBackup(Player player) {
        try {
            return InventorySerializer.restoreSnapshot(player);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Erro ao restaurar backup do jogador " + player.getName() + ".", exception);
            return false;
        }
    }

    public boolean hasBackup(UUID uniqueId) {
        return InventorySerializer.hasSnapshot(uniqueId);
    }

    public String getBackupEventIdentifier(UUID uniqueId) {
        return InventorySerializer.getSnapshotEventIdentifier(uniqueId);
    }
}
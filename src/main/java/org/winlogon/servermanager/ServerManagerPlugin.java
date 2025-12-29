package org.winlogon.servermanager;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.plugin.java.JavaPlugin;
import org.quartz.SchedulerException;
import org.winlogon.servermanager.config.CronConfig;
import org.winlogon.servermanager.config.ServerManagerConfig;
import org.winlogon.servermanager.config.ServiceConfig;
import org.winlogon.servermanager.cron.CronJobManager;
import org.winlogon.servermanager.discord.DiscordWebhookSender;
import org.winlogon.servermanager.platform.SchedulerAdapter;

import oshi.SystemInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ServerManagerPlugin extends JavaPlugin {
    private ServerManagerConfig mainConfig;
    private final Map<String, ServiceConfig> serviceConfigs = new HashMap<>();
    private YamlConfigurationProperties configProperties;

    private CronJobManager cronJobManager;
    private Optional<DiscordWebhookSender> discordWebhookSender = Optional.empty();

    private final ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(1);
    private final ExecutorService processesExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private CommandRegistrar commandRegistrar;
    private ProcessManager processManager;
    private SchedulerAdapter schedulerAdapter;

    @Override
    public void onLoad() {
        this.configProperties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
            .build();

        var mainConfigPath = getDataFolder().toPath().resolve("config.yml");
        this.mainConfig = YamlConfigurations.update(mainConfigPath, ServerManagerConfig.class, configProperties);

        OperatingSystem.init(getLogger());

        loadServiceConfigs();
    }

    @Override
    public void onEnable() {
        this.processManager = new ProcessManager(this, mainConfig, serviceConfigs, monitorExecutor, processesExecutor);
        this.commandRegistrar = new CommandRegistrar(this);
        this.schedulerAdapter = new SchedulerAdapter(this);
        try {
            this.cronJobManager = new CronJobManager(this);
        } catch (SchedulerException e) {
            getLogger().log(Level.SEVERE, "Failed to initialize cron job manager. Cron jobs will be disabled.", e);
            mainConfig.cronJobsEnabled = false;
        }

        commandRegistrar.registerCommands();
        processManager.startProcessMonitoring();

        scheduleTasks();
        logSystemInfo();
        setupDiscord();

        getLogger().info("ServerManager has been enabled!");
    }

    private void scheduleTasks() {
        if (mainConfig.oomKillerEnabled) {
            monitorExecutor.scheduleAtFixedRate(processManager::runOOMKiller, 10, 10, TimeUnit.SECONDS);
            getLogger().info("OOM Killer enabled and scheduled to run every 10 seconds.");
        }

        if (mainConfig.cronJobsEnabled && cronJobManager != null) {
            loadCronConfigs();
            cronJobManager.startScheduler();
            getLogger().info("Cron jobs enabled and scheduled.");
        }
    }

    private void logSystemInfo() {
        var si = new SystemInfo();
        var os = si.getOperatingSystem();
        var hal = si.getHardware();

        getLogger().info("OS: " + os.getFamily() + " " + os.getVersionInfo());
        getLogger().info("CPU: " + hal.getProcessor().getProcessorIdentifier().getName());
        getLogger().info("Memory: " + (hal.getMemory().getTotal() / (1024 * 1024 * 1024)) + " GB");
    }

    private void setupDiscord() {
        if (mainConfig.discordWebhooksEnabled && !mainConfig.discordWebhookUrl.isEmpty()) {
            this.discordWebhookSender = Optional.of(new DiscordWebhookSender(mainConfig.discordWebhookUrl, getLogger()));
            getLogger().info("Discord webhook integration enabled.");
        } else {
            this.discordWebhookSender = Optional.empty();
        }
    }

    @Override
    public void onDisable() {
        processManager.stopAllProcesses();

        monitorExecutor.shutdown();
        try {
            if (!monitorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                monitorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            monitorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (cronJobManager != null) {
            cronJobManager.shutdownScheduler();
        }

        getLogger().info("ServerManager has been disabled!");
    }

    public Map<String, ServiceConfig> getServiceConfigs() {
        return serviceConfigs;
    }

    private void loadServiceConfigs() {
        serviceConfigs.clear();
        var servicesFolder = getDataFolder().toPath().resolve("services");

        try {
            Files.createDirectories(servicesFolder);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to create services directory.", e);
            return;
        }

        try (Stream<Path> paths = Files.list(servicesFolder)) {
            serviceConfigs.putAll(paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yml"))
                .collect(Collectors.toMap(
                    serviceFile -> serviceFile.getFileName().toString().replace(".yml", ""),
                    serviceFile -> {
                        getLogger().info("Loaded service config: " + serviceFile.getFileName());
                        return YamlConfigurations.update(serviceFile, ServiceConfig.class, configProperties);
                    }
                )));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load service configs.", e);
        }
    }

    private void loadCronConfigs() {
        var cronFolder = getDataFolder().toPath().resolve("cron");

        try {
            Files.createDirectories(cronFolder);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to create cron directory.", e);
            return;
        }

        List<CronConfig> cronConfigs = new ArrayList<>();
        try (var paths = Files.list(cronFolder)) {
            cronConfigs.addAll(paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yml"))
                .map(cronFile -> {
                    getLogger().info("Loaded cron config: " + cronFile.getFileName());
                    return YamlConfigurations.update(cronFile, CronConfig.class, configProperties);
                })
                .collect(Collectors.toList()));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Failed to load cron configs.", e);
        }
        cronJobManager.scheduleJobs(cronConfigs);
    }

    public ServerManagerConfig getMainConfig() {
        return mainConfig;
    }

    public ExecutorService getProcessesExecutor() {
        return processesExecutor;
    }

    public ProcessManager getProcessManager() {
        return processManager;
    }

    public SchedulerAdapter getSchedulerAdapter() {
        return schedulerAdapter;
    }

    public void sendDiscordMessage(String message) {
        discordWebhookSender.ifPresentOrElse(sender -> sender.sendMessage(message), () -> getLogger().warning(message));
    }

    public void reloadConfigs(CommandSourceStack source) {
        processManager.stopAllProcesses();

        var mainConfigPath = getDataFolder().toPath().resolve("config.yml");
        this.mainConfig = YamlConfigurations.update(mainConfigPath, ServerManagerConfig.class, configProperties);

        loadServiceConfigs();
        loadCronConfigs();

        if (mainConfig.cronJobsEnabled && cronJobManager != null) {
            cronJobManager.startScheduler();
        } else if (cronJobManager != null) {
            cronJobManager.shutdownScheduler();
        }

        source.getSender().sendMessage(
            Component.text("Configuration reloaded!", NamedTextColor.GREEN)
        );
    }
}

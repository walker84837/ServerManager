package org.winlogon.servermanager;

import de.exlll.configlib.ConfigLib;
import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import lombok.Getter;

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
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ServerManagerPlugin extends JavaPlugin {
    private final Logger logger = getLogger();

    @Getter
    private ServerManagerConfig mainConfig;
    @Getter
    private final Map<String, ServiceConfig> serviceConfigs = new HashMap<>();
    private YamlConfigurationProperties configProperties;

    private CronJobManager cronJobManager;
    private Optional<DiscordWebhookSender> discordWebhookSender = Optional.empty();

    private final ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(1);
    @Getter
    private final ExecutorService processesExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Getter
    private ProcessManager processManager;
    @Getter
    private SchedulerAdapter schedulerAdapter;

    @Override
    public void onLoad() {
        this.configProperties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .setNameFormatter(NameFormatters.LOWER_KEBAB_CASE)
            .build();

        var mainConfigPath = getDataFolder().toPath().resolve("config.yml");
        this.mainConfig = YamlConfigurations.update(mainConfigPath, ServerManagerConfig.class, configProperties);

        OperatingSystem.init(logger);

        loadServiceConfigs();
    }

    @Override
    public void onEnable() {
        this.processManager = new ProcessManager(this, mainConfig, serviceConfigs, monitorExecutor, processesExecutor);
        var commandRegistrar = new CommandRegistrar(this);
        this.schedulerAdapter = new SchedulerAdapter(this);
        try {
            this.cronJobManager = new CronJobManager(this);
        } catch (SchedulerException e) {
            logger.log(Level.SEVERE, "Failed to initialize cron job manager. Cron jobs will be disabled.", e);
            mainConfig.cronJobsEnabled = false;
        }

        commandRegistrar.registerCommands();
        processManager.startProcessMonitoring();

        scheduleTasks();
        logSystemInfo();
        setupDiscord();

        logger.info("ServerManager has been enabled!");
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

        logger.info("ServerManager has been disabled!");
    }

    private void scheduleTasks() {
        if (mainConfig.oomKillerEnabled) {
            monitorExecutor.scheduleAtFixedRate(processManager::runOOMKiller, 10, 10, TimeUnit.SECONDS);
            logger.info("OOM Killer enabled and scheduled to run every 10 seconds.");
        }

        if (mainConfig.cronJobsEnabled && cronJobManager != null) {
            loadCronConfigs();
            cronJobManager.startScheduler();
            logger.info("Cron jobs enabled and scheduled.");
        }
    }

    // Logs system info when enabling
    private void logSystemInfo() {
        var si = new SystemInfo();
        var os = si.getOperatingSystem();
        var hal = si.getHardware();

        logger.info("OS: " + os.getFamily() + " " + os.getVersionInfo());
        logger.info("CPU: " + hal.getProcessor().getProcessorIdentifier().getName());
        logger.info("Memory: " + (hal.getMemory().getTotal() / (1024 * 1024 * 1024)) + " GB");
    }

    // Sets up Discord webhooks if they are configured
    private void setupDiscord() {
        if (mainConfig.discordWebhooksEnabled && !mainConfig.discordWebhookUrl.isEmpty()) {
            this.discordWebhookSender = Optional.of(new DiscordWebhookSender(mainConfig.discordWebhookUrl, logger));
            logger.info("Discord webhook integration enabled.");
        } else {
            this.discordWebhookSender = Optional.empty();
        }
    }

    private void loadServiceConfigs() {
        serviceConfigs.clear();
        var servicesFolder = getDataFolder().toPath().resolve("services");

        try {
            Files.createDirectories(servicesFolder);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create services directory.", e);
            return;
        }

        try (Stream<Path> paths = Files.list(servicesFolder)) {
            serviceConfigs.putAll(paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yml"))
                .collect(Collectors.toMap(
                    serviceFile -> serviceFile.getFileName().toString().replace(".yml", ""),
                    serviceFile -> {
                        logger.info("Loaded service config: " + serviceFile.getFileName());
                        return YamlConfigurations.update(serviceFile, ServiceConfig.class, configProperties);
                    }
                )));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load service configs.", e);
        }
    }

    private void loadCronConfigs() {
        var cronFolder = getDataFolder().toPath().resolve("cron");

        try {
            Files.createDirectories(cronFolder);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create cron directory.", e);
            return;
        }

        List<CronConfig> cronConfigs = new ArrayList<>();
        try (var paths = Files.list(cronFolder)) {
            cronConfigs.addAll(paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yml"))
                .map(cronFile -> {
                    logger.info("Loaded cron config: " + cronFile.getFileName());
                    return YamlConfigurations.update(cronFile, CronConfig.class, configProperties);
                })
                .toList());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load cron configs.", e);
        }
        cronJobManager.scheduleJobs(cronConfigs);
    }

    /**
     * Sends a message to the configured Discord webhook, and if it's not configured, a warning message will be logged, containing the message.
     * @param message The message to send
     */
    public void sendDiscordMessage(String message) {
        discordWebhookSender.ifPresentOrElse(sender -> sender.sendMessage(message), () -> logger.warning(message));
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

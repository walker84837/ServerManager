package org.winlogon.servermanager;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurations;
import de.exlll.configlib.ConfigLib;

import org.bukkit.plugin.java.JavaPlugin;
import org.winlogon.servermanager.config.CronConfig;
import org.winlogon.servermanager.config.ServerManagerConfig;
import org.winlogon.servermanager.config.ServiceConfig;
import org.winlogon.servermanager.cron.CronJobManager;
import org.winlogon.servermanager.discord.DiscordWebhookSender;

import oshi.SystemInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ServerManagerPlugin extends JavaPlugin {
    private ServerManagerConfig mainConfig;
    private final Map<String, ServiceConfig> serviceConfigs = new HashMap<>();
    private YamlConfigurationProperties configProperties;

    private CronJobManager cronJobManager;
    private DiscordWebhookSender discordWebhookSender;

    private final ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(1);
    private final ExecutorService processesExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private CommandRegistrar commandRegistrar;
    private ProcessManager processManager;

    @Override
    public void onEnable() {
        this.configProperties = ConfigLib.BUKKIT_DEFAULT_PROPERTIES.toBuilder()
            .setNameFormatter(de.exlll.configlib.NameFormatters.LOWER_KEBAB_CASE)
            .build();
        
        // Load main config.yml
        var mainConfigPath = getDataFolder().toPath().resolve("config.yml");
        this.mainConfig = YamlConfigurations.update(mainConfigPath, ServerManagerConfig.class, configProperties);
        
        // Load service configs
        loadServiceConfigs();
        
        this.processManager = new ProcessManager(this, mainConfig, serviceConfigs, monitorExecutor, processesExecutor);
        this.commandRegistrar = new CommandRegistrar(this);

        commandRegistrar.registerCommands();
        
        processManager.startProcessMonitoring();

        if (mainConfig.oomKillerEnabled) {
            monitorExecutor.scheduleAtFixedRate(processManager::runOOMKiller, 10, 10, TimeUnit.SECONDS);
            getLogger().info("OOM Killer enabled and scheduled to run every 10 seconds.");
        }
        
        this.cronJobManager = new CronJobManager(this);

        if (mainConfig.cronJobsEnabled) {
            loadCronConfigs();
            cronJobManager.startScheduler();
            getLogger().info("Cron jobs enabled and scheduled.");
        }

        // Log OS information
        var si = new SystemInfo();
        var os = si.getOperatingSystem();
        var hal = si.getHardware();

        getLogger().info("OS: " + os.getFamily() + " " + os.getVersionInfo());
        getLogger().info("CPU: " + hal.getProcessor().getProcessorIdentifier().getName());
        getLogger().info("Memory: " + (hal.getMemory().getTotal() / (1024 * 1024 * 1024)) + " GB");

        if (mainConfig.discordWebhooksEnabled && !mainConfig.discordWebhookUrl.isEmpty()) {
            this.discordWebhookSender = new DiscordWebhookSender(mainConfig.discordWebhookUrl, getLogger());
            getLogger().info("Discord webhook integration enabled.");
        }
        
        getLogger().info("ServerManager has been enabled!");
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

        createFolderIfNotExists(servicesFolder);

        try (var paths = Files.list(servicesFolder)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yml"))
                .forEach(serviceFile -> {
                    var serviceName = serviceFile.getFileName().toString().replace(".yml", "");
                    var serviceConfig = YamlConfigurations.update(serviceFile, ServiceConfig.class, configProperties);
                    serviceConfigs.put(serviceName, serviceConfig);
                    getLogger().info("Loaded service config: " + serviceName);
                });
        } catch (java.io.IOException e) {
            getLogger().severe("Failed to load service configs: " + e.getMessage());
        }
    }

    private void loadCronConfigs() {
        Path cronFolder = getDataFolder().toPath().resolve("cron");
        createFolderIfNotExists(cronFolder);

        List<CronConfig> cronConfigs = new ArrayList<>();
        try (var paths = Files.list(cronFolder)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".yml"))
                .forEach(cronFile -> {
                    CronConfig cronConfig = YamlConfigurations.update(cronFile, CronConfig.class, configProperties);
                    cronConfigs.add(cronConfig);
                    getLogger().info("Loaded cron config: " + cronFile.getFileName().toString());
                });
        } catch (java.io.IOException e) {
            getLogger().severe("Failed to load cron configs: " + e.getMessage());
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

    public void sendDiscordMessage(String message) {
        if (discordWebhookSender != null) {
            discordWebhookSender.sendMessage(message);
        }
    }

    public void reloadConfigs(CommandSourceStack source) {
        processManager.stopAllProcesses();
        
        Path mainConfigPath = getDataFolder().toPath().resolve("config.yml");
        this.mainConfig = YamlConfigurations.update(mainConfigPath, ServerManagerConfig.class, configProperties);

        loadServiceConfigs();
        loadCronConfigs(); // Reload cron configs as well
        if (mainConfig.cronJobsEnabled) {
            cronJobManager.startScheduler();
        } else {
            cronJobManager.shutdownScheduler();
        }
        
        source.getSender().sendMessage(
            Component.text("Configuration reloaded!", NamedTextColor.GREEN)
        );
    }

    public void createFolderIfNotExists(Path servicesFolder) {
        if (!Files.exists(servicesFolder)) {
            try {
                Files.createDirectories(servicesFolder);
            } catch (IOException e) {
                getLogger().severe("Failed to create services directory: " + e.getMessage());
                return;
            }
        }
    }
}

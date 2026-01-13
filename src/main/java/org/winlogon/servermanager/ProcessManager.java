package org.winlogon.servermanager;

import com.github.walker84837.JResult.Result;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.winlogon.servermanager.config.ServerManagerConfig;
import org.winlogon.servermanager.config.ServiceConfig;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ProcessManager {
    private final ServerManagerPlugin plugin;
    private final Logger logger;
    private final ServerManagerConfig mainConfig;
    private final Map<String, ServiceConfig> serviceConfigs;
    private final Map<String, ProcessHandle> runningProcesses = new ConcurrentHashMap<>();
    private final ScheduledExecutorService monitorExecutor;
    private final ExecutorService processesExecutor;
    private final Map<String, CompletableFuture<Void>> processFutures = new ConcurrentHashMap<>();
    private final SystemInfo systemInfo = new SystemInfo();
    private final OperatingSystem os = systemInfo.getOperatingSystem();

    public ProcessManager(
        ServerManagerPlugin plugin, ServerManagerConfig mainConfig,
        Map<String, ServiceConfig> serviceConfigs, ScheduledExecutorService monitorExecutor,
        ExecutorService processesExecutor
    ) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.mainConfig = mainConfig;
        this.serviceConfigs = serviceConfigs;
        this.monitorExecutor = monitorExecutor;
        this.processesExecutor = processesExecutor;
    }

    public Map<String, ProcessHandle> getRunningProcesses() {
        return Map.copyOf(runningProcesses);
    }

    public void startProcess(String programName, CommandSender sender) {
        Result.<String, String>ok(programName)
          .andThen(this::isProcessNotRunning)
          .andThen(this::getServiceConfig)
          .match(
              config -> processesExecutor.submit(() -> startProcessInternal(programName, config, sender)),
              error -> sendErrorMessage(sender, error, programName)
          );
    }

    private Result<String, String> isProcessNotRunning(String programName) {
        return runningProcesses.containsKey(programName)
            ? Result.err("Program <program> is already running!")
            : Result.ok(programName);
    }

    private Result<ServiceConfig, String> getServiceConfig(String programName) {
        return Optional.ofNullable(serviceConfigs.get(programName))
            .map(Result::<ServiceConfig, String>ok)
            .orElse(Result.err("Unknown program: <program>"));
    }

    private void startProcessInternal(String programName, ServiceConfig config, CommandSender sender) {
        try {
            executePreLaunchCommands(programName, config);

            Process process = createAndStartProcess(config);
            ProcessHandle handle = process.toHandle();

            runningProcesses.put(programName, handle);
            scheduleDurationBasedKill(programName, config, handle, sender);
            setupProcessCompletionHandler(programName, config, process, sender);

            sendSuccessMessage(sender, "Started program '" + programName + "' with PID: " + handle.pid());

        } catch (IOException e) {
            handleStartupFailure(programName, sender, e, "Failed to start program");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handleStartupFailure(programName, sender, e, "Startup interrupted for program");
        } catch (Exception e) {
            handleStartupFailure(programName, sender, e, "Unexpected error starting program");
        }
    }

    private void executePreLaunchCommands(String programName, ServiceConfig config) throws IOException, InterruptedException {
        for (var cmd : config.preLaunchCommands) {
            logger.info("Executing pre-launch command for " + programName + ": " + cmd);
            new ProcessBuilder(cmd.split(" ")).start().waitFor();
        }
    }

    private Process createAndStartProcess(ServiceConfig config) throws IOException {
        var processBuilder = new ProcessBuilder();

        List<String> commandArgs = new ArrayList<>();
        commandArgs.add(config.program);
        commandArgs.addAll(config.args);
        processBuilder.command(commandArgs);

        if (!config.workingDirectory.isEmpty()) {
            processBuilder.directory(new File(config.workingDirectory));
        }

        processBuilder.redirectErrorStream(true);
        return processBuilder.start();
    }

    private void scheduleDurationBasedKill(String programName, ServiceConfig config, ProcessHandle handle, CommandSender sender) {
        if (config.duration <= 0) return;

        monitorExecutor.schedule(() -> {
            if (handle.isAlive()) {
                logger.info("Program " + programName + " reached its duration limit. Sending " + config.killSignal);
                sendKillSignal(handle, config.killSignal);
                sendWarningMessage(sender, "Program <program> reached its duration limit and was terminated.", programName);
            }
        }, config.duration, config.durationUnit);
    }

    private void sendKillSignal(ProcessHandle handle, String signal) {
        var osType = org.winlogon.servermanager.OperatingSystem.Type.detect();
        try {
            if (osType == org.winlogon.servermanager.OperatingSystem.Type.LINUX || osType == org.winlogon.servermanager.OperatingSystem.Type.MACOS) {
                new ProcessBuilder("kill", "-" + signal, String.valueOf(handle.pid())).start().waitFor();
            } else if (osType == org.winlogon.servermanager.OperatingSystem.Type.WINDOWS) {
                new ProcessBuilder("taskkill", "/PID", String.valueOf(handle.pid()), "/F").start().waitFor();
            } else {
                handle.destroy();
            }
        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to send kill signal " + signal + " to process " + handle.pid() + ": " + e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Fallback to destroy()
            handle.destroy();
        }
    }

    private void setupProcessCompletionHandler(String programName, ServiceConfig config, Process process, CommandSender sender) {
        CompletableFuture<Void> future = process.onExit().thenAccept(p -> {
            runningProcesses.remove(programName);
            processFutures.remove(programName);

            executeAfterDeathCommands(programName, config);
            handleAutoRestart(programName, config, sender);
        });

        processFutures.put(programName, future);
    }

    private void executeAfterDeathCommands(String programName, ServiceConfig config) {
        for (String cmd : config.afterDeathCommands) {
            logger.info("Executing after-death command for " + programName + ": " + cmd);
            try {
                new ProcessBuilder(cmd.split(" ")).start().waitFor();
            } catch (IOException | InterruptedException e) {
                logger.severe("Error executing after-death command for " + programName + ": " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // TODO: this relies on Paper/Bukkit's scheduler, and won't work on Folia. `SchedulerAdapter` is a better option.
    private void handleAutoRestart(String programName, ServiceConfig config, CommandSender sender) {
        if (!config.autoRestart) return;

        logger.info("Auto-restarting program: " + programName);
        Bukkit.getScheduler().runTask(plugin, () -> {
            sendWarningMessage(sender, "Auto-restarting program: <gold><program></gold>", programName);
        });

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> startProcess(programName, sender), 5 * 20);
    }

    public void stopProcess(String programName, CommandSender sender) {
        Optional.ofNullable(runningProcesses.get(programName))
            .ifPresentOrElse(
                handle -> processesExecutor.submit(() -> stopProcessInternal(programName, handle, sender)),
                () -> sendErrorMessage(sender, "Program '<program>' is not running!", programName)
            );
    }

    private void stopProcessInternal(String programName, ProcessHandle handle, CommandSender sender) {
        try {
            handle.destroy();

            CompletableFuture<Void> future = processFutures.get(programName);
            if (future != null) {
                future.orTimeout(10, TimeUnit.SECONDS).join();
            }

            sendSuccessMessage(sender, "Stopped program '" + programName + "'");
        } catch (Exception e) {
            handleStopFailure(programName, sender, e);
        }
    }

    public void listProcesses(CommandSourceStack source) {
        var runningProcessEntries = runningProcesses.entrySet().stream()
            .map(entry -> "  - <green>" + entry.getKey() + " (PID: " + entry.getValue().pid() + ")</green>")
            .collect(Collectors.joining("\n"));

        // TODO: Placeholder.unparsed doesn't parse the MiniMessage a string contains. However, this variable contains that.
        // Maybe the `available_programs` placeholder should be effectively deserialized as MiniMessage?
        var availablePrograms = serviceConfigs.keySet().stream()
            .map(programName -> {
                boolean isRunning = runningProcesses.containsKey(programName);
                String status = isRunning ? "<success>[RUNNING]</success>" : "<failure>[STOPPED]</failure>";
                return "  - " + programName + status;
            })
            .collect(Collectors.joining("\n"));

        var message = """
            <secondary>=== Running Processes ===</secondary>
            <placeholder><running_processes></placeholder>

            <secondary>=== Available Programs ===</secondary>
            <available_programs>
            """;

        source.getSender().sendRichMessage(message,
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("running_processes", runningProcesses.isEmpty() ? "  No processes running" : runningProcessEntries),
            Placeholder.unparsed("available_programs", availablePrograms)
        );
    }

    public void stopAllProcesses() {
        runningProcesses.values().forEach(ProcessHandle::destroy);
        runningProcesses.clear();

        processFutures.values().forEach(future -> future.cancel(true));
        processFutures.clear();
    }

    public void runOOMKiller() {
        if (!mainConfig.oomKillerEnabled) return;

        long totalMemoryLimitBytes = mainConfig.totalMemoryLimitMB * 1024L * 1024L;
        if (totalMemoryLimitBytes <= 0) return;

        MemoryUsageSnapshot snapshot = captureMemoryUsage();

        if (snapshot.totalMemoryUsage > totalMemoryLimitBytes) {
            killMemoryHeavyProcess(snapshot);
        }
    }

    private MemoryUsageSnapshot captureMemoryUsage() {
        Map<String, OSProcess> osProcesses = new HashMap<>();
        long currentTotalMemoryUsage = 0;

        for (Map.Entry<String, ProcessHandle> entry : runningProcesses.entrySet()) {
            os.getProcess((int) entry.getValue().pid());
            Optional.ofNullable(os.getProcess((int) entry.getValue().pid())).ifPresent(osProcess -> {
                osProcesses.put(entry.getKey(), osProcess);
            });
        }
        
        currentTotalMemoryUsage = osProcesses.values().stream()
            .mapToLong(OSProcess::getResidentSetSize)
            .sum();

        return new MemoryUsageSnapshot(currentTotalMemoryUsage, osProcesses);
    }

    private void killMemoryHeavyProcess(MemoryUsageSnapshot snapshot) {
        logger.warning("Total memory usage (" +
            (snapshot.totalMemoryUsage / (1024 * 1024)) + " MB) exceeds limit (" +
            mainConfig.totalMemoryLimitMB + " MB). Initiating OOM kill.");

        Optional<Map.Entry<String, OSProcess>> victim = snapshot.osProcesses.entrySet().stream()
            .max(Comparator.comparingLong(e -> calculateOOMBadness(e.getValue())));

        victim.ifPresentOrElse(
            entry -> killProcess(entry.getKey(), entry.getValue()),
            () -> logger.warning("OOM Killer: No suitable process found to kill.")
        );
    }

    private void killProcess(String programName, OSProcess process) {
        Optional.ofNullable(runningProcesses.get(programName)).ifPresent(victimHandle -> {
            logger.severe("OOM Killer: Killing process " + programName +
                " (PID: " + victimHandle.pid() + ") due to excessive memory usage.");
            victimHandle.destroyForcibly();

            Bukkit.getScheduler().runTask(plugin, () -> {
                logger.info("OOM Killer: Killed process " + programName + ".");
            });
        });
    }

    private long calculateOOMBadness(OSProcess process) {
        return process.getResidentSetSize() + process.getVirtualSize();
    }

    public void startProcessMonitoring() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            runningProcesses.entrySet().removeIf(entry -> {
                if (!entry.getValue().isAlive()) {
                    handleUnexpectedTermination(entry.getKey());
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void handleUnexpectedTermination(String programName) {
        logger.warning("Process '" + programName + "' terminated unexpectedly");
        Optional.ofNullable(serviceConfigs.get(programName))
            .filter(config -> config.autoRestart)
            .ifPresent(config -> logger.info("Program '" + programName + "' terminated unexpectedly. Auto-restart will be attempted."));
    }

    // Helper methods for consistent messaging
    private void sendErrorMessage(CommandSender sender, String message, String programName) {
        sender.sendRichMessage("<failure>" + message + "</failure>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("program", programName)
        );
    }

    private void sendWarningMessage(CommandSender sender, String message, String programName) {
        sender.sendRichMessage("<primary>" + message + "</primary>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("program", programName)
        );
    }

    private void sendSuccessMessage(CommandSender sender, String message) {
        sender.sendRichMessage("<success>" + message + "</success>", plugin.getMessageTheme().getPaletteResolver());
    }

    private void handleStartupFailure(String programName, CommandSender sender, Exception e, String context) {
        sender.sendRichMessage(
            "<failure>" + context + " '<program>': " + e.getMessage() + "</failure>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("program", programName)
        );
        logger.severe(context + " '" + programName + "': " + e.getMessage());
    }

    private void handleStopFailure(String programName, CommandSender sender, Exception e) {
        sender.sendRichMessage(
            "<failure>Failed to stop program <program>: <exception></failure>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("program", programName),
            Placeholder.unparsed("exception", e.getMessage())
        );
        logger.severe("Failed to stop program '" + programName + "': " + e.getMessage());
    }

    private record MemoryUsageSnapshot(long totalMemoryUsage, Map<String, OSProcess> osProcesses) {}
}

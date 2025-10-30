package org.winlogon.servermanager;

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
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class ProcessManager {
    private final ServerManagerPlugin plugin;
    private final Logger logger;
    private final ServerManagerConfig mainConfig;
    private final Map<String, ServiceConfig> serviceConfigs;
    private final Map<String, ProcessHandle> runningProcesses = new HashMap<>();
    private final ScheduledExecutorService monitorExecutor;
    private final ExecutorService processesExecutor;
    private final Map<String, CompletableFuture<Void>> processFutures = new HashMap<>();
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
        if (runningProcesses.containsKey(programName)) {
            sendErrorMessage(sender, "Program <program> is already running!", programName);
            return;
        }

        var config = serviceConfigs.get(programName);
        if (config == null) {
            sendErrorMessage(sender, "Unknown program: <program>", programName);
            return;
        }

        processesExecutor.submit(() -> startProcessInternal(programName, config, sender));
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
            Process preLaunchProcess = Runtime.getRuntime().exec(cmd);
            preLaunchProcess.waitFor();
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
                handle.destroy();
                sendWarningMessage(sender, "Program <program> reached its duration limit and was terminated.", programName);
            }
        }, config.duration, config.durationUnit);
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
                Process afterDeathProcess = Runtime.getRuntime().exec(cmd);
                afterDeathProcess.waitFor();
            } catch (IOException | InterruptedException e) {
                logger.severe("Error executing after-death command for " + programName + ": " + e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handleAutoRestart(String programName, ServiceConfig config, CommandSender sender) {
        if (!config.autoRestart) return;

        logger.info("Auto-restarting program: " + programName);
        Bukkit.getScheduler().runTask(plugin, () -> {
            sendWarningMessage(sender, "Auto-restarting program: <gold>" + programName + "</gold>", programName);
        });
        
        processesExecutor.submit(() -> {
            try {
                Thread.sleep(5000);
                startProcess(programName, sender);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void stopProcess(String programName, CommandSender sender) {
        var handle = runningProcesses.get(programName);
        if (handle == null) {
            sendErrorMessage(sender, "Program '" + programName + "' is not running!", programName);
            return;
        }

        var config = serviceConfigs.get(programName);
        if (config == null) {
            sendErrorMessage(sender, "Program '" + programName + "' is not configured!", programName);
            return;
        }

        processesExecutor.submit(() -> stopProcessInternal(programName, handle, sender));
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
        Component message = buildProcessListMessage();
        source.getSender().sendMessage(message);
    }

    private Component buildProcessListMessage() {
        var builder = Component.text();
        
        builder.append(Component.text("=== Running Processes ", NamedTextColor.GOLD));
        
        if (runningProcesses.isEmpty()) {
            builder.append(Component.text("No processes running", NamedTextColor.GRAY));
        } else {
            runningProcesses.forEach((name, handle) -> 
                builder.append(Component.text("- " + name + " (PID: " + handle.pid() + ")\n", NamedTextColor.GREEN))
            );
        }

        builder.append(Component.text("\n=== Available Programs ===", NamedTextColor.GOLD));

        serviceConfigs.keySet().forEach(programName -> {
            boolean isRunning = runningProcesses.containsKey(programName);
            var status = isRunning ? " [RUNNING]" : " [STOPPED]";
            var color = isRunning ? NamedTextColor.GREEN : NamedTextColor.RED;
            
            builder.append(Component.text("\n- " + programName + status, color));
        });

        return builder.build();
    }

    public void stopAllProcesses() {
        runningProcesses.forEach((programName, handle) -> {
            if (handle != null) {
                handle.destroy();
            }
        });
        
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
        long currentTotalMemoryUsage = 0;
        Map<String, OSProcess> osProcesses = new HashMap<>();

        for (Map.Entry<String, ProcessHandle> entry : runningProcesses.entrySet()) {
            String programName = entry.getKey();
            ProcessHandle handle = entry.getValue();
            
            Optional.ofNullable(os.getProcess((int) handle.pid())).ifPresent(osProcess -> {
                osProcesses.put(programName, osProcess);
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
        ProcessHandle victimHandle = runningProcesses.get(programName);
        if (victimHandle != null) {
            logger.severe("OOM Killer: Killing process " + programName + 
                " (PID: " + victimHandle.pid() + ") due to excessive memory usage.");
            victimHandle.destroyForcibly();
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                logger.info("OOM Killer: Killed process " + programName + ".");
            });
        }
    }

    private long calculateOOMBadness(OSProcess process) {
        return process.getResidentSetSize() + process.getVirtualSize();
    }

    public void startProcessMonitoring() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            runningProcesses.entrySet().removeIf(entry -> {
                var programName = entry.getKey();
                var handle = entry.getValue();

                if (!handle.isAlive()) {
                    handleUnexpectedTermination(programName);
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }

    private void handleUnexpectedTermination(String programName) {
        logger.warning("Process '" + programName + "' terminated unexpectedly");
        var config = serviceConfigs.get(programName);
        if (config != null && config.autoRestart) {
            logger.info("Program '" + programName + "' terminated unexpectedly. Auto-restart will be attempted.");
        }
    }

    // Helper methods for consistent messaging
    private void sendErrorMessage(CommandSender sender, String message, String programName) {
        sender.sendRichMessage("<red>" + message + "</red>",
            Placeholder.component("program", Component.text(programName, NamedTextColor.DARK_RED))
        );
    }

    private void sendWarningMessage(CommandSender sender, String message, String programName) {
        sender.sendRichMessage("<yellow>" + message + "</yellow>",
            Placeholder.component("program", Component.text(programName, NamedTextColor.GOLD))
        );
    }

    private void sendSuccessMessage(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private void handleStartupFailure(String programName, CommandSender sender, Exception e, String context) {
        sender.sendRichMessage("<red>" + context + " '" + programName + "': " + e.getMessage() + "</red>");
        logger.severe(context + " '" + programName + "': " + e.getMessage());
    }

    private void handleStopFailure(String programName, CommandSender sender, Exception e) {
        sender.sendRichMessage(
            "<red>Failed to stop program <program_name>: <exception></red>",
            Placeholder.component("program_name", Component.text(programName, NamedTextColor.DARK_RED)),
            Placeholder.component("exception", Component.text(e.getMessage(), NamedTextColor.DARK_RED))
        );
        logger.severe("Failed to stop program '" + programName + "': " + e.getMessage());
    }

    private record MemoryUsageSnapshot(long totalMemoryUsage, Map<String, OSProcess> osProcesses) {}
}

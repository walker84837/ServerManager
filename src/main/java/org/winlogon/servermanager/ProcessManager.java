package org.winlogon.servermanager;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;

import org.winlogon.servermanager.config.ServerManagerConfig;
import org.winlogon.servermanager.config.ServiceConfig;

import java.io.IOException;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.software.os.OperatingSystem;
import oshi.software.os.OSProcess;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ProcessManager {
    private final ServerManagerPlugin plugin;
    private final Logger logger;
    private final ServerManagerConfig mainConfig;
    private final Map<String, ServiceConfig> serviceConfigs;
    private final Map<String, ProcessHandle> runningProcesses = new HashMap<>();
    private final ScheduledExecutorService monitorExecutor;
    private final ExecutorService processesExecutor;
    private final Map<String, CompletableFuture<Void>> processFutures = new HashMap<>();

    public ProcessManager(ServerManagerPlugin plugin, ServerManagerConfig mainConfig, Map<String, ServiceConfig> serviceConfigs, ScheduledExecutorService monitorExecutor, ExecutorService processesExecutor) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.mainConfig = mainConfig;
        this.serviceConfigs = serviceConfigs;
        this.monitorExecutor = monitorExecutor;
        this.processesExecutor = processesExecutor;
    }

    public Map<String, ProcessHandle> getRunningProcesses() {
        return runningProcesses;
    }

    public void startProcess(String programName, CommandSourceStack source) {
        if (runningProcesses.containsKey(programName)) {
            source.getSender().sendRichMessage(
                "<red>Program <program> is already running!</red>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("program", Component.text(programName, NamedTextColor.DARK_RED))
            );
            return;
        }

        ServiceConfig config = serviceConfigs.get(programName);
        if (config == null) {
            source.getSender().sendRichMessage("<red>Unknown program: " + programName + "</red>");
            return;
        }

        try {
            // Execute pre-launch commands
            for (String cmd : config.preLaunchCommands) {
                plugin.getLogger().info("Executing pre-launch command for " + programName + ": " + cmd);
                Process preLaunchProcess = Runtime.getRuntime().exec(cmd);
                preLaunchProcess.waitFor();
            }

            var processBuilder = new ProcessBuilder();

            // Build command with arguments
            java.util.List<String> commandArgs = new java.util.ArrayList<>();
            commandArgs.add(config.program);
            commandArgs.addAll(config.args);
            processBuilder.command(commandArgs);

            // Set working directory if specified
            if (!config.workingDirectory.isEmpty()) {
                processBuilder.directory(new File(config.workingDirectory));
            }

            // Redirect error stream to output stream
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            ProcessHandle handle = process.toHandle();

            runningProcesses.put(programName, handle);

            // Schedule duration-based killing if configured
            if (config.duration > 0) {
                monitorExecutor.schedule(() -> {
                    if (handle.isAlive()) {
                        plugin.getLogger().info("Program " + programName + " reached its duration limit. Sending " + config.killSignal);
                        handle.destroy(); // Default destroy, can be extended to send specific signals
                        source.getSender().sendMessage(
                            Component.text("Program '" + programName + "' reached its duration limit and was terminated.", NamedTextColor.YELLOW)
                        );
                    }
                }, config.duration, config.durationUnit);
            }

            // Create completion future
            CompletableFuture<Void> future = process.onExit().thenAccept(p -> {
                runningProcesses.remove(programName);
                processFutures.remove(programName);

                // Execute after-death commands
                for (String cmd : config.afterDeathCommands) {
                    plugin.getLogger().info("Executing after-death command for " + programName + ": " + cmd);
                    try {
                        Process afterDeathProcess = Runtime.getRuntime().exec(cmd);
                        afterDeathProcess.waitFor();
                    } catch (IOException | InterruptedException e) {
                        plugin.getLogger().severe("Error executing after-death command for " + programName + ": " + e.getMessage());
                    }
                }

                // Auto-restart if configured
                if (config.autoRestart) {
                    logger.info("Auto-restarting program: " + programName);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        source.getSender().sendMessage(
                            Component.text("Auto-restarting program: " + programName, NamedTextColor.YELLOW)
                        );
                    });
                    // Use virtual thread for restart
                    processesExecutor.submit(() -> {
                        try {
                            Thread.sleep(5000); // Wait 5 seconds before restart
                            startProcess(programName, source);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            });

            processFutures.put(programName, future);

            source.getSender().sendMessage(
                Component.text("Started program '" + programName + "' with PID: " + handle.pid(), NamedTextColor.GREEN)
            );

        } catch (IOException | InterruptedException e) {
            source.getSender().sendMessage(
                Component.text("Failed to start program '" + programName + "': " + e.getMessage(), NamedTextColor.RED)
            );
            logger.severe("Failed to start program '" + programName + "': " + e.getMessage());
        }
    }

    public void stopProcess(String programName, CommandSourceStack source) {
        ProcessHandle handle = runningProcesses.get(programName);
        if (handle == null) {
            source.getSender().sendMessage(
                Component.text("Program '" + programName + "' is not running!", NamedTextColor.RED)
            );
            return;
        }

        ServiceConfig config = serviceConfigs.get(programName);
        if (config == null) {
            source.getSender().sendMessage(
                Component.text("Program '" + programName + "' is not configured!", NamedTextColor.RED)
            );
            return;
        }

        try {
            // Destroy the process
            // For now, we'll just use destroy(), but in the future, we can send specific signals
            // based on config.killSignal
            handle.destroy();

            // Wait for completion with timeout
            CompletableFuture<Void> future = processFutures.get(programName);
            if (future != null) {
                future.orTimeout(10, TimeUnit.SECONDS).join();
            }

            source.getSender().sendMessage(
                Component.text("Stopped program '" + programName + "'", NamedTextColor.GREEN)
            );

        } catch (Exception e) {
            source.getSender().sendMessage(
                Component.text("Failed to stop program '" + programName + "': " + e.getMessage(), NamedTextColor.RED)
            );
            logger.severe("Failed to stop program '" + programName + "': " + e.getMessage());
        }
    }

    public void listProcesses(CommandSourceStack source) {
        Component message = Component.text()
            .append(Component.text("=== Running Processes ", NamedTextColor.GOLD))
            .build();

        if (runningProcesses.isEmpty()) {
            message = message.append(Component.text("No processes running", NamedTextColor.GRAY));
        } else {
            for (Map.Entry<String, ProcessHandle> entry : runningProcesses.entrySet()) {
                ProcessHandle handle = entry.getValue();
                message = message.append(
                    Component.text("- " + entry.getKey() + " (PID: " + handle.pid() + ")\n", NamedTextColor.GREEN)
                );
            }
        }

        message = message.append(
            Component.text("\n=== Available Programs ===", NamedTextColor.GOLD)
        );

        for (String programName : serviceConfigs.keySet()) {
            String status = runningProcesses.containsKey(programName) ? " [RUNNING]" : " [STOPPED]";
            NamedTextColor color = runningProcesses.containsKey(programName) ? NamedTextColor.GREEN : NamedTextColor.RED;

            message = message.append(
                Component.text("\n- " + programName + status, color)
            );
        }

        source.getSender().sendMessage(message);
    }

    public void stopAllProcesses() {
        for (String programName : runningProcesses.keySet()) {
            ProcessHandle handle = runningProcesses.get(programName);
            if (handle != null) {
                handle.destroy();
            }
        }
        runningProcesses.clear();

        // Cancel all futures
        for (CompletableFuture<Void> future : processFutures.values()) {
            future.cancel(true);
        }
        processFutures.clear();
    }

    private final SystemInfo systemInfo = new SystemInfo();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();
    private final OperatingSystem os = systemInfo.getOperatingSystem();

    public void runOOMKiller() {
        if (!mainConfig.oomKillerEnabled) {
            return;
        }

        long totalMemoryLimitBytes = mainConfig.totalMemoryLimitMB * 1024 * 1024;
        if (totalMemoryLimitBytes <= 0) {
            return; // OOM killer disabled by limit setting
        }

        long currentTotalMemoryUsage = 0;
        Map<String, OSProcess> osProcesses = new HashMap<>();

        for (Map.Entry<String, ProcessHandle> entry : runningProcesses.entrySet()) {
            String programName = entry.getKey();
            ProcessHandle handle = entry.getValue();
            Optional<OSProcess> osProcess = Optional.ofNullable(os.getProcess((int) handle.pid()));
            if (osProcess.isPresent()) {
                osProcesses.put(programName, osProcess.get());
                currentTotalMemoryUsage += osProcess.get().getResidentSetSize();
            }
        }

        if (currentTotalMemoryUsage > totalMemoryLimitBytes) {
            logger.warning("Total memory usage (" + (currentTotalMemoryUsage / (1024 * 1024)) + " MB) exceeds limit (" + mainConfig.totalMemoryLimitMB + " MB). Initiating OOM kill.");

            // Select the 'bad' process to kill
            Optional<Map.Entry<String, OSProcess>> victim = osProcesses.entrySet().stream()
                .max(Comparator.comparingLong(e -> calculateOOMBadness(e.getValue())));

            if (victim.isPresent()) {
                String victimProgramName = victim.get().getKey();
                ProcessHandle victimHandle = runningProcesses.get(victimProgramName);
                if (victimHandle != null) {
                    logger.severe("OOM Killer: Killing process " + victimProgramName + " (PID: " + victimHandle.pid() + ") due to excessive memory usage.");
                    victimHandle.destroyForcibly(); // Use destroyForcibly for OOM situations
                    // Notify sender if possible, or log to console
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("OOM Killer: Killed process " + victimProgramName + ".");
                    });
                }
            } else {
                logger.warning("OOM Killer: No suitable process found to kill.");
            }
        }
    }

    private long calculateOOMBadness(OSProcess process) {
        // Simplified badness score: RSS + VSZ (Virtual Size) as a proxy for total memory footprint
        // In a real scenario, this would be more complex, similar to Linux kernel's oom_score_adj
        return process.getResidentSetSize() + process.getVirtualSize();
    }

    public void startProcessMonitoring() {
        // Monitor processes every 30 seconds to detect unexpected terminations
        monitorExecutor.scheduleAtFixedRate(() -> {
            runningProcesses.entrySet().removeIf(entry -> {
                String programName = entry.getKey();
                ProcessHandle handle = entry.getValue();

                if (!handle.isAlive()) {
                    logger.warning("Process '" + programName + "' terminated unexpectedly");
                    ServiceConfig config = serviceConfigs.get(programName);
                    if (config != null && config.autoRestart) {
                        // Auto-restart logic is already handled in the onExit() callback of startProcess
                        // This part is mainly for logging unexpected terminations not caught by onExit
                        plugin.getLogger().info("Program '" + programName + "' terminated unexpectedly. Auto-restart will be attempted.");
                    }
                    return true;
                }
                return false;
            });
        }, 30, 30, TimeUnit.SECONDS);
    }
}
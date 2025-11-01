package org.winlogon.servermanager.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.winlogon.servermanager.OperatingSystem;
import org.winlogon.servermanager.ServerManagerPlugin;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.util.FormatUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class SystemCommand {

    private final ServerManagerPlugin plugin;
    private final ExecutorService commandExecutor;
    private final SystemInfo systemInfo = new SystemInfo();
    private final oshi.software.os.OperatingSystem os = systemInfo.getOperatingSystem();
    private final HardwareAbstractionLayer hardware = systemInfo.getHardware();

    private final String permissionNode = "servermanager.command.system";
    private final Permission perm = new Permission(
        permissionNode,
        "Allows execution of system management commands",
        PermissionDefault.OP
    );

    public SystemCommand(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.commandExecutor = plugin.getProcessesExecutor();
        var pm = Bukkit.getPluginManager();

        // Register permission if it doesn't exist
        if (pm.getPermission(permissionNode) == null) {
            pm.addPermission(perm);
        }
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("system")
            .requires(source -> source.getSender().hasPermission(permissionNode))
            .then(Commands.literal("install")
                .then(Commands.argument("package", StringArgumentType.greedyString())
                    .executes(this::installPackage)
                )
            )
            .then(Commands.literal("ram")
                .executes(this::queryRamUsage)
            )
            .then(Commands.literal("storage")
                .executes(this::checkStorageUsage)
            )
            .then(Commands.literal("run")
                .then(Commands.argument("command", StringArgumentType.greedyString())
                    .executes(this::runShellCommand)
                )
            )
            .then(Commands.literal("health")
                .executes(this::systemHealth)
            );
    }

    private int installPackage(CommandContext<CommandSourceStack> context) {
        String packageName = StringArgumentType.getString(context, "package");
        var sender = context.getSource().getSender();

        sender.sendMessage(Component.text("Attempting to install package: " + packageName, NamedTextColor.YELLOW));

        CompletableFuture.runAsync(() -> {
            try {
                var osType = OperatingSystem.Type.detect();
                List<String> installCommand = new ArrayList<>();

                // TODO: use OperatingSystem class to detect package install command
                if (osType == OperatingSystem.Type.WINDOWS) {
                    installCommand.add("choco");
                    sender.sendMessage(Component.text("Package installation not supported on Windows.", NamedTextColor.RED));
                    return;

                }

                if (osType == OperatingSystem.Type.LINUX) {
                    var osFamily = OperatingSystem.LinuxDistro.detect();
                    installCommand.add("sudo");

                    List<String> command = switch (osFamily) {
                        case DEBIAN:
                            yield Arrays.asList("apt-get", "install", "-y", packageName);
                        case ARCH:
                            yield Arrays.asList("pacman", "-S", "--noconfirm", packageName);
                        case FEDORA:
                            yield Arrays.asList("dnf", "install", "-y", packageName);
                        default:
                            yield Arrays.asList("echo", "Unsupported distro");
                    };

                    installCommand.addAll(command);
                } else {
                    sender.sendMessage("Unsupported OS for package installation");
                    return;
                }

                var process = Runtime.getRuntime().exec((String[]) installCommand.toArray());

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    sender.sendMessage(Component.text("Package '" + packageName + "' installed successfully. Output:", NamedTextColor.GREEN));
                    sender.sendMessage(Component.text(output.toString(), NamedTextColor.WHITE));
                } else {
                    var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    var errorOutput = new StringBuilder();
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    sender.sendMessage(Component.text("Package installation failed with exit code " + exitCode + ". Error:", NamedTextColor.RED));
                    sender.sendMessage(Component.text(errorOutput.toString(), NamedTextColor.RED));
                }

            } catch (Exception e) {
                sender.sendMessage(Component.text("Error installing package: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().severe("Error installing package: " + e.getMessage());
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }

    private int queryRamUsage(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();
        long totalMemory = hardware.getMemory().getTotal();
        long availableMemory = hardware.getMemory().getAvailable();
        long usedMemory = totalMemory - availableMemory;

        sender.sendMessage(Component.text("=== RAM Usage ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Total: " + FormatUtil.formatBytes(totalMemory), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Used: " + FormatUtil.formatBytes(usedMemory), NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Available: " + FormatUtil.formatBytes(availableMemory), NamedTextColor.YELLOW));

        return Command.SINGLE_SUCCESS;
    }

    private int checkStorageUsage(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();

        sender.sendMessage(Component.text("=== Storage Usage ===", NamedTextColor.GOLD));

        File[] roots = File.listRoots();
        for (File root : roots) {
            Path rootPath = root.toPath();
            try {
                FileStore store = Files.getFileStore(rootPath);
                long totalSpace = store.getTotalSpace();
                long usableSpace = store.getUsableSpace();
                long usedSpace = totalSpace - usableSpace;


                sender.sendMessage(Component.text("Drive: " + root.getAbsolutePath(), NamedTextColor.AQUA));
                sender.sendMessage(Component.text("  Total: " + FormatUtil.formatBytes(totalSpace), NamedTextColor.WHITE));
                sender.sendMessage(Component.text("  Used: " + FormatUtil.formatBytes(usedSpace), NamedTextColor.GREEN));
                sender.sendMessage(Component.text("  Available: " + FormatUtil.formatBytes(usableSpace), NamedTextColor.YELLOW));
            } catch (Exception e) {
                sender.sendMessage(Component.text("Error checking storage for " + root.getAbsolutePath() + ": " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().severe("Error checking storage: " + e.getMessage());
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int runShellCommand(CommandContext<CommandSourceStack> context) {
        String command = StringArgumentType.getString(context, "command");
        CommandSourceStack source = context.getSource();
        var sender = source.getSender();

        source.getSender().sendMessage(Component.text("Executing shell command: " + command, NamedTextColor.YELLOW));

        CompletableFuture.runAsync(() -> {
            try {
                var process = Runtime.getRuntime().exec(command);

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = process.waitFor();

                if (exitCode == 0) {
                    sender.sendRichMessage("<green>Command executed successfully. Output:</green>");
                    sender.sendMessage(Component.text(output.toString(), NamedTextColor.WHITE));
                } else {
                    var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    var errorOutput = new StringBuilder();
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    sender.sendMessage(Component.text("Command failed with exit code " + exitCode + ". Error:", NamedTextColor.RED));
                    sender.sendMessage(Component.text(errorOutput.toString(), NamedTextColor.RED));
                }

            } catch (Exception e) {
                sender.sendMessage(Component.text("Error executing shell command: " + e.getMessage(), NamedTextColor.RED));
                plugin.getLogger().severe("Error executing shell command: " + e.getMessage());
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }

    private int systemHealth(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();

        sender.sendMessage(Component.text("=== System Health ===", NamedTextColor.GOLD));

        sender.sendRichMessage(
            "<aqua>OS: <family> <version></aqua>",
            Placeholder.component("family", Component.text(os.getFamily(), NamedTextColor.DARK_AQUA)),
            Placeholder.component("version", Component.text(os.getVersionInfo().toString(), NamedTextColor.AQUA))
        );

        sender.sendMessage(Component.text("CPU: " + hardware.getProcessor().getProcessorIdentifier().getName(), NamedTextColor.AQUA));

        // RAM Usage
        long totalMemory = hardware.getMemory().getTotal();
        long availableMemory = hardware.getMemory().getAvailable();
        long usedMemory = totalMemory - availableMemory;
        sender.sendMessage(Component.text("RAM Used: " + FormatUtil.formatBytes(usedMemory) + " / " + FormatUtil.formatBytes(totalMemory), NamedTextColor.AQUA));

        // CPU Load
        double[] loadAverage = hardware.getProcessor().getSystemLoadAverage(3);
        if (loadAverage != null) {
            var stats = String.format(
                "%.2f, %.2f, %.2f",
                loadAverage[0],
                loadAverage[1],
                loadAverage[2]
            );
            sender.sendRichMessage("<aqua>CPU Load Average 1m, 5m, 15m): " + stats + "</aqua>");
        }

        var runningProcesses = plugin.getProcessManager().getRunningProcesses();
        // Running Processes (managed by plugin)
        sender.sendRichMessage("<gold>Managed Processes: </gold>");
        if (runningProcesses.isEmpty()) {
            sender.sendRichMessage("<gray>  No managed processes running.</gray>");
        } else {
            runningProcesses.forEach((name, handle) -> {
                sender.sendMessage(Component.text("  - " + name + " (PID: " + handle.pid() + ")", NamedTextColor.GREEN));
            });
        }

        return Command.SINGLE_SUCCESS;
    }
}

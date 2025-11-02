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
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        sender.sendRichMessage(
            "<yellow>Attempting to install package: <pkg></yellow>",
            Placeholder.component("pkg", Component.text(packageName, NamedTextColor.GOLD))
        );

        CompletableFuture.runAsync(() -> {
            try {
                var osType = OperatingSystem.Type.detect();
                List<String> installCommand = new ArrayList<>();

                // TODO: use OperatingSystem class to detect package install command
                if (osType == OperatingSystem.Type.WINDOWS) {
                    installCommand.add("choco");
                    sender.sendRichMessage("<red>Package installation not supported on Windows.</red>");
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
                    sender.sendRichMessage("<red>Unsupported OS for package installation</red>");
                    return;
                }

                // NOTE: original code used toArray incorrectly; leaving that as-is per your request to focus on sendRichMessage changes.
                var process = Runtime.getRuntime().exec((String[]) installCommand.toArray());

                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                var output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                int exitCode = process.waitFor();

                // This looks not so great, and should be refactored to something better, but it works!
                if (exitCode == 0) {
                    sender.sendRichMessage(
                        "<green>Package '<name>' installed successfully. Output:</green>",
                        Placeholder.component("name", Component.text(packageName, NamedTextColor.DARK_GREEN))
                    );
                    sender.sendRichMessage(
                        "<white><out></white>",
                        Placeholder.component("out", Component.text(output.toString(), NamedTextColor.DARK_GRAY))
                    );
                } else {
                    var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    var errorOutput = new StringBuilder();
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    sender.sendRichMessage(
                        "<red>Package installation failed with exit code <exit-code>. Error:</red>",
                        Placeholder.component("exit-code", Component.text(exitCode, NamedTextColor.DARK_RED))
                    );
                    sender.sendRichMessage(
                        "<red><err></red>",
                        Placeholder.component("err", Component.text(errorOutput.toString(), NamedTextColor.DARK_RED))
                    );
                }

            } catch (Exception e) {
                sender.sendRichMessage(
                    "<red>Error installing package: <err></red>",
                    Placeholder.component("err", Component.text(e.getMessage(), NamedTextColor.DARK_RED))
                );
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

        sender.sendRichMessage("<gold>=== RAM Usage ===</gold>");
        sender.sendRichMessage(
            "<aqua>Total: <total></aqua>",
            Placeholder.component("total", Component.text(FormatUtil.formatBytes(totalMemory), NamedTextColor.DARK_AQUA))
        );
        sender.sendRichMessage(
            "<green>Used: <used></green>",
            Placeholder.component("used", Component.text(FormatUtil.formatBytes(usedMemory), NamedTextColor.DARK_GREEN))
        );
        sender.sendRichMessage(
            "<yellow>Available: <avail></yellow>",
            Placeholder.component("avail", Component.text(FormatUtil.formatBytes(availableMemory), NamedTextColor.GOLD))
        );

        return Command.SINGLE_SUCCESS;
    }

    private int checkStorageUsage(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();

        sender.sendRichMessage("<gold>=== Storage Usage ===</gold>");

        var roots = File.listRoots();
        for (var root : roots) {
            var rootPath = root.toPath();
            try {
                var store = Files.getFileStore(rootPath);
                long totalSpace = store.getTotalSpace();
                long usableSpace = store.getUsableSpace();
                long usedSpace = totalSpace - usableSpace;

                Map<String, TagResolver> map = new HashMap<String, TagResolver>();

                var components = new Component[] {
                    Component.text(root.getAbsolutePath(), NamedTextColor.DARK_AQUA),
                    Component.text(FormatUtil.formatBytes(totalSpace), NamedTextColor.DARK_GRAY),
                    Component.text(FormatUtil.formatBytes(usedSpace), NamedTextColor.DARK_GREEN),
                    Component.text(FormatUtil.formatBytes(usableSpace), NamedTextColor.GOLD),
                };

                map.put("<aqua>Drive: <path></aqua>", Placeholder.component("path", components[0]));
                map.put("<white>  Total: <total></white>", Placeholder.component("total", components[1]));
                map.put("<green>  Used: <used></green>", Placeholder.component("used", components[2]));
                map.put("<yellow>  Available: <avail></yellow>", Placeholder.component("avail", components[3]));

                for (var entry : map.entrySet()) {
                    sender.sendRichMessage(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                sender.sendRichMessage(
                    "<red>Error checking storage for <path>: <err></red>",
                    Placeholder.component("path", Component.text(root.getAbsolutePath(), NamedTextColor.DARK_RED)),
                    Placeholder.component("err", Component.text(e.getMessage(), NamedTextColor.DARK_RED))
                );
                plugin.getLogger().severe("Error checking storage: " + e.getMessage());
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int runShellCommand(CommandContext<CommandSourceStack> context) {
        String command = StringArgumentType.getString(context, "command");
        CommandSourceStack source = context.getSource();
        var sender = source.getSender();

        // yellow -> GOLD for placeholder
        sender.sendRichMessage(
            "<yellow>Executing shell command: <cmd></yellow>",
            Placeholder.component("cmd", Component.text(command, NamedTextColor.GOLD))
        );

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
                    sender.sendRichMessage(
                        "<white><out></white>",
                        Placeholder.component("out", Component.text(output.toString(), NamedTextColor.DARK_GRAY))
                    );
                } else {
                    var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    var errorOutput = new StringBuilder();
                    while ((line = errorReader.readLine()) != null) {
                        errorOutput.append(line).append("\n");
                    }
                    sender.sendRichMessage(
                        "<red>Command failed with exit code <exit-code>. Error:</red>",
                        Placeholder.component("exit-code", Component.text(exitCode, NamedTextColor.DARK_RED))
                    );
                    sender.sendRichMessage(
                        "<red><err></red>",
                        Placeholder.component("err", Component.text(errorOutput.toString(), NamedTextColor.DARK_RED))
                    );
                }

            } catch (Exception e) {
                sender.sendRichMessage(
                    "<red>Error executing shell command: <error></red>",
                    Placeholder.component("error", Component.text(e.getMessage(), NamedTextColor.DARK_RED))
                );
                plugin.getLogger().severe("Error executing shell command: " + e.getMessage());
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }

    private int systemHealth(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();

        sender.sendRichMessage("<gold>=== System Health ===</gold>");

        // aqua -> placeholders use DARK_AQUA
        sender.sendRichMessage(
            "<aqua>OS: <family> <version></aqua>",
            Placeholder.component("family", Component.text(os.getFamily(), NamedTextColor.DARK_AQUA)),
            Placeholder.component("version", Component.text(os.getVersionInfo().toString(), NamedTextColor.DARK_AQUA))
        );

        sender.sendRichMessage(
            "<aqua>CPU: <cpu></aqua>",
            Placeholder.component("cpu", Component.text(hardware.getProcessor().getProcessorIdentifier().getName(), NamedTextColor.DARK_AQUA))
        );

        // RAM Usage
        long totalMemory = hardware.getMemory().getTotal();
        long availableMemory = hardware.getMemory().getAvailable();
        long usedMemory = totalMemory - availableMemory;
        sender.sendRichMessage(
            "<aqua>RAM Used: <used> / <total></aqua>",
            Placeholder.component("used", Component.text(FormatUtil.formatBytes(usedMemory), NamedTextColor.DARK_AQUA)),
            Placeholder.component("total", Component.text(FormatUtil.formatBytes(totalMemory), NamedTextColor.DARK_AQUA))
        );

        // CPU Load
        double[] loadAverage = hardware.getProcessor().getSystemLoadAverage(3);
        if (loadAverage != null) {
            var stats = String.format(
                "%.2f, %.2f, %.2f",
                loadAverage[0],
                loadAverage[1],
                loadAverage[2]
            );
            sender.sendRichMessage(
                "<aqua>CPU Load Average (1m, 5m, 15m): <stats></aqua>",
                Placeholder.component("stats", Component.text(stats, NamedTextColor.DARK_AQUA))
            );
        }

        var runningProcesses = plugin.getProcessManager().getRunningProcesses();
        // Running Processes (managed by plugin)
        sender.sendRichMessage("<gold>Managed Processes:</gold>");
        if (runningProcesses.isEmpty()) {
            sender.sendRichMessage("<gray>  No managed processes running.</gray>");
        } else {
            runningProcesses.forEach((name, handle) -> {
                sender.sendRichMessage(
                    "<green>  - <proc> (PID: <pid>)</green>",
                    Placeholder.component("proc", Component.text(name, NamedTextColor.DARK_GREEN)),
                    Placeholder.component("pid", Component.text(handle.pid(), NamedTextColor.DARK_GREEN))
                );
            });
        }

        return Command.SINGLE_SUCCESS;
    }
}

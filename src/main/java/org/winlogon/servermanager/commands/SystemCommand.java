package org.winlogon.servermanager.commands;

import com.google.common.io.CharStreams;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.winlogon.servermanager.PluginCommand;
import org.winlogon.servermanager.OperatingSystem;
import org.winlogon.servermanager.ServerManagerPlugin;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.util.FormatUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class SystemCommand implements PluginCommand {
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

    @Override
    public Permission permission() {
        return perm;
    }

    @Override
    public String permissionNode() {
        return permissionNode;
    }

    public SystemCommand(ServerManagerPlugin plugin) {
        this.plugin = plugin;
        this.commandExecutor = plugin.getProcessesExecutor();
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCommand() {
        return Commands.literal("system")
            .requires(this::hasPermission)
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
        if (!plugin.getMainConfig().packageManagementEnabled) {
            var sender = context.getSource().getSender();
            sender.sendRichMessage(
                "<failure>Package management commands are disabled by the server administrator.</failure>",
                plugin.getMessageTheme().getPaletteResolver()
            );
            return 0;
        }

        String packageName = StringArgumentType.getString(context, "package");
        var sender = context.getSource().getSender();

        sender.sendRichMessage(
            "<primary>Attempting to install package: <pkg></primary>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("pkg", packageName)
        );

        CompletableFuture.runAsync(() -> {
            var installCommand = OperatingSystem.buildInstallCommand(packageName);

            if (installCommand.isEmpty()) {
                sender.sendRichMessage("<failure>Package installation not supported on this OS.</failure>", plugin.getMessageTheme().getPaletteResolver());
                return;
            }

            try {
                Process process = createShellProcess(installCommand.get()).start();

                String output;
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    output = CharStreams.toString(reader);
                }

                int exitCode = process.waitFor();
                output = plugin.handleCommandOutput(output, sender);

                if (exitCode == 0) {
                    sender.sendRichMessage(
                        "<success>Package '<name>' installed successfully. Output:</success>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("name", packageName)
                    );
                    sender.sendRichMessage(
                        "<foreground><out></foreground>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("out", output)
                    );
                } else {
                    sender.sendRichMessage(
                        "<failure>Package installation failed with exit code <exit-code>. Error:</failure>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("exit-code", String.valueOf(exitCode))
                    );
                    sender.sendMessage(plugin.handleCommandOutputComponent(output, sender));
                }

            } catch (Exception e) {
                sender.sendRichMessage(
                    "<failure>Error installing package: <err></failure>",
                    plugin.getMessageTheme().getPaletteResolver(),
                    Placeholder.unparsed("err", e.getMessage())
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

        sender.sendRichMessage("<header>=== RAM Usage ===</header>", plugin.getMessageTheme().getPaletteResolver());
        sender.sendRichMessage(
            "<label>Total:</label> <details><total></details>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("total", FormatUtil.formatBytes(totalMemory))
        );
        sender.sendRichMessage(
            "<label>Used:</label> <warning><used></warning>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("used", FormatUtil.formatBytes(usedMemory))
        );
        sender.sendRichMessage(
            "<label>Available:</label> <success><avail></success>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("avail", FormatUtil.formatBytes(availableMemory))
        );

        return Command.SINGLE_SUCCESS;
    }

    private int checkStorageUsage(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();

        sender.sendRichMessage("<header>=== Storage Usage ===</header>", plugin.getMessageTheme().getPaletteResolver());

        var roots = File.listRoots();
        for (var root : roots) {
            var rootPath = root.toPath();
            try {
                var store = Files.getFileStore(rootPath);
                long totalSpace = store.getTotalSpace();
                long usableSpace = store.getUsableSpace();
                long usedSpace = totalSpace - usableSpace;

                sender.sendRichMessage(
                    """
                    <label>Drive:</label> <details><path></details>
                      <label>Total:</label> <details><total></details>
                      <label>Used:</label> <warning><used></warning>
                      <label>Available:</label> <success><avail></success>
                    """,
                    plugin.getMessageTheme().getPaletteResolver(),
                    Placeholder.unparsed("path", root.getAbsolutePath()),
                    Placeholder.unparsed("total", FormatUtil.formatBytes(totalSpace)),
                    Placeholder.unparsed("used", FormatUtil.formatBytes(usedSpace)),
                    Placeholder.unparsed("avail", FormatUtil.formatBytes(usableSpace))
                );
            } catch (Exception e) {
                sender.sendRichMessage(
                    "<failure>Error checking storage for <path>: <err></failure>",
                    plugin.getMessageTheme().getPaletteResolver(),
                    Placeholder.unparsed("path", root.getAbsolutePath()),
                    Placeholder.unparsed("err", e.getMessage())
                );
                plugin.getLogger().severe("Error checking storage: " + e.getMessage());
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int runShellCommand(CommandContext<CommandSourceStack> context) {
        String command = StringArgumentType.getString(context, "command");
        var source = context.getSource();
        var sender = source.getSender();

        sender.sendRichMessage(
            "<header>Executing shell command:</header> <details><cmd></details>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("cmd", command)
        );

        CompletableFuture.runAsync(() -> {
            Process process = null;
            try {
                process = createShellProcess(command).start();

                String output;
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    output = reader.lines().collect(java.util.stream.Collectors.joining("\n"));
                }

                int exitCode = process.waitFor();
                output = plugin.handleCommandOutput(output, sender);

                if (exitCode == 0) {
                    sender.sendRichMessage("<success>Command executed successfully. Output:</success>", plugin.getMessageTheme().getPaletteResolver());
                    sender.sendMessage(plugin.handleCommandOutputComponent(output, sender));
                } else {
                    sender.sendRichMessage(
                        "<failure>Command failed with exit code <exit-code>. Output:</failure>",
                        plugin.getMessageTheme().getPaletteResolver(),
                        Placeholder.unparsed("exit-code", String.valueOf(exitCode))
                    );
sender.sendMessage(plugin.handleCommandOutputComponent(output, sender));
                }

            } catch (Exception e) {
                sender.sendRichMessage(
                    "<failure>Error executing shell command: <error></failure>",
                    plugin.getMessageTheme().getPaletteResolver(),
                    Placeholder.unparsed("error", e.getMessage())
                );
                plugin.getLogger().severe("Error executing shell command: " + e.getMessage());
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroy();
                }
            }
        }, commandExecutor);

        return Command.SINGLE_SUCCESS;
    }

    private ProcessBuilder createShellProcess(String command) {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] shellCmd = isWindows
                ? new String[]{"cmd", "/c", command}
                : new String[]{"/bin/sh", "-c", command};
        return new ProcessBuilder(shellCmd).redirectErrorStream(true);
    }

    private int systemHealth(CommandContext<CommandSourceStack> context) {
        var sender = context.getSource().getSender();

        sender.sendRichMessage("<header>=== System Health ===</header>", plugin.getMessageTheme().getPaletteResolver());

        sender.sendRichMessage(
            "<label>OS:</label> <details><family> <version></details>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("family", os.getFamily()),
            Placeholder.unparsed("version", os.getVersionInfo().toString())
        );

        sender.sendRichMessage(
            "<label>CPU:</label> <details><cpu></details>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("cpu", hardware.getProcessor().getProcessorIdentifier().getName())
        );

        long totalMemory = hardware.getMemory().getTotal();
        long availableMemory = hardware.getMemory().getAvailable();
        long usedMemory = totalMemory - availableMemory;
        sender.sendRichMessage(
            "<label>RAM Used:</label> <details><used> / <total></details>",
            plugin.getMessageTheme().getPaletteResolver(),
            Placeholder.unparsed("used", FormatUtil.formatBytes(usedMemory)),
            Placeholder.unparsed("total", FormatUtil.formatBytes(totalMemory))
        );

        double[] loadAverage = hardware.getProcessor().getSystemLoadAverage(3);
        if (loadAverage != null) {
            var stats = String.format(
                "%.2f, %.2f, %.2f",
                loadAverage[0],
                loadAverage[1],
                loadAverage[2]
            );
            sender.sendRichMessage(
                "<label>CPU Load Average (1m, 5m, 15m):</label> <details><stats></details>",
                plugin.getMessageTheme().getPaletteResolver(),
                Placeholder.unparsed("stats", stats)
            );
        }

        var runningProcesses = plugin.getProcessManager().getRunningProcesses();
        sender.sendRichMessage("<header>Managed Processes:</header>", plugin.getMessageTheme().getPaletteResolver());
        if (runningProcesses.isEmpty()) {
            sender.sendRichMessage("<placeholder>  No managed processes running.</placeholder>", plugin.getMessageTheme().getPaletteResolver());
        } else {
            runningProcesses.forEach((name, handle) -> sender.sendRichMessage(
                "  <details><proc></details> <placeholder>(PID: <pid>)</placeholder>",
                plugin.getMessageTheme().getPaletteResolver(),
                Placeholder.unparsed("proc", name),
                Placeholder.unparsed("pid", String.valueOf(handle.pid()))
            ));
        }

        return Command.SINGLE_SUCCESS;
    }
}

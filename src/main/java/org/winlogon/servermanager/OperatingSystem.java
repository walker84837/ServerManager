package org.winlogon.servermanager;

import lombok.Getter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OperatingSystem {
    private static Logger logger = Logger.getLogger(OperatingSystem.class.getName());

    /**
     * Initializes the logger for the OperatingSystem class.
     *
     * @param pluginLogger The logger to be used for logging messages from the OperatingSystem class.
     */
    public static void init(Logger pluginLogger) {
        logger = pluginLogger;
    }

    public interface PackageManager {
        String getInstallCommand(String packageName);
    }

    public enum LinuxDistro {
        DEBIAN(pkg -> "sudo apt install -y " + pkg),
        FEDORA(pkg -> "sudo dnf install -y " + pkg),
        ARCH(pkg -> "sudo pacman -S --noconfirm " + pkg),
        UNKNOWN(pkg -> "echo 'Unsupported distro'");

        @Getter
        private final PackageManager packageManager;

        LinuxDistro(PackageManager packageManager) {
            this.packageManager = packageManager;
        }

        public static LinuxDistro detect() {
            var osMap = Map.of(
                DEBIAN, List.of("ubuntu", "debian"),
                FEDORA, List.of("fedora", "rhel", "centos"),
                ARCH, List.of("arch")
            );

            var osRelease = Path.of("/etc/os-release");
            String content;

            try {
                if (!Files.exists(osRelease)) {
                    logger.fine("/etc/os-release not found");
                    return UNKNOWN;
                }
                content = Files.readString(osRelease, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to read /etc/os-release", e);
                return UNKNOWN;
            }

            return osMap.entrySet().stream()
                    .filter(e -> e.getValue().stream().anyMatch(content::contains))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }

    public enum Type {
        LINUX,
        WINDOWS,
        MACOS,
        UNKNOWN;

        public static Type detect() {
            String osName = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) return WINDOWS;
            if (osName.contains("mac") || osName.contains("darwin")) return MACOS;
            if (osName.contains("nux") || osName.contains("nix")) return LINUX;
            return UNKNOWN;
        }
    }

    public static Optional<String> buildInstallCommand(String packageName) {
        return switch (Type.detect()) {
            case LINUX -> Optional.of(
                    LinuxDistro.detect()
                            .getPackageManager()
                            .getInstallCommand(packageName)
            );
            case WINDOWS -> isOnPath("choco")
                    ? Optional.of("choco install " + packageName + " -y")
                    : Optional.empty();
            case MACOS -> isOnPath("brew")
                    ? Optional.of("brew install " + packageName)
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    public static void runCommand(String command) {
        String[] args = Type.detect() == Type.WINDOWS
                ? new String[] { "cmd", "/c", command }
                : new String[] { "/bin/sh", "-c", command };

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.redirectErrorStream(true);

        try {
            logger.fine(() -> "Executing command: " + command);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> logger.info(line));
            }

            int exitCode = process.waitFor();
            logger.info("Command exited with code " + exitCode);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Command execution interrupted", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to execute command", e);
        }
    }

    public static boolean isOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return false;
        }

        boolean isWindows = Type.detect() == Type.WINDOWS;
        List<String> dirs = Arrays.stream(pathEnv.split(File.pathSeparator))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());

        List<String> exts;
        if (isWindows) {
            String pathext = System.getenv("PATHEXT");
            exts = pathext != null
                    ? Arrays.stream(pathext.split(";"))
                            .map(String::toLowerCase)
                            .toList()
                    : List.of(".exe", ".bat", ".cmd", ".com", "");
        } else {
            exts = List.of("");
        }

        for (var dir : dirs) {
            for (var ext : exts) {
                var candidate = Path.of(dir, name + ext);
                if (Files.isRegularFile(candidate) && (isWindows || Files.isExecutable(candidate))) {
                    return true;
                }
            }
        }
        return false;
    }
}

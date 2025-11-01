package org.winlogon.servermanager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class OperatingSystem {
    public interface PackageManager {
        String getInstallCommand(String packageName);
    }

    public enum LinuxDistro {
        DEBIAN(pkg -> "sudo apt install -y " + pkg),
        FEDORA(pkg -> "sudo dnf install -y " + pkg),
        ARCH(pkg -> "sudo pacman -S --noconfirm " + pkg),
        UNKNOWN(pkg -> "echo 'Unsupported distro'");

        private final PackageManager packageManager;

        LinuxDistro(PackageManager packageManager) {
            this.packageManager = packageManager;
        }

        public PackageManager getPackageManager() {
            return packageManager;
        }

        public static LinuxDistro detect() {
            var osMap = Map.of(
                DEBIAN, List.of("ubuntu", "debian"),
                FEDORA, List.of("fedora", "rhel", "centos"),
                ARCH, List.of("arch")
            );

            try {
                var process = new ProcessBuilder("cat", "/etc/os-release").start();
                var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                // read and process content
                var content = reader.lines()
                        .map(String::toLowerCase)
                        .collect(Collectors.joining("\n"));

                reader.close(); // close stream manually

                // return distro based on content and possible values in osMap
                return osMap.entrySet().stream()
                        .filter(e -> e.getValue().stream().anyMatch(content::contains))
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(UNKNOWN);

            } catch (IOException e) {
                e.printStackTrace();
                return UNKNOWN;
            }
        }
    }

    public enum Type {
        LINUX,
        WINDOWS,
        MACOS,
        UNKNOWN;

        public static Type detect() {
            String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (osName.contains("win")) return WINDOWS;
            if (osName.contains("mac")) return MACOS;
            if (osName.contains("nux") || osName.contains("nix")) return LINUX;
            return UNKNOWN;
        }
    }

    public static Optional<String> buildInstallCommand(String packageName) {
        var os = OperatingSystem.Type.detect();

        switch (os) {
            case LINUX -> {
                var distro = LinuxDistro.detect();
                return Optional.of(distro.getPackageManager().getInstallCommand(packageName));
            }
            case WINDOWS -> {
                if (!isOnPath("choco")) {
                    return Optional.empty();
                }
                return Optional.of("choco install " + packageName + " -y");
            }
            case MACOS -> {
                if (!isOnPath("brew")) return Optional.empty();
                return Optional.of("brew install " + packageName);
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    // TODO: get rid of System.out.println: not recommended with Minecraft plugins
    public static void runCommand(String command) {
        try {
            // TODO: should this be `bash` or `sh`?
            var args = Type.detect() == Type.WINDOWS
                ? new String[] { "cmd", "/c", command }
                : new String[] { "bash", "-c", command };

            var builder = new ProcessBuilder(args);
            builder.redirectErrorStream(true);

            var process = builder.start();

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                reader.lines().forEach(System.out::println);
            }

            int exitCode = process.waitFor();
            System.out.println("Command exited with code: " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isOnPath(String name) {
        var path = System.getenv("PATH");

        if (path == null || path.isEmpty()) return false;
        boolean isWindows = Type.detect() == Type.WINDOWS;

        List<String> dirs = Arrays.asList(path.split(File.pathSeparator));
        List<String> exts = isWindows ? Arrays.asList(".exe", ".bat", ".cmd", ".com", "") : List.of("", ".app");

        for (var dir : dirs) {
            for (var ext : exts) {
                var p = Path.of(dir, name + ext);
                var f = p.toFile();
                if (f.isFile() && (isWindows || f.canExecute())) return true;
            }
        }
        return false;
    }
}

package org.winlogon.servermanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

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
            try {
                var process = new ProcessBuilder("cat", "/etc/os-release").start();
                try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    List<String> lines = reader.lines().toList();

                    for (var line : lines) {
                        if (line.startsWith("ID=")) {
                            var id = line.substring(3).replace("\"", "").toLowerCase();

                            if (id.contains("debian") || id.contains("ubuntu")) return DEBIAN;
                            if (id.contains("fedora") || id.contains("rhel") || id.contains("centos")) return FEDORA;
                            if (id.contains("arch")) return ARCH;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return UNKNOWN;
        }
    }

    public enum Type {
        LINUX,
        WINDOWS,
        MACOS,
        UNKNOWN;

        public static Type detect() {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) return WINDOWS;
            if (osName.contains("mac")) return MACOS;
            if (osName.contains("nux") || osName.contains("nix")) return LINUX;
            return UNKNOWN;
        }
    }

    public static Optional<String> buildInstallCommand(String packageName) {
        var os = OperatingSystem.Type.detect();

        // TODO: check if this choco or brew are installed
        switch (os) {
            case LINUX -> {
                var distro = LinuxDistro.detect();
                return Optional.of(distro.getPackageManager().getInstallCommand(packageName));
            }
            case WINDOWS -> {
                return Optional.of("choco install " + packageName + " -y");
            }
            case MACOS -> {
                return Optional.of("brew install " + packageName);
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    // TODO: handle Windows commands properly
    public static void runCommand(String command) {
        try {
            var builder = new ProcessBuilder("bash", "-c", command);
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
}

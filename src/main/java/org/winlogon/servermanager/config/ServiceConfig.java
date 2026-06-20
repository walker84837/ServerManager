package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class ServiceConfig {
    @Comment("Commands to run before launching the program")
    public List<String> preLaunchCommands = List.of();

    @Comment("The program executable to launch")
    public String program = "";

    @Comment("Arguments to pass to the program")
    public List<String> args = List.of();

    @Comment("Working directory for the program")
    public String workingDirectory = ".";

    @Comment("Environment variables to set for the process (inherits system environment by default)")
    public Map<String, String> environment = Map.of();

    @Comment("Duration to keep the program running before sending a kill signal (0 for indefinite)")
    public long duration = 0;

    @Comment("Time unit for the duration (SECONDS, MINUTES, HOURS, DAYS)")
    public TimeUnit durationUnit = TimeUnit.MINUTES;

    @Comment("Kill mode: SOFT (graceful termination) or FORCE (immediate termination)")
    public String killMode = "SOFT";

    @Comment("Commands to run after the program terminates")
    public List<String> afterDeathCommands = List.of();

    @Comment("Whether to automatically restart the service if it stops")
    public boolean autoRestart = false;

    public ServiceConfig() {
    }
}

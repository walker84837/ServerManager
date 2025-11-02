package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

@Configuration
public class CronConfig {
    @Comment("The cron expression for scheduling (e.g., \"0 0 12 * * ?\")")
    public String expression = "";

    @Comment("The command to execute when the cron job triggers")
    public String command = "";

    @Comment("Whether the cron job is enabled")
    public boolean enabled = true;

    public CronConfig() {
    }
}

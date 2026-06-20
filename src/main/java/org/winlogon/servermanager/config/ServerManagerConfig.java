package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

@Configuration
public class ServerManagerConfig {
    @Comment("Enable or disable Discord webhook integration (requires discordWebhookUrl to be set)")
    public boolean discordWebhooksEnabled = false;

    @Comment("The Discord webhook URL for sending notifications (leave empty to disable)")
    public String discordWebhookUrl = "";

    @Comment("Enable or disable cron job scheduling")
    public boolean cronJobsEnabled = false;

    @Comment("Enable or disable the Out-Of-Memory (OOM) killer")
    public boolean oomKillerEnabled = false;

    @Comment("Maximum total memory (in MB) allowed for all processes managed by the plugin. Set to 0 for no limit.")
    public long totalMemoryLimitMB = 0;

    @Comment("Enable or disable package management commands (/system install). Disabled by default for security.")
    public boolean packageManagementEnabled = false;

    @Comment("Color palette for message theming")
    public PaletteConfig palette = new PaletteConfig();

    @Comment("Paste service configuration for long command output")
    public PasteServiceConfig pasteService = new PasteServiceConfig();

    public ServerManagerConfig() {
    }
}

package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

@Configuration
public class PasteServiceConfig {
    @Comment("Maximum output length in characters before truncation (0 = unlimited)")
    public int maxOutputLength = 5000;

    @Comment("Upload configuration for long command output")
    public PasteUploadConfig upload = new PasteUploadConfig();

    public PasteServiceConfig() {
    }
}

package org.winlogon.servermanager.config;

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class PasteUploadConfig {
    @Comment("Paste service endpoint URL. Leave empty to disable upload (falls back to file).")
    public String url = "https://paste.myst.rs/api/v3/paste";

    @Comment("HTTP method (default: POST)")
    public String method = "POST";

    @Comment("Request body format: json, text, or multipart")
    public String format = "json";

    @Comment("Additional HTTP headers")
    public Map<String, String> headers = new LinkedHashMap<>();

    @Comment("""
        Request body template. Use {content} as placeholder for the paste content.
        For format: json, {content} is JSON-escaped automatically.
        For format: text, {content} is inserted as-is.
        For format: multipart, {content} is the file content and the body template is ignored.
        Leave empty to send raw content.""")
    public String body = """
        {"expiresIn": "1d", "pasties": [{"title": "output.txt", "language": "text", "code": "{content}"}]}""";

    @Comment("""
        jq expression to extract the paste URL or ID from the response JSON.
        Leave empty to use the entire response body as the URL.
        Example: ._id  (paste.myst.rs)""")
    public String selector = "._id";

    @Comment("""
        Template to build the final URL from the extracted value.
        Use {result} as placeholder for the extracted value.
        Leave empty to use the extracted value directly.
        Example: https://paste.myst.rs/{result}""")
    public String urlTemplate = "https://paste.myst.rs/{result}";

    public PasteUploadConfig() {
    }
}

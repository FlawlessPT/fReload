package pt.flawless.freload.config;

import org.bukkit.configuration.file.FileConfiguration;

/** Mensagens do {@code config.yml} (chave em {@code messages}), com {@code &} convertido e placeholders. */
public final class Texts {

    private final FileConfiguration config;

    public Texts(FileConfiguration config) {
        this.config = config;
    }

    /** {@code pairs} alternam placeholder e valor: {@code get("x", "{plugin}", nome)}. */
    public String get(String key, String... pairs) {
        String text = config.getString("messages." + key, key);
        for (int i = 0; i + 1 < pairs.length; i += 2) text = text.replace(pairs[i], pairs[i + 1]);
        return text.replace('&', '§');
    }
}

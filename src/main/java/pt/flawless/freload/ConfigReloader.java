package pt.flawless.freload;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import pt.flawless.fapi.reload.Reloadable;
import pt.flawless.fapi.reload.Reloads;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reload de configs: chama o {@link Reloadable} do plugin, ou só relê o config.yml. */
final class ConfigReloader {

    private final Texts texts;

    ConfigReloader(Texts texts) {
        this.texts = texts;
    }

    /** {@code pluginName} nulo = todos os plugins {@code pt.flawless}. */
    void reload(CommandSender sender, String pluginName) {
        Map<String, Plugin> targets = new LinkedHashMap<>();
        if (pluginName != null) {
            Plugin p = FReloadCommand.find(pluginName);
            if (p == null) {
                sender.sendMessage(texts.get("inexistente", "{plugin}", pluginName));
                return;
            }
            targets.put(p.getName(), p);
        } else {
            Arrays.stream(Bukkit.getPluginManager().getPlugins())
                    .filter(FReloadCommand::isOurs)
                    .forEach(p -> targets.put(p.getName(), p));
        }
        Map<String, Reloadable> reloadables = new LinkedHashMap<>();
        for (RegisteredServiceProvider<Reloadable> rsp : Reloads.all()) {
            reloadables.put(rsp.getPlugin().getName(), rsp.getProvider());
        }
        int ok = 0;
        for (Plugin p : targets.values()) {
            try {
                Reloadable r = reloadables.get(p.getName());
                if (r != null) {
                    r.reload();
                    sender.sendMessage(texts.get("config-ok", "{plugin}", p.getName()));
                } else {
                    p.reloadConfig();
                    sender.sendMessage(texts.get("config-ficheiro", "{plugin}", p.getName()));
                }
                ok++;
            } catch (Exception e) {
                sender.sendMessage(texts.get("config-erro", "{plugin}", p.getName(), "{erro}", String.valueOf(e.getMessage())));
                p.getLogger().severe("Falha no reload de config: " + e);
            }
        }
        if (targets.isEmpty()) sender.sendMessage(texts.get("config-nenhum"));
        else sender.sendMessage(texts.get("config-resumo", "{n}", String.valueOf(ok)));
    }
}

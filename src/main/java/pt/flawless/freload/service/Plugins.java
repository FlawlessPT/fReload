package pt.flawless.freload.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Consulta de plugins carregados. */
public final class Plugins {

    private Plugins() {
    }

    /** Plugin carregado pelo nome, sem distinguir maiúsculas; nulo se não existir. */
    public static Plugin find(String name) {
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /** Só os plugins do pacote {@code pt.flawless} se recarregam. */
    public static boolean isOurs(Plugin p) {
        return p.getPluginMeta().getMainClass().startsWith("pt.flawless.");
    }
}

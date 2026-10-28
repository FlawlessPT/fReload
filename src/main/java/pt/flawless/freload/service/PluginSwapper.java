package pt.flawless.freload.service;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recarrega o jar de um plugin e dos seus dependentes sem o /reload global.
 *
 * <p>Verificado com javap no Paper 1.21.11: o {@code disablePlugin} desliga, cancela tarefas, tira os
 * serviços, listeners e canais e fecha o ClassLoader, mas NÃO tira o plugin de {@code plugins} /
 * {@code lookupNames} do {@code PaperPluginInstanceManager} nem os comandos do CommandMap; é o que
 * se faz aqui, por reflexão.
 */
public final class PluginSwapper {

    public static final class SwapException extends Exception {
        public SwapException(String message) {
            super(message);
        }

        public SwapException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Set<String> blocked;

    public PluginSwapper(Set<String> blockedLowercase) {
        this.blocked = blockedLowercase;
    }

    public boolean isBlocked(Plugin p) {
        return blocked.contains(p.getName().toLowerCase(Locale.ROOT));
    }

    /** Plugins do conjunto que não se podem recarregar (bloqueados ou de terceiros). */
    public List<String> refused(List<Plugin> set) {
        List<String> out = new ArrayList<>();
        for (Plugin p : set) if (isBlocked(p) || !Plugins.isOurs(p)) out.add(p.getName());
        return out;
    }

    /** O alvo mais os dependentes transitivos (depend e softdepend), do mais dependente ao alvo. */
    public List<Plugin> affected(Plugin target) {
        Plugin[] all = Bukkit.getPluginManager().getPlugins();
        Set<Plugin> set = new LinkedHashSet<>();
        set.add(target);
        boolean grew;
        do {
            grew = false;
            Set<String> names = lowerNames(set);
            for (Plugin p : all) {
                if (!set.contains(p) && deps(p).stream().anyMatch(d -> names.contains(d.toLowerCase(Locale.ROOT)))) {
                    set.add(p);
                    grew = true;
                }
            }
        } while (grew);
        List<Plugin> enableOrder = new ArrayList<>();
        for (Plugin p : set) visit(p, set, enableOrder, new HashSet<>());
        Collections.reverse(enableOrder);
        return enableOrder;
    }

    private static void visit(Plugin p, Set<Plugin> set, List<Plugin> out, Set<Plugin> seen) {
        if (out.contains(p) || !seen.add(p)) return;
        for (String d : deps(p)) {
            for (Plugin q : set) if (q.getName().equalsIgnoreCase(d)) visit(q, set, out, seen);
        }
        out.add(p);
    }

    private static List<String> deps(Plugin p) {
        List<String> d = new ArrayList<>(p.getPluginMeta().getPluginDependencies());
        d.addAll(p.getPluginMeta().getPluginSoftDependencies());
        return d;
    }

    private static Set<String> lowerNames(Set<Plugin> plugins) {
        Set<String> s = new HashSet<>();
        for (Plugin p : plugins) s.add(p.getName().toLowerCase(Locale.ROOT));
        return s;
    }

    public void reload(Plugin target) throws SwapException {
        List<Plugin> order = affected(target);
        List<String> refused = refused(order);
        if (!refused.isEmpty()) throw new SwapException("bloqueados ou de terceiros: " + String.join(", ", refused));

        // Antes de desligar nada: todos os jars têm de existir.
        Map<Plugin, File> files = new LinkedHashMap<>();
        for (Plugin p : order) {
            File f = jarOf(p);
            if (!f.isFile()) throw new SwapException("jar de " + p.getName() + " não encontrado: " + f);
            files.put(p, f);
        }

        for (Plugin p : order) Bukkit.getPluginManager().disablePlugin(p);

        List<Plugin> enableOrder = new ArrayList<>(order);
        Collections.reverse(enableOrder);
        List<String> pending = new ArrayList<>();
        for (Plugin p : enableOrder) pending.add(p.getName());
        try {
            for (Plugin old : enableOrder) {
                swap(old, files.get(old));
                pending.remove(old.getName());
            }
        } catch (Exception e) {
            throw new SwapException(e.getMessage() + " (ficaram desligados: " + String.join(", ", pending) + ")", e);
        } finally {
            syncCommands();
        }
    }

    private void swap(Plugin old, File jar) throws Exception {
        removeFromManager(old);
        Bukkit.getCommandMap().getKnownCommands().values()
                .removeIf(c -> c instanceof PluginIdentifiableCommand pic && pic.getPlugin() == old);
        Plugin fresh = Bukkit.getPluginManager().loadPlugin(jar);
        if (fresh == null) throw new IllegalStateException(old.getName() + " não carregou");
        Bukkit.getPluginManager().enablePlugin(fresh);
        if (!fresh.isEnabled()) throw new IllegalStateException(old.getName() + " não ficou ativo (ver a consola)");
    }

    /** Tira o plugin antigo das listas internas do PaperPluginInstanceManager (nomes verificados com javap). */
    private static void removeFromManager(Plugin old) throws Exception {
        Object manager = Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl")
                .getMethod("getInstance").invoke(null);
        Field im = manager.getClass().getDeclaredField("instanceManager");
        im.setAccessible(true);
        Object instance = im.get(manager);
        Field plugins = instance.getClass().getDeclaredField("plugins");
        plugins.setAccessible(true);
        ((List<?>) plugins.get(instance)).remove(old);
        Field names = instance.getClass().getDeclaredField("lookupNames");
        names.setAccessible(true);
        ((Map<?, ?>) names.get(instance)).values().removeIf(v -> v == old);
    }

    /** O ficheiro original; se o Paper apontar para a cópia remapeada, usa o jar na pasta dos plugins. */
    private static File jarOf(Plugin p) throws SwapException {
        try {
            Method m = JavaPlugin.class.getDeclaredMethod("getFile");
            m.setAccessible(true);
            File f = (File) m.invoke(p);
            if (f.getParentFile() != null && f.getParentFile().getName().equals(".paper-remapped")) {
                return new File(p.getDataFolder().getParentFile(), f.getName());
            }
            return f;
        } catch (ReflectiveOperationException e) {
            throw new SwapException("não consegui descobrir o jar de " + p.getName(), e);
        }
    }

    /** Refaz a árvore de comandos do Brigadier (CraftServer.syncCommands, fora da API). */
    private static void syncCommands() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException ignored) {
            // sem sync os comandos novos funcionam mas o tab-complete do cliente pode ficar desatualizado
        }
    }
}

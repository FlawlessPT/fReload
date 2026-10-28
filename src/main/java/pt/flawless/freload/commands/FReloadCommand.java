package pt.flawless.freload.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import pt.flawless.fapi.messages.Messages;
import pt.flawless.fapi.reload.Reloadable;
import pt.flawless.fapi.reload.Reloads;
import pt.flawless.freload.FReload;
import pt.flawless.freload.config.Texts;
import pt.flawless.freload.service.ConfigReloader;
import pt.flawless.freload.service.PluginSwapper;
import pt.flawless.freload.service.Plugins;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code /freload}: {@code config [plugin]}, {@code plugin <plugin>}, {@code listar}, {@code help}. */
public final class FReloadCommand implements CommandExecutor, TabCompleter {

    private final FReload main;
    private final Texts texts;
    private final ConfigReloader configReloader;
    private final PluginSwapper swapper;

    public FReloadCommand(FReload main, Texts texts) {
        this.main = main;
        this.texts = texts;
        this.configReloader = new ConfigReloader(texts);
        Set<String> blocked = main.getConfig().getStringList("blocked").stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        this.swapper = new PluginSwapper(blocked);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("freload.admin")) {
            Messages.noPermission(sender);
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(texts.get("uso"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "config" -> configReloader.reload(sender, args.length > 1 ? args[1] : null);
            case "plugin" -> reloadPlugin(sender, args);
            case "listar", "list" -> list(sender);
            case "help" -> {
                sender.sendMessage("§e/freload config [plugin] §7- Recarrega a configuração de um plugin ou de todos");
                sender.sendMessage("§e/freload plugin <plugin> §7- Descarrega e carrega de novo o jar de um plugin");
                sender.sendMessage("§e/freload listar §7- Lista os plugins e o tipo de reload");
                sender.sendMessage("§e/freload help §7- Mostra esta lista");
            }
            default -> sender.sendMessage(texts.get("uso"));
        }
        return true;
    }

    private void reloadPlugin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(texts.get("plugin-uso"));
            return;
        }
        Plugin target = Plugins.find(args[1]);
        if (target == null) {
            sender.sendMessage(texts.get("inexistente", "{plugin}", args[1]));
            return;
        }
        List<Plugin> affected = swapper.affected(target);
        String names = affected.stream().map(Plugin::getName).collect(Collectors.joining(", "));
        List<String> refused = swapper.refused(affected);
        if (!refused.isEmpty()) {
            sender.sendMessage(texts.get("plugin-bloqueado", "{lista}", String.join(", ", refused)));
            return;
        }
        sender.sendMessage(texts.get("plugin-inicio", "{lista}", names));
        try {
            swapper.reload(target);
            sender.sendMessage(texts.get("plugin-ok", "{n}", String.valueOf(affected.size()), "{lista}", names));
        } catch (PluginSwapper.SwapException e) {
            sender.sendMessage(texts.get("plugin-erro", "{erro}", String.valueOf(e.getMessage())));
            main.error("reload de " + target.getName() + " falhou: " + e.getMessage());
            if (e.getCause() != null) e.getCause().printStackTrace();
        }
    }

    private void list(CommandSender sender) {
        Set<String> withReload = new HashSet<>();
        for (RegisteredServiceProvider<Reloadable> rsp : Reloads.all()) withReload.add(rsp.getPlugin().getName());
        sender.sendMessage(texts.get("listar-cabecalho"));
        String line = Arrays.stream(Bukkit.getPluginManager().getPlugins())
                .filter(Plugins::isOurs)
                .map(p -> (swapper.isBlocked(p) ? "§c" : withReload.contains(p.getName()) ? "§a" : "§7") + p.getName())
                .collect(Collectors.joining("§7, "));
        sender.sendMessage(line);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("freload.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("config", "plugin", "listar", "help"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("config") || args[0].equalsIgnoreCase("plugin"))) {
            for (Plugin p : Bukkit.getPluginManager().getPlugins()) if (Plugins.isOurs(p)) out.add(p.getName());
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}

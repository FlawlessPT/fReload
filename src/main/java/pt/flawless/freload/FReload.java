package pt.flawless.freload;

import org.bukkit.plugin.java.JavaPlugin;
import pt.flawless.fapi.enums.EMessageTypeEnum;
import pt.flawless.fapi.logs.FConsoleLogger;
import pt.flawless.freload.commands.FReloadCommand;
import pt.flawless.freload.config.Texts;

public class FReload extends JavaPlugin {
    private final FConsoleLogger consoleLogger = new FConsoleLogger(this.getName());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Texts texts = new Texts(getConfig());
        FReloadCommand command = new FReloadCommand(this, texts);
        getCommand("freload").setExecutor(command);
        getCommand("freload").setTabCompleter(command);
        consoleLogger.sendEnablePluginMessage();
    }

    @Override
    public void onDisable() {
        consoleLogger.sendDisablePluginMessage();
    }

    public void error(String message) {
        consoleLogger.sendMessage(message, EMessageTypeEnum.ERROR);
    }
}

package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;

public class TbansCommand implements SimpleCommand {
    private final String version;
    private final String build;
    private final LanguageManager languageManager;
    private final Runnable reloadTask;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public TbansCommand(String version, String build, LanguageManager languageManager, Runnable reloadTask) {
        this.version = version;
        this.build = build;
        this.languageManager = languageManager;
        this.reloadTask = reloadTask;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!invocation.source().hasPermission("tbans.admin")) {
                invocation.source().sendMessage(mm.deserialize("<red>No permission."));
                return;
            }
            reloadTask.run();
            invocation.source().sendMessage(mm.deserialize("<green>Plugin reloaded successfully!"));
            return;
        }

        invocation.source().sendMessage(
                mm.deserialize("<gray>--- <gradient:#ff5555:#aa0000><bold>TBans Info</bold></gradient> ---"));
        invocation.source().sendMessage(mm.deserialize("<gray>Version: <white>" + version));
        invocation.source().sendMessage(mm.deserialize("<gray>Build Number: <white>#" + build));
        invocation.source().sendMessage(mm.deserialize("<gray>Platform: <white>Velocity"));
        invocation.source().sendMessage(mm.deserialize("<gray>Authors: <white>treppi"));
        invocation.source().sendMessage(mm.deserialize("<gray>-----------------------"));
        invocation.source()
                .sendMessage(mm.deserialize("<gray>Use <white>/tbans reload <gray>to reload configuration."));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1 && invocation.source().hasPermission("tbans.admin")) {
            return List.of("reload");
        }
        return List.of();
    }
}

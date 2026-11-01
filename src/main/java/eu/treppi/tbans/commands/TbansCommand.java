package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TbansCommand implements SimpleCommand {
    private static final List<String> STORAGE_TYPES = List.of("json", "mongodb");

    private final String version;
    private final String build;
    private final LanguageManager languageManager;
    private final Runnable reloadTask;
    private final BiFunction<String, String, String> migrateTask;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public TbansCommand(String version, String build, LanguageManager languageManager, Runnable reloadTask,
            BiFunction<String, String, String> migrateTask) {
        this.version = version;
        this.build = build;
        this.languageManager = languageManager;
        this.reloadTask = reloadTask;
        this.migrateTask = migrateTask;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource source = invocation.source();

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!source.hasPermission("tbans.admin")) {
                source.sendMessage(mm.deserialize("<red>No permission."));
                return;
            }
            reloadTask.run();
            source.sendMessage(mm.deserialize("<green>Plugin reloaded successfully!"));
            return;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("migrate")) {
            if (!source.hasPermission("tbans.admin")) {
                source.sendMessage(mm.deserialize("<red>No permission."));
                return;
            }
            handleMigrate(source, args);
            return;
        }

        source.sendMessage(
                mm.deserialize("<gray>--- <gradient:#ff5555:#aa0000><bold>TBans Info</bold></gradient> ---"));
        source.sendMessage(mm.deserialize("<gray>Version: <white>" + version));
        source.sendMessage(mm.deserialize("<gray>Build Number: <white>#" + build));
        source.sendMessage(mm.deserialize("<gray>Platform: <white>Velocity"));
        source.sendMessage(mm.deserialize("<gray>Authors: <white>treppi"));
        source.sendMessage(mm.deserialize("<gray>-----------------------"));
        source.sendMessage(mm.deserialize("<gray>Use <white>/tbans reload <gray>to reload configuration."));
        if (source.hasPermission("tbans.admin")) {
            source.sendMessage(mm.deserialize(
                    "<gray>Use <white>/tbans migrate [from] [to] <gray>to copy data between storages (default: json → mongodb)."));
        }
    }

    private void handleMigrate(CommandSource source, String[] args) {
        String from = args.length > 1 ? normalize(args[1]) : "json";
        String to = args.length > 2 ? normalize(args[2]) : "mongodb";

        if (!STORAGE_TYPES.contains(from) || !STORAGE_TYPES.contains(to)) {
            source.sendMessage(mm.deserialize("<red>Usage: /tbans migrate [json|mongodb] [json|mongodb]"));
            return;
        }
        if (from.equals(to)) {
            source.sendMessage(mm.deserialize("<red>Source and target storage must be different."));
            return;
        }

        source.sendMessage(mm.deserialize("<gray>Migrating data from <white>" + from + "</white> to <white>" + to
                + "</white>... this may take a moment."));

        CompletableFuture.supplyAsync(() -> migrateTask.apply(from, to)).whenComplete((summary, error) -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                source.sendMessage(mm.deserialize("<red>Migration failed: <white>"
                        + mm.escapeTags(String.valueOf(cause.getMessage()))));
                cause.printStackTrace();
                return;
            }
            source.sendMessage(mm.deserialize("<green>Migration complete: <white>" + summary));
            if (to.equals("mongodb")) {
                source.sendMessage(mm.deserialize("<gray>Set <white>storage-type: mongodb</white> in config.yml "
                        + "and run <white>/tbans reload</white> to switch to MongoDB."));
            }
        });
    }

    private static String normalize(String type) {
        String t = type.toLowerCase();
        return t.equals("mongo") ? "mongodb" : t;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("tbans.admin")) {
            return List.of();
        }
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return filter(Stream.of("reload", "migrate"), prefix);
        }
        if (args[0].equalsIgnoreCase("migrate") && args.length <= 3) {
            return filter(STORAGE_TYPES.stream(), args[args.length - 1].toLowerCase());
        }
        return List.of();
    }

    private static List<String> filter(Stream<String> options, String prefix) {
        return options.filter(o -> o.startsWith(prefix)).collect(Collectors.toList());
    }
}

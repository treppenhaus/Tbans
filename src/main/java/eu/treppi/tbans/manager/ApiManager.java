package eu.treppi.tbans.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.util.TimeUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Executors;

public class ApiManager {
    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final IpLogManager ipLogManager;
    private HttpServer serverSide;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    public ApiManager(ProxyServer server, BanManager banManager, LanguageManager languageManager,
            ConfigManager configManager, IpLogManager ipLogManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
        this.configManager = configManager;
        this.ipLogManager = ipLogManager;
    }

    public void start() {
        int port = configManager.getApiPort();
        try {
            serverSide = HttpServer.create(new InetSocketAddress(port), 0);
            serverSide.createContext("/api/alts/", new AltsHandler());
            serverSide.createContext("/api/ban", new BanHandler());
            serverSide.createContext("/api/kick", new KickHandler());
            serverSide.createContext("/api/unban", new UnbanHandler());
            serverSide.createContext("/api/history/", new HistoryHandler());

            serverSide.setExecutor(Executors.newCachedThreadPool());
            serverSide.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (serverSide != null) {
            serverSide.stop(0);
        }
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        String expectedToken = configManager.getApiToken();
        return authHeader != null && authHeader.startsWith("Bearer ") && authHeader.substring(7).equals(expectedToken);
    }

    private void sendResponse(HttpExchange exchange, int code, Object data) throws IOException {
        byte[] response = GSON.toJson(data).getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private Map<String, Object> parseBody(HttpExchange exchange) {
        try {
            return GSON.fromJson(new InputStreamReader(exchange.getRequestBody()), Map.class);
        } catch (Exception e) {
            return null;
        }
    }

    private class AltsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 4) {
                sendResponse(exchange, 400, Map.of("error", "No player provided"));
                return;
            }
            String target = parts[3];

            banManager.resolveUuid(target).thenAccept(uuid -> {
                try {
                    if (uuid == null) {
                        sendResponse(exchange, 404, Map.of("error", "Player not found"));
                        return;
                    }
                    int daysLimit = configManager.getAltLinkDays();
                    Map<UUID, Long> directAlts = ipLogManager.getAlts(uuid, daysLimit);
                    Map<UUID, Integer> recursiveAlts = ipLogManager.getRecursiveAlts(uuid, 20);

                    Map<String, Object> response = new HashMap<>();
                    response.put("player", target);
                    response.put("uuid", uuid.toString());
                    response.put("direct_alts", formatAlts(directAlts));
                    response.put("recursive_alts", formatRecursiveAlts(recursiveAlts, directAlts));
                    sendResponse(exchange, 200, response);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private class BanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            Map<String, Object> body = parseBody(exchange);
            if (body == null || !body.containsKey("target") || !body.containsKey("author")) {
                sendResponse(exchange, 400, Map.of("error", "Missing 'target' or 'author' field"));
                return;
            }

            String target = (String) body.get("target");
            String author = (String) body.get("author");
            String durationStr = (String) body.getOrDefault("duration", "");
            String reason = (String) body.getOrDefault("reason", "No reason provided.");

            long duration = durationStr.isEmpty() ? -1 : TimeUtils.parseTime(durationStr);
            String timeStr = duration == -1 ? "Permanent" : durationStr;

            banManager.resolveUuid(target).thenAccept(uuid -> {
                try {
                    if (uuid == null) {
                        sendResponse(exchange, 404, Map.of("error", "Player not found"));
                        return;
                    }

                    banManager.resolveUuid(author).thenAccept(authorUuid -> {
                        UUID actualAuthor = authorUuid != null ? authorUuid : CONSOLE_UUID;

                        // Execute ban logic
                        Optional<Player> targetPlayer = server.getPlayer(uuid);
                        if (targetPlayer.isPresent()) {
                            String disconnectMsg = languageManager.getMessage("ban.disconnect_screen")
                                    .replace("{duration}", timeStr)
                                    .replace("{reason}", reason);
                            targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));
                        }

                        banManager.banPlayer(uuid, actualAuthor, duration, reason);
                        broadcast("ban", target, author, timeStr, reason);

                        try {
                            sendResponse(exchange, 200,
                                    Map.of("success", true, "message", "Player banned successfully"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private class KickHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            Map<String, Object> body = parseBody(exchange);
            if (body == null || !body.containsKey("target") || !body.containsKey("author")) {
                sendResponse(exchange, 400, Map.of("error", "Missing 'target' or 'author' field"));
                return;
            }

            String target = (String) body.get("target");
            String author = (String) body.get("author");
            String reason = (String) body.getOrDefault("reason", "No reason provided.");

            banManager.resolveUuid(target).thenAccept(uuid -> {
                try {
                    if (uuid == null) {
                        sendResponse(exchange, 404, Map.of("error", "Player not found"));
                        return;
                    }

                    Optional<Player> targetPlayer = server.getPlayer(uuid);
                    if (targetPlayer.isEmpty()) {
                        sendResponse(exchange, 400, Map.of("error", "Player is not online"));
                        return;
                    }

                    banManager.resolveUuid(author).thenAccept(authorUuid -> {
                        UUID actualAuthor = authorUuid != null ? authorUuid : CONSOLE_UUID;

                        String disconnectMsg = languageManager.getMessage("kick.disconnect_screen")
                                .replace("{reason}", reason);
                        targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));

                        banManager.kickPlayer(uuid, actualAuthor, reason);
                        broadcast("kick", target, author, "", reason);

                        try {
                            sendResponse(exchange, 200,
                                    Map.of("success", true, "message", "Player kicked successfully"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private class UnbanHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, Map.of("error", "Method Not Allowed"));
                return;
            }

            Map<String, Object> body = parseBody(exchange);
            if (body == null || !body.containsKey("target") || !body.containsKey("author")) {
                sendResponse(exchange, 400, Map.of("error", "Missing 'target' or 'author' field"));
                return;
            }

            String target = (String) body.get("target");
            String author = (String) body.get("author");
            String reason = (String) body.getOrDefault("reason", "No reason provided.");

            banManager.resolveUuid(target).thenAccept(uuid -> {
                try {
                    if (uuid == null) {
                        sendResponse(exchange, 404, Map.of("error", "Player not found"));
                        return;
                    }

                    banManager.resolveUuid(author).thenAccept(authorUuid -> {
                        UUID actualAuthor = authorUuid != null ? authorUuid : CONSOLE_UUID;
                        banManager.unbanPlayer(uuid, actualAuthor, reason);
                        broadcast("unban", target, author, "", reason);

                        try {
                            sendResponse(exchange, 200,
                                    Map.of("success", true, "message", "Player unbanned successfully"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                sendResponse(exchange, 401, Map.of("error", "Unauthorized"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length < 4) {
                sendResponse(exchange, 400, Map.of("error", "No player provided"));
                return;
            }
            String target = parts[3];

            banManager.resolveUuid(target).thenAccept(uuid -> {
                try {
                    if (uuid == null) {
                        sendResponse(exchange, 404, Map.of("error", "Player not found"));
                        return;
                    }
                    sendResponse(exchange, 200, Map.of(
                            "player", target,
                            "uuid", uuid.toString(),
                            "history", banManager.getEvents(uuid),
                            "is_banned", banManager.isBanned(uuid)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    private void broadcast(String type, String target, String author, String duration, String reason) {
        String msg = languageManager.getMessage(type + ".broadcast")
                .replace("{player}", target)
                .replace("{executor}", author)
                .replace("{duration}", duration)
                .replace("{reason}", reason);

        for (Player p : server.getAllPlayers()) {
            if (p.hasPermission("tbans.notify")) {
                p.sendMessage(mm.deserialize(msg));
            }
        }
    }

    private Map<String, Object> formatAlts(Map<UUID, Long> alts) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : alts.entrySet()) {
            map.put(entry.getKey().toString(), Map.of(
                    "name", banManager.getNameFromUuid(entry.getKey()),
                    "first_seen", entry.getValue()));
        }
        return map;
    }

    private Map<String, Object> formatRecursiveAlts(Map<UUID, Integer> recursive, Map<UUID, Long> direct) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : recursive.entrySet()) {
            if (direct.containsKey(entry.getKey()))
                continue;
            map.put(entry.getKey().toString(), Map.of(
                    "name", banManager.getNameFromUuid(entry.getKey()),
                    "hops", entry.getValue()));
        }
        return map;
    }
}

package eu.treppi.tbans.storage;

import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.IpLogManager;
import org.bson.Document;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Stores data in MongoDB. Collections:
 * <ul>
 * <li>player_events: {_id: uuid, events: [...]}</li>
 * <li>ip_events: {_id: ipHash, events: [...]}</li>
 * <li>names: {_id: lowercase name, uuid}</li>
 * <li>display_names: {_id: uuid, name}</li>
 * <li>ip_logs: {_id: ipHash, iphashvalue, players: [...]}</li>
 * </ul>
 */
public class MongoStorage implements Storage {
    private static final Gson GSON = new Gson();
    private static final JsonWriterSettings JSON_SETTINGS = JsonWriterSettings.builder()
            .outputMode(JsonMode.RELAXED).build();
    private static final ReplaceOptions UPSERT = new ReplaceOptions().upsert(true);
    private static final int BATCH_SIZE = 1000;

    private final MongoClient client;
    private final MongoCollection<Document> playerEvents;
    private final MongoCollection<Document> ipEvents;
    private final MongoCollection<Document> names;
    private final MongoCollection<Document> displayNames;
    private final MongoCollection<Document> ipLogs;

    /** Connects and pings the server; throws if MongoDB is not reachable. */
    public MongoStorage(String uri, String databaseName) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build();
        this.client = MongoClients.create(settings);
        try {
            MongoDatabase db = client.getDatabase(databaseName);
            db.runCommand(new Document("ping", 1));
            this.playerEvents = db.getCollection("player_events");
            this.ipEvents = db.getCollection("ip_events");
            this.names = db.getCollection("names");
            this.displayNames = db.getCollection("display_names");
            this.ipLogs = db.getCollection("ip_logs");
        } catch (RuntimeException e) {
            client.close();
            throw e;
        }
    }

    @Override
    public String getName() {
        return "mongodb";
    }

    // ---- Loading ----

    @Override
    public Map<UUID, List<BanEvent>> loadPlayerEvents() {
        Map<UUID, List<BanEvent>> map = new HashMap<>();
        for (Document doc : playerEvents.find()) {
            EventsDoc parsed = GSON.fromJson(doc.toJson(JSON_SETTINGS), EventsDoc.class);
            map.put(UUID.fromString(parsed._id), parsed.events != null ? parsed.events : new ArrayList<>());
        }
        return map;
    }

    @Override
    public Map<String, List<BanEvent>> loadIpEvents() {
        Map<String, List<BanEvent>> map = new HashMap<>();
        for (Document doc : ipEvents.find()) {
            EventsDoc parsed = GSON.fromJson(doc.toJson(JSON_SETTINGS), EventsDoc.class);
            map.put(parsed._id, parsed.events != null ? parsed.events : new ArrayList<>());
        }
        return map;
    }

    @Override
    public Map<String, UUID> loadNameIndex() {
        Map<String, UUID> map = new HashMap<>();
        for (Document doc : names.find()) {
            map.put(doc.getString("_id"), UUID.fromString(doc.getString("uuid")));
        }
        return map;
    }

    @Override
    public Map<UUID, String> loadDisplayNames() {
        Map<UUID, String> map = new HashMap<>();
        for (Document doc : displayNames.find()) {
            map.put(UUID.fromString(doc.getString("_id")), doc.getString("name"));
        }
        return map;
    }

    @Override
    public List<IpLogManager.IpEntry> loadIpLogs() {
        List<IpLogManager.IpEntry> list = new ArrayList<>();
        for (Document doc : ipLogs.find()) {
            list.add(GSON.fromJson(doc.toJson(JSON_SETTINGS), IpLogManager.IpEntry.class));
        }
        return list;
    }

    // ---- Incremental writes ----

    @Override
    public void savePlayerEvents(UUID target, List<BanEvent> events, Map<UUID, List<BanEvent>> all) {
        playerEvents.replaceOne(Filters.eq("_id", target.toString()), eventsDoc(target.toString(), events), UPSERT);
    }

    @Override
    public void saveIpEvents(String ipHash, List<BanEvent> events, Map<String, List<BanEvent>> all) {
        ipEvents.replaceOne(Filters.eq("_id", ipHash), eventsDoc(ipHash, events), UPSERT);
    }

    @Override
    public void saveName(UUID uuid, String name, Map<String, UUID> nameIndex, Map<UUID, String> displayNamesMap) {
        String lower = name.toLowerCase();
        names.replaceOne(Filters.eq("_id", lower), nameDoc(lower, uuid), UPSERT);
        displayNames.replaceOne(Filters.eq("_id", uuid.toString()), displayNameDoc(uuid, name), UPSERT);
    }

    @Override
    public void saveIpEntry(IpLogManager.IpEntry entry, List<IpLogManager.IpEntry> all) {
        ipLogs.replaceOne(Filters.eq("_id", entry.getIpHash()), ipEntryDoc(entry), UPSERT);
    }

    // ---- Bulk import (migration) ----

    @Override
    public void importAll(StorageData data) {
        List<WriteModel<Document>> ops = new ArrayList<>();
        data.playerEvents().forEach((uuid, events) -> ops.add(replace(uuid.toString(), eventsDoc(uuid.toString(), events))));
        bulk(playerEvents, ops);

        data.ipEvents().forEach((hash, events) -> ops.add(replace(hash, eventsDoc(hash, events))));
        bulk(ipEvents, ops);

        data.nameIndex().forEach((lower, uuid) -> ops.add(replace(lower, nameDoc(lower, uuid))));
        bulk(names, ops);

        data.displayNames().forEach((uuid, name) -> ops.add(replace(uuid.toString(), displayNameDoc(uuid, name))));
        bulk(displayNames, ops);

        data.ipLogs().forEach(entry -> ops.add(replace(entry.getIpHash(), ipEntryDoc(entry))));
        bulk(ipLogs, ops);
    }

    private static ReplaceOneModel<Document> replace(String id, Document doc) {
        return new ReplaceOneModel<>(Filters.eq("_id", id), doc, UPSERT);
    }

    /** Executes and clears the given operations in batches. */
    private static void bulk(MongoCollection<Document> collection, List<WriteModel<Document>> ops) {
        for (int i = 0; i < ops.size(); i += BATCH_SIZE) {
            collection.bulkWrite(ops.subList(i, Math.min(i + BATCH_SIZE, ops.size())),
                    new BulkWriteOptions().ordered(false));
        }
        ops.clear();
    }

    @Override
    public void close() {
        client.close();
    }

    // ---- Document mapping ----

    private static Document eventsDoc(String id, List<BanEvent> events) {
        return Document.parse(GSON.toJson(new EventsDoc(id, events)));
    }

    private static Document nameDoc(String lowerName, UUID uuid) {
        return new Document("_id", lowerName).append("uuid", uuid.toString());
    }

    private static Document displayNameDoc(UUID uuid, String name) {
        return new Document("_id", uuid.toString()).append("name", name);
    }

    private static Document ipEntryDoc(IpLogManager.IpEntry entry) {
        Document doc = Document.parse(GSON.toJson(entry));
        doc.put("_id", entry.getIpHash());
        return doc;
    }

    private static class EventsDoc {
        String _id;
        List<BanEvent> events;

        EventsDoc(String id, List<BanEvent> events) {
            this._id = id;
            this.events = events;
        }
    }
}

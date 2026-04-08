package eu.treppi.tbans.manager;

import java.util.UUID;

public class BanEvent {
    public enum Type {
        BAN, UNBAN, KICK
    }

    private final Type type;
    private final UUID targetUUID;
    private final UUID executorUUID;
    private final long timestamp;
    private final long expiry;
    private final String reason;

    public BanEvent(Type type, UUID targetUUID, UUID executorUUID, long timestamp, long expiry, String reason) {
        this.type = type;
        this.targetUUID = targetUUID;
        this.executorUUID = executorUUID;
        this.timestamp = timestamp;
        this.expiry = expiry;
        this.reason = reason;
    }

    public Type getType() {
        return type;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public UUID getExecutorUUID() {
        return executorUUID;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getExpiry() {
        return expiry;
    }

    public String getReason() {
        return reason;
    }
}

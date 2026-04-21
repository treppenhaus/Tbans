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
    private String code;

    public BanEvent(Type type, UUID targetUUID, UUID executorUUID, long timestamp, long expiry, String reason) {
        this(type, targetUUID, executorUUID, timestamp, expiry, reason, null);
    }

    public BanEvent(Type type, UUID targetUUID, UUID executorUUID, long timestamp, long expiry, String reason, String code) {
        this.type = type;
        this.targetUUID = targetUUID;
        this.executorUUID = executorUUID;
        this.timestamp = timestamp;
        this.expiry = expiry;
        this.reason = reason;
        this.code = (code == null && type == Type.BAN) ? generateCode() : code;
    }

    public void ensureCode() {
        if (this.code == null && type == Type.BAN) {
            this.code = generateCode();
        }
    }

    public void setCode(String code) {
        this.code = code;
    }

    private String generateCode() {
        String chars = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
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

    public String getCode() {
        return code;
    }
}

# TBans JSON API

TBans features a minimalistic JSON API for external integrations. Authentication is required via a Bearer Token.

## Authentication
All requests must include the `Authorization` header with a Bearer Token.
```http
Authorization: Bearer <your_api_token>
```
*Your token is auto-generated in `config.yml` on the first run.*

## Base Configuration
- **Default Port:** `8869`
- **CORS:** Enabled (`Access-Control-Allow-Origin: *`)
- **Content-Type:** `application/json`

---

## Endpoints

### 1. Get Linked Accounts (Alts)
Returns potential secondary accounts linked via IP hashing.

- **URL:** `/api/alts/<player_name_or_uuid>`
- **Method:** `GET`
- **Success Response (200 OK):**
```json
{
  "player": "treppi",
  "uuid": "...",
  "direct_alts": {
    "uuid1": { "name": "alt1", "first_seen": 1620000000000 }
  },
  "recursive_alts": {
    "uuid2": { "name": "alt2", "hops": 2 }
  }
}
```

### 2. Get Punishment History
Returns the full history of bans, unbans, and kicks for a player.

- **URL:** `/api/history/<player_name_or_uuid>`
- **Method:** `GET`
- **Success Response (200 OK):**
```json
{
  "player": "treppi",
  "uuid": "...",
  "is_banned": false,
  "history": [
    {
      "type": "BAN",
      "targetUUID": "...",
      "executorUUID": "...",
      "timestamp": 1620000000000,
      "expiry": -1,
      "reason": "Example Reason"
    }
  ]
}
```

### 3. Ban a Player
Creates a new ban record and disconnects the player if online.

- **URL:** `/api/ban`
- **Method:** `POST`
- **Body Parameters:**
  - `target` (String, Required): Name or UUID of the player to ban.
  - `author` (String, Required): Name or UUID of the person/system executing the ban.
  - `duration` (String, Optional): Time string (e.g., `7d`, `1h`, `30m`). Permanent if omitted.
  - `reason` (String, Optional): Reason for the ban.
- **Example Payload:**
```json
{
  "target": "player123",
  "author": "WebDashboard",
  "duration": "24h",
  "reason": "Rule violation"
}
```

### 4. Kick a Player
Disconnects an online player from the proxy and logs the action.

- **URL:** `/api/kick`
- **Method:** `POST`
- **Body Parameters:**
  - `target` (String, Required): Name or UUID of the player to kick.
  - `author` (String, Required): Name or UUID of the person/system executing the kick.
  - `reason` (String, Optional): Reason for the kick.

### 5. Unban a Player
Removes a player's active ban.

- **URL:** `/api/unban`
- **Method:** `POST`
- **Body Parameters:**
  - `target` (String, Required): Name or UUID of the player to unban.
  - `author` (String, Required): Name or UUID of the person/system executing the unban.
  - `reason` (String, Optional): Internal reason for the unban.

---

## Error Handling
The API returns standard HTTP status codes:
- `400 Bad Request`: Missing mandatory fields or invalid body.
- `401 Unauthorized`: Missing or incorrect Bearer token.
- `404 Not Found`: Player could not be resolved.
- `405 Method Not Allowed`: Used `GET` on a `POST` endpoint or vice-versa.
- `500 Internal Server Error`: Unexpected server issue.

**Error Response Body:**
```json
{
  "error": "Error message description"
}
```

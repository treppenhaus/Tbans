#!/usr/bin/env python3
"""
Reads bans.json (Tbans format with player names) and converts all usernames
to UUIDs, outputting uuid-bans.json.

UUID resolution order:
  1. libertybans_names table from bans.sql (already has uuid <-> name mappings)
  2. Mojang API fallback for any names not found locally
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error

# Reuse the SQL parsing functions from convert_bans.py
from convert_bans import parse_insert_values, parse_row_fields, bytes_to_uuid


def build_name_to_uuid_map(sql_file):
    """Build a lowercase_name -> UUID mapping from libertybans_names table."""
    print(f"Reading {sql_file} for name mappings...")
    with open(sql_file, 'rb') as f:
        raw_data = f.read()

    name_rows = parse_insert_values(raw_data, 'libertybans_names')
    name_to_uuid = {}  # lowercase name -> uuid string

    for row in name_rows:
        fields = parse_row_fields(row)
        if len(fields) >= 4:
            uuid_bytes = fields[0]
            name = fields[1].decode('utf-8', errors='replace') if isinstance(fields[1], bytes) else str(fields[1])
            updated = fields[3]

            uuid_str = bytes_to_uuid(uuid_bytes) if isinstance(uuid_bytes, bytes) and len(uuid_bytes) == 16 else None
            if uuid_str:
                key = name.lower()
                # Keep the most recently updated mapping
                if key not in name_to_uuid or updated > name_to_uuid[key][1]:
                    name_to_uuid[key] = (uuid_str, updated)

    # Flatten to just name -> uuid
    result = {k: v[0] for k, v in name_to_uuid.items()}
    print(f"  Loaded {len(result)} name->UUID mappings from SQL dump")
    return result


def lookup_mojang_uuid(username):
    """Look up a player's UUID via the Mojang API. Returns UUID string or None."""
    url = f"https://api.mojang.com/users/profiles/minecraft/{username}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            raw_id = data.get('id', '')
            if len(raw_id) == 32:
                # Insert dashes: 8-4-4-4-12
                return f"{raw_id[:8]}-{raw_id[8:12]}-{raw_id[12:16]}-{raw_id[16:20]}-{raw_id[20:]}"
            return raw_id
    except (urllib.error.HTTPError, urllib.error.URLError, Exception) as e:
        return None


def resolve_uuid(name, name_to_uuid, mojang_cache, use_mojang=True):
    """
    Resolve a player name to a UUID.
    Returns UUID string, or the original name if unresolvable.
    """
    # Already a UUID?
    if len(name) == 36 and name.count('-') == 4:
        return name

    key = name.lower()

    # Check local mapping first
    if key in name_to_uuid:
        return name_to_uuid[key]

    # Check Mojang cache
    if key in mojang_cache:
        return mojang_cache[key]

    # Mojang API lookup
    if use_mojang:
        print(f"  Mojang API lookup: {name}...", end=" ")
        uuid = lookup_mojang_uuid(name)
        if uuid:
            print(f"-> {uuid}")
            mojang_cache[key] = uuid
            name_to_uuid[key] = uuid  # cache for future lookups
            time.sleep(0.2)  # rate limit
            return uuid
        else:
            print("NOT FOUND")
            mojang_cache[key] = name  # cache the miss
            time.sleep(0.2)

    return name  # fallback to original name


def main():
    base_dir = os.path.dirname(os.path.abspath(__file__))
    sql_file = os.path.join(base_dir, 'bans.sql')
    bans_file = os.path.join(base_dir, 'bans.json')
    output_file = os.path.join(base_dir, 'uuid-bans.json')

    # Build name -> UUID mapping from SQL dump
    name_to_uuid = build_name_to_uuid_map(sql_file)

    # Load bans.json
    print(f"\nReading {bans_file}...")
    with open(bans_file, 'r', encoding='utf-8') as f:
        bans_data = json.load(f)
    print(f"  Loaded {len(bans_data)} player entries")

    # Convert all names to UUIDs
    print("\nConverting names to UUIDs...")
    mojang_cache = {}
    uuid_bans = {}
    resolved = 0
    unresolved = 0

    for player_key, events in bans_data.items():
        # Resolve the player key (target)
        # Use the targetName from the first event for better casing
        target_name = events[0]['targetName'] if events else player_key
        target_uuid = resolve_uuid(target_name, name_to_uuid, mojang_cache)

        new_events = []
        for event in events:
            # Resolve executor name
            executor_uuid = resolve_uuid(event['executorName'], name_to_uuid, mojang_cache)

            new_event = {
                'type': event['type'],
                'targetName': target_uuid,
                'executorName': executor_uuid,
                'timestamp': event['timestamp'],
                'expiry': event['expiry'],
                'reason': event['reason'],
            }
            new_events.append(new_event)

        # Use UUID as the key
        uuid_key = target_uuid.lower() if len(target_uuid) == 36 and target_uuid.count('-') == 4 else target_uuid.lower()
        uuid_bans[uuid_key] = new_events

        if len(target_uuid) == 36 and target_uuid.count('-') == 4:
            resolved += 1
        else:
            unresolved += 1

    print(f"\n--- Results ---")
    print(f"Resolved to UUID: {resolved}")
    print(f"Unresolved (kept as name): {unresolved}")

    # Write output
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(uuid_bans, f, indent=2, ensure_ascii=False)
    print(f"\nWrote {output_file}")


if __name__ == '__main__':
    main()

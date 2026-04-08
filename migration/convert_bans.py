#!/usr/bin/env python3
"""
Parses a LibertyBans MariaDB/MySQL SQL dump (bans.sql) and generates a bans.json
file in the Tbans plugin format.

LibertyBans schema (relevant tables):
  - libertybans_bans(id, victim)           -- active bans: punishment_id -> victim_id
  - libertybans_punishments(id, type, operator, reason, scope, start, end, track, scope_id)
      type: 0=BAN, 1=MUTE, 2=WARN, 3=KICK
      operator: binary(16) UUID of the staff member
      start/end: epoch seconds (end=0 means permanent)
  - libertybans_victims(id, type, uuid, address)
      type: 0=PLAYER, 1=ADDRESS, 2=COMPOSITE
      uuid: binary(16) UUID of the victim
  - libertybans_names(uuid, name, lower_name, updated)
      uuid: binary(16) -> most recent player name

Strategy:
  1. Read raw bytes of the SQL file
  2. Use regex to extract INSERT INTO statements for each table
  3. Parse each row, handling binary data via latin-1 encoding (1 byte = 1 char)
  4. Join tables by foreign keys
  5. Output bans.json in Tbans format
"""

import re
import json
import uuid
import struct
import sys
import os

def bytes_to_uuid(raw_bytes):
    """Convert 16 raw bytes to a UUID string."""
    if len(raw_bytes) != 16:
        return None
    return str(uuid.UUID(bytes=raw_bytes))

def parse_mysql_string_literal(s):
    """
    Unescape a MySQL string literal (the content between the outer quotes).
    Returns raw bytes (for binary fields) or a string.
    """
    result = bytearray()
    i = 0
    while i < len(s):
        if s[i] == '\\' and i + 1 < len(s):
            next_char = s[i + 1]
            if next_char == '0':
                result.append(0)
            elif next_char == 'n':
                result.append(ord('\n'))
            elif next_char == 'r':
                result.append(ord('\r'))
            elif next_char == 't':
                result.append(ord('\t'))
            elif next_char == '\\':
                result.append(ord('\\'))
            elif next_char == "'":
                result.append(ord("'"))
            elif next_char == '"':
                result.append(ord('"'))
            else:
                result.append(ord(next_char))
            i += 2
        else:
            result.append(ord(s[i]) if isinstance(s[i], str) else s[i])
            i += 1
    return bytes(result)

def parse_insert_values(raw_data, table_name):
    """
    Find INSERT INTO `table_name` VALUES and parse the rows.
    Returns a list of raw row strings.
    """
    # Find the INSERT statement
    pattern = f"INSERT INTO `{table_name}` VALUES"
    # Use bytes-level search
    pattern_bytes = pattern.encode('latin-1')
    
    idx = raw_data.find(pattern_bytes)
    if idx == -1:
        print(f"Warning: No INSERT INTO `{table_name}` found")
        return []
    
    # Move past the "VALUES\n" part
    idx = idx + len(pattern_bytes)
    
    # Now we need to parse rows until we hit a semicolon
    # Each row is: (val1, val2, ...) followed by , or ;
    rows = []
    while idx < len(raw_data):
        # Skip whitespace
        while idx < len(raw_data) and raw_data[idx:idx+1] in (b' ', b'\n', b'\r', b'\t'):
            idx += 1
        
        if idx >= len(raw_data):
            break
            
        if raw_data[idx:idx+1] != b'(':
            break
        
        # Parse one row: find matching closing paren
        # We need to handle quoted strings which may contain parens
        idx += 1  # skip opening (
        row_start = idx
        in_string = False
        escape_next = False
        depth = 0
        
        while idx < len(raw_data):
            ch = raw_data[idx:idx+1]
            if escape_next:
                escape_next = False
                idx += 1
                continue
            if ch == b'\\':
                escape_next = True
                idx += 1
                continue
            if ch == b"'":
                in_string = not in_string
                idx += 1
                continue
            if not in_string:
                if ch == b')':
                    if depth == 0:
                        row_data = raw_data[row_start:idx]
                        rows.append(row_data)
                        idx += 1
                        # skip comma or semicolon
                        while idx < len(raw_data) and raw_data[idx:idx+1] in (b',', b';', b'\n', b'\r', b' ', b'\t'):
                            if raw_data[idx:idx+1] == b';':
                                idx += 1
                                return rows
                            idx += 1
                        break
                    else:
                        depth -= 1
                elif ch == b'(':
                    depth += 1
            idx += 1
    
    return rows

def parse_row_fields(row_bytes):
    """
    Parse a row's raw bytes into individual field values.
    Returns a list of field values (bytes for strings, int for numbers, None for NULL).
    """
    fields = []
    i = 0
    
    while i < len(row_bytes):
        # Skip whitespace
        while i < len(row_bytes) and row_bytes[i:i+1] in (b' ', b'\t'):
            i += 1
        
        if i >= len(row_bytes):
            break
        
        ch = row_bytes[i:i+1]
        
        if ch == b"'":
            # String literal
            i += 1
            start = i
            escape_next = False
            string_bytes = bytearray()
            while i < len(row_bytes):
                if escape_next:
                    b = row_bytes[i]
                    if b == ord('0'):
                        string_bytes.append(0)
                    elif b == ord('n'):
                        string_bytes.append(ord('\n'))
                    elif b == ord('r'):
                        string_bytes.append(ord('\r'))
                    elif b == ord('t'):
                        string_bytes.append(ord('\t'))
                    elif b == ord('\\'):
                        string_bytes.append(ord('\\'))
                    elif b == ord("'"):
                        string_bytes.append(ord("'"))
                    elif b == ord('"'):
                        string_bytes.append(ord('"'))
                    else:
                        string_bytes.append(b)
                    escape_next = False
                    i += 1
                    continue
                if row_bytes[i:i+1] == b'\\':
                    escape_next = True
                    i += 1
                    continue
                if row_bytes[i:i+1] == b"'":
                    i += 1
                    break
                string_bytes.append(row_bytes[i])
                i += 1
            fields.append(bytes(string_bytes))
        elif row_bytes[i:i+4] == b'NULL':
            fields.append(None)
            i += 4
        elif ch in (b'-', b'0', b'1', b'2', b'3', b'4', b'5', b'6', b'7', b'8', b'9'):
            # Number
            start = i
            if ch == b'-':
                i += 1
            while i < len(row_bytes) and row_bytes[i:i+1] in (b'0', b'1', b'2', b'3', b'4', b'5', b'6', b'7', b'8', b'9', b'.'):
                i += 1
            num_str = row_bytes[start:i].decode('ascii')
            if '.' in num_str:
                fields.append(float(num_str))
            else:
                fields.append(int(num_str))
        
        # Skip comma separator
        while i < len(row_bytes) and row_bytes[i:i+1] in (b',', b' ', b'\t'):
            i += 1
    
    return fields


def main():
    sql_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bans.sql')
    
    print(f"Reading {sql_file}...")
    with open(sql_file, 'rb') as f:
        raw_data = f.read()
    print(f"Read {len(raw_data)} bytes")
    
    # Parse libertybans_bans: (id, victim)
    print("Parsing libertybans_bans...")
    ban_rows = parse_insert_values(raw_data, 'libertybans_bans')
    bans = {}  # id -> victim_id
    for row in ban_rows:
        fields = parse_row_fields(row)
        if len(fields) >= 2:
            bans[fields[0]] = fields[1]  # punishment_id -> victim_id
    print(f"  Found {len(bans)} active bans")
    
    # Parse libertybans_punishments: (id, type, operator, reason, scope, start, end, track, scope_id)
    print("Parsing libertybans_punishments...")
    punishment_rows = parse_insert_values(raw_data, 'libertybans_punishments')
    punishments = {}  # id -> {type, operator_uuid, reason, start, end}
    for row in punishment_rows:
        fields = parse_row_fields(row)
        if len(fields) >= 7:
            pid = fields[0]
            ptype = fields[1]
            operator_bytes = fields[2]
            reason = fields[3].decode('utf-8', errors='replace') if isinstance(fields[3], bytes) else str(fields[3])
            start = fields[5]
            end = fields[6]
            
            operator_uuid = bytes_to_uuid(operator_bytes) if isinstance(operator_bytes, bytes) and len(operator_bytes) == 16 else None
            
            punishments[pid] = {
                'type': ptype,
                'operator_uuid': operator_uuid,
                'reason': reason,
                'start': start,
                'end': end,
            }
    print(f"  Found {len(punishments)} punishments")
    
    # Parse libertybans_victims: (id, type, uuid, address)
    print("Parsing libertybans_victims...")
    victim_rows = parse_insert_values(raw_data, 'libertybans_victims')
    victims = {}  # id -> uuid_str
    for row in victim_rows:
        fields = parse_row_fields(row)
        if len(fields) >= 3:
            vid = fields[0]
            vtype = fields[1]
            vuuid_bytes = fields[2]
            
            vuuid = bytes_to_uuid(vuuid_bytes) if isinstance(vuuid_bytes, bytes) and len(vuuid_bytes) == 16 else None
            victims[vid] = {
                'type': vtype,
                'uuid': vuuid,
            }
    print(f"  Found {len(victims)} victims")
    
    # Parse libertybans_names: (uuid, name, lower_name, updated)
    print("Parsing libertybans_names...")
    name_rows = parse_insert_values(raw_data, 'libertybans_names')
    # Build uuid -> name mapping (keep the most recently updated)
    names = {}  # uuid_str -> name
    for row in name_rows:
        fields = parse_row_fields(row)
        if len(fields) >= 4:
            nuuid_bytes = fields[0]
            name = fields[1].decode('utf-8', errors='replace') if isinstance(fields[1], bytes) else str(fields[1])
            updated = fields[3]
            
            nuuid = bytes_to_uuid(nuuid_bytes) if isinstance(nuuid_bytes, bytes) and len(nuuid_bytes) == 16 else None
            if nuuid:
                if nuuid not in names or updated > names[nuuid]['updated']:
                    names[nuuid] = {'name': name, 'updated': updated}
    print(f"  Found {len(names)} name mappings")
    
    # Now join: for each active ban, get punishment details + victim UUID + names
    # Tbans format: Map<playerName, List<BanEvent>>
    # BanEvent: {type: "BAN"/"UNBAN", targetName, executorName, timestamp, expiry, reason}
    
    import time
    current_time_seconds = int(time.time())
    
    tbans_data = {}
    skipped = 0
    
    for punishment_id, victim_id in bans.items():
        punishment = punishments.get(punishment_id)
        if not punishment:
            skipped += 1
            continue
        
        victim = victims.get(victim_id)
        if not victim:
            skipped += 1
            continue
        
        victim_uuid = victim.get('uuid')
        operator_uuid = punishment.get('operator_uuid')
        
        # Get names
        victim_name = names.get(victim_uuid, {}).get('name', victim_uuid or 'unknown') if victim_uuid else 'unknown'
        operator_name = names.get(operator_uuid, {}).get('name', operator_uuid or 'Console') if operator_uuid else 'Console'
        
        # LibertyBans stores start/end as epoch seconds
        # Tbans stores timestamp/expiry as epoch milliseconds
        start_ms = punishment['start'] * 1000
        end_epoch = punishment['end']
        
        if end_epoch == 0:
            # Permanent ban
            expiry_ms = 253402300800000  # year 9999 in ms - effectively permanent
        else:
            expiry_ms = end_epoch * 1000
        
        reason = punishment['reason']
        
        ban_event = {
            'type': 'BAN',
            'targetName': victim_name,
            'executorName': operator_name,
            'timestamp': start_ms,
            'expiry': expiry_ms,
            'reason': reason,
        }
        
        key = victim_name.lower()
        if key not in tbans_data:
            tbans_data[key] = []
        tbans_data[key].append(ban_event)
    
    print(f"\nGenerated {sum(len(v) for v in tbans_data.values())} ban events for {len(tbans_data)} players")
    if skipped:
        print(f"Skipped {skipped} bans (missing punishment/victim data)")
    
    # Write output
    output_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bans.json')
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(tbans_data, f, indent=2, ensure_ascii=False)
    print(f"\nWrote {output_file}")

if __name__ == '__main__':
    main()

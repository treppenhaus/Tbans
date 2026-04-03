import json

data = json.load(open('uuid-bans.json'))
uuids = [k for k in data if len(k) == 36 and k.count('-') == 4]
names = [k for k in data if not (len(k) == 36 and k.count('-') == 4)]
print(f'Total entries: {len(data)}')
print(f'UUID keys: {len(uuids)}')
print(f'Name keys (unresolved): {len(names)}')
if names:
    print(f'Unresolved names: {names[:10]}')
print('---Sample entries---')
for k, v in list(data.items())[:5]:
    print(f'  {k}')
    print(f'    target={v[0]["targetName"]}')
    print(f'    executor={v[0]["executorName"]}')

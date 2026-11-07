# Writes the converter's mapping: each Vanilla Tweaks head texture, and where Vanilla Tweaks reuses one face for
# several heads its item name too, to the exact Jodek head the plugin should turn it into.
# Usage: python3 -I tools/make_mapping.py heads.json pairs.json src/main/resources/mapping.json
import json, collections, sys

FROM = 'Vanilla Tweaks More Mob Heads 2.20.1'
TO = "Jodek's Mob Heads 5.3.1"

d = json.load(open(sys.argv[1]))
pairs = json.load(open(sys.argv[2]))
jodek = {(h['table'], h['hash']): h for h in d['jodek']}
jodek_hashes = {h['hash'] for h in d['jodek']}

def target(table, hsh):
	h = jodek[(table, hsh)]
	tiers, holder = {}, None
	for n in h['names']:
		c = n['condition'] or {}
		if c.get('value', {}).get('score') == 'mob_heads.rarity':
			holder = c['value']['target']['name']
			tiers[c['test']] = json.dumps(n['name'], separators=(',', ':'))
		elif not c:
			tiers.setdefault(0, json.dumps(n['name'], separators=(',', ':')))
	return {
		'table': table,
		'textures': h['textures'],
		'sound': h['components'].get('minecraft:note_block_sound'),
		'rarity': holder,
		'names': [tiers.get(i) for i in range(6)],
	}

by_hash = collections.defaultdict(list)
for p in pairs:
	by_hash[p['vt_hash']].append(p)
heads = {}
for hsh, ps in sorted(by_hash.items()):
	targets = {p['vt_name']: target(p['jodek_table'], p['jodek_hash']) for p in ps}
	distinct = {t['table'] for t in targets.values()}
	if len(distinct) == 1:
		heads[hsh] = {'default': next(iter(targets.values()))}
		if hsh in jodek_hashes:
			# Jodek uses this face too, so only an item still carrying its Vanilla Tweaks name is certainly old.
			heads[hsh]['shared'] = True
	else:
		# One face, several professions: the item name decides; a placed skull has no name and becomes the plain one.
		plain = next(t for n, t in targets.items() if n in ('Villager Head', 'Zombie Villager Head'))
		heads[hsh] = {'default': plain, 'byName': targets}
		if hsh in jodek_hashes:
			heads[hsh]['shared'] = True
out = {'from': FROM, 'to': TO, 'heads': heads}
json.dump(out, open(sys.argv[3], 'w'), indent='\t', ensure_ascii=False)
print('textures', len(heads), 'with per-name targets', sum(1 for v in heads.values() if 'byName' in v))
missing = [t['table'] for v in heads.values() for t in [v['default'], *v.get('byName', {}).values()] if not t['rarity'] or None in t['names'] or not t['sound']]
print('targets missing a rarity holder, a tier name or a sound:', missing[:10], len(missing))
print(json.dumps(heads[next(iter(heads))], indent=1)[:900])

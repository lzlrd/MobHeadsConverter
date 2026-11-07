# Pairs each Vanilla Tweaks head with the Jodek head for the same mob and variant.
# Usage: python3 -I tools/mapper.py heads.json pairs.json
import json, re, collections, sys

d = json.load(open(sys.argv[1]))
vt, jodek = d['vt'], d['jodek']

def values(conditions):
	s = json.dumps(conditions)
	out = set()
	for m in re.finditer(r'"minecraft:[a-z_]+/variant": "(?:minecraft:)?([a-z_]+)"', s):
		out.add(m.group(1))
	for m in re.finditer(r'profession:[\\"]*minecraft:([a-z_]+)', s):
		out.add(m.group(1))
	for m in re.finditer(r'MainGene:[\\"]*([a-z_]+)', s):
		out.add(m.group(1))
	for m in re.finditer(r"weather_state: '([a-z_]+)'", s):
		out.add(m.group(1))
	return out

def name(h):
	return (h['components'].get('minecraft:item_name') or '').strip('"')

jodek_by_mob = collections.defaultdict(list)
for h in jodek:
	parts = h['table'].split('/')
	if 'baby' in parts:
		continue
	h['rel'] = '/'.join(parts[2:])
	h['facts'] = values(h['conditions'])
	jodek_by_mob[parts[1]].append(h)

TRADER = {0: 'creamy', 1: 'white', 2: 'brown', 3: 'gray'}
NO_MATCH = {
	'Cold Strider Head': 'Jodek has no cold strider',
	'Wither Projectile Head': 'Jodek has no wither projectile',
	'Blue Wither Projectile Head': 'Jodek has no wither projectile',
	'Tropical Fish Head': 'one generic head; Jodek only has pattern-specific fish heads',
	'Creaking Head': 'Jodek has no creaking head',
}
# Settled by comparing the two textures side by side.
BY_HASH = {
	# The red-tinted Vanilla Tweaks vex is the charging one.
	'4a4e518e16e4b5c114acbd9c61cd18292da9ef60550a4fcae27d39ae293e477a': 'vex_charging',
}

def pick(mob, rel=None, fact=None):
	cands = jodek_by_mob.get(mob, [])
	if rel is not None:
		cands = [h for h in cands if h['rel'] == rel]
	if fact is not None:
		cands = [h for h in cands if fact in h['facts'] or h['rel'].split('/')[-1] == fact or fact in h['rel'].split('/')]
	hashes = {h['hash'] for h in cands}
	return cands[0] if len(hashes) == 1 else None

def target(h):
	mob = h['table'].split('/')[1]
	n = name(h)
	words = n.lower().replace(' head', '').replace(' shell', '').split()
	facts = values(h['conditions'])
	if n in NO_MATCH:
		return None, NO_MATCH[n]
	if h['hash'] in BY_HASH:
		return pick(mob, rel=BY_HASH[h['hash']]), 'by texture comparison'
	if mob == 'wolf':
		if words[0] == 'angry':
			return None, 'Jodek has no angry wolf heads'
		return pick(mob, rel='adult/' + words[0]), ''
	if mob in ('villager', 'zombie_villager'):
		# Vanilla Tweaks names say the profession: "Armorer Villager Head", "Zombie Armorer Head".
		prof = next(iter(facts), None) or (words[1] if mob == 'zombie_villager' else words[0])
		if n in ('Villager Head', 'Zombie Villager Head'):
			prof = 'normal'
		return pick(mob, rel='adult/plains/' + prof), 'plains biome'
	if mob == 'panda':
		return pick(mob, rel='adult/' + (next(iter(facts), None) or 'normal')), ''
	if mob == 'bee':
		key = {'bee': 'normal', 'pollinated bee': 'pollen', 'angry bee': 'angry', 'angry pollinated bee': 'angry_pollen'}[' '.join(words)]
		return pick(mob, rel='adult/' + key), ''
	if mob == 'goat':
		return pick(mob, rel='adult/screaming_normal' if words[0] == 'screaming' else 'adult/normal'), ''
	if mob == 'copper_golem':
		return pick(mob, rel=words[0] if words[0] in ('exposed', 'weathered', 'oxidized') else 'normal'), ''
	if mob == 'creeper':
		return pick(mob, rel='charged'), ''
	if mob == 'vindicator':
		return pick(mob, rel='vindicator'), ''
	if mob == 'wither':
		return pick(mob, rel='wither'), ''
	if mob in ('strider', 'happy_ghast'):
		return pick(mob, rel='adult'), ''
	if mob == 'nautilus':
		return pick(mob, rel='adult'), ''
	if mob == 'zombie_nautilus':
		return pick(mob, fact=next(iter(facts), 'temperate')), ''
	if mob == 'sheep':
		color = h['table'].split('/')[2] if len(h['table'].split('/')) > 2 else words[0]
		return pick(mob, rel='adult/' + ('jeb_' if color.startswith('jeb_') else color)), ''
	if mob == 'trader_llama':
		return pick(mob, rel='adult/' + words[0]), ''
	if mob == 'llama':
		return pick(mob, rel='adult/' + (next(iter(facts), None) or words[0]) + '/no_carpet'), 'no carpet'
	if mob == 'rabbit' and n == 'Toast Rabbit Head':
		return pick(mob, rel='adult/toast'), ''
	if mob in ('cow', 'chicken', 'pig'):
		return pick(mob, rel='adult/' + words[0]), ''
	variant = next(iter(facts), None)
	if variant == 'lucy':
		variant = 'leucistic'
	if variant:
		return pick(mob, rel='adult/' + variant) or pick(mob, rel=variant), ''
	# Single-head mobs.
	return pick(mob, rel='adult') or pick(mob, rel='') or pick(mob, rel=mob), ''

pairs, unmatched = [], []
for h in vt:
	t, note = target(h)
	if t:
		pairs.append((h, t, note))
	else:
		unmatched.append((h, note))
print('matched', len(pairs), 'unmatched', len(unmatched))
for h, note in unmatched:
	print('  no match:', h['table'], name(h), h['hash'][:12], note)
json.dump([{'vt_hash': h['hash'], 'vt_name': name(h), 'vt_table': h['table'], 'jodek_table': t['table'], 'jodek_hash': t['hash'], 'note': note} for h, t, note in pairs], open(sys.argv[2], 'w'), indent=1)

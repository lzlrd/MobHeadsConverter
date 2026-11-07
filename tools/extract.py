# Extracts every head (texture hash, mob, conditions, name and components) from Vanilla Tweaks More Mob Heads and Jodek's
# Mob Heads. Vanilla Tweaks puts variants of one mob on a single item entry as conditional set_components modifiers, so
# each conditional profile is its own head; Jodek adds six conditional names (one per rarity tier) to each head.
# Usage: python3 -I tools/extract.py VANILLA_TWEAKS_DIR JODEK_DIR heads.json (both packs unzipped)
import base64, json, re, sys, pathlib

def texture(components):
	for prop in components.get('minecraft:profile', {}).get('properties', []) if isinstance(components.get('minecraft:profile'), dict) else []:
		if prop.get('name') == 'textures':
			data = json.loads(base64.b64decode(prop['value'] + '==='))
			return data['textures']['SKIN']['url'].rsplit('/', 1)[1], prop['value']
	return None, None

def kind(mod):
	return mod.get('type', mod.get('function', '')).replace('minecraft:', '')

def heads_of(entry, conditions):
	base, variants, names = {}, [], []
	for mod in entry.get('modifier', entry.get('functions', [])):
		cond = mod.get('condition') or mod.get('conditions')
		if kind(mod) == 'set_components':
			if cond is None:
				base.update(mod['components'])
			else:
				variants.append(([cond], mod['components']))
		elif kind(mod) == 'set_name':
			names.append({'name': mod['name'], 'condition': cond})
	out = []
	for extra, comps in [([], {})] + variants:
		merged = {**base, **comps}
		h, value = texture(merged)
		if h and (not extra or 'minecraft:profile' in comps):
			out.append({'hash': h, 'textures': value, 'conditions': conditions + extra, 'components': merged, 'names': names})
	return out

def walk(node, conditions, out, ctx):
	if isinstance(node, dict):
		cond = node.get('condition') or node.get('conditions')
		here = conditions + ([cond] if cond else [])
		if node.get('type') in ('item', 'minecraft:item') and node.get('name') == 'minecraft:player_head':
			for h in heads_of(node, here):
				out.append({**ctx, **h})
			return
		for k, v in node.items():
			if k not in ('condition', 'conditions'):
				walk(v, here, out, ctx)
	elif isinstance(node, list):
		for v in node:
			walk(v, conditions, out, ctx)

def effective_files(root, namespace):
	# Overlays replace base files for the formats they cover; 26.3 uses format 121.
	base = pathlib.Path(root)
	meta = json.load(open(base / 'pack.mcmeta', encoding='utf-8'))
	overlays = []
	for e in meta.get('overlays', {}).get('entries', []):
		lo = e.get('min_format', e.get('formats', [0])[0] if isinstance(e.get('formats'), list) else e.get('formats', 0))
		hi = e.get('max_format', e.get('formats', [999])[-1] if isinstance(e.get('formats'), list) else e.get('formats', 999))
		lo = lo[0] if isinstance(lo, list) else lo
		hi = hi[0] if isinstance(hi, list) else hi
		if lo <= 121 <= hi:
			overlays.append(e['directory'])
	files = {}
	for top in [''] + overlays:
		d = base / top / 'data' / namespace / 'loot_table' if top else base / 'data' / namespace / 'loot_table'
		for f in d.rglob('*.json'):
			files[f.relative_to(d).as_posix()] = f
	return files

def extract(root, namespace):
	out = []
	for rel, f in sorted(effective_files(root, namespace).items()):
		if rel.startswith('entities/'):
			walk(json.load(open(f, encoding='utf-8')), [], out, {'table': rel[:-5]})
	return out

vt = extract(sys.argv[1], 'more_mob_heads')
jodek = extract(sys.argv[2], 'mob_heads')
json.dump({'vt': vt, 'jodek': jodek}, open(sys.argv[3], 'w'), indent=1)
print('vt heads', len(vt), 'unique hashes', len({h['hash'] for h in vt}))
print('jodek heads', len(jodek), 'unique hashes', len({h['hash'] for h in jodek}))

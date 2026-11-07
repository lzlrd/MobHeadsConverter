package by.ayoo.mobheadsconverter;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

/**
 * Turns heads from Vanilla Tweaks' More Mob Heads into the matching heads from Jodek's Mob Heads: same texture, name,
 * note block sound and rarity colour as a head Jodek's loot tables drop today, so converted heads stack with new ones
 * and count for Jodek's collection advancements. Heads are converted where they turn up: player inventories and ender
 * chests on join, containers when opened, items when picked up, and placed heads, item frames, armour stands and
 * dropped items when their chunk loads.
 */
public final class MobHeadsConverter extends JavaPlugin implements Listener {
	private static final Pattern TEXTURE_URL = Pattern.compile("textures\\.minecraft\\.net/texture/([0-9a-fA-F]+)");
	private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

	private final Map<String, Mapping> heads = new HashMap<>();

	@Override
	public void onEnable() {
		try (Reader reader = new InputStreamReader(getResource("mapping.json"), StandardCharsets.UTF_8)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("heads").entrySet())
				heads.put(entry.getKey(), Mapping.parse(entry.getValue().getAsJsonObject()));
		} catch (Exception e) {
			throw new IllegalStateException("Could not read mapping.json", e);
		}
		getServer().getPluginManager().registerEvents(this, this);
		// Chunks and players already loaded when the plugin starts, for example after a reload.
		for (World world : getServer().getWorlds())
			for (Chunk chunk : world.getLoadedChunks()) {
				convertSkulls(chunk);
				for (Entity entity : chunk.getEntities())
					convertEntity(entity);
			}
		for (Player player : getServer().getOnlinePlayers())
			convertPlayer(player);
		getLogger().info("Ready to convert " + heads.size() + " Vanilla Tweaks head textures");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onJoin(PlayerJoinEvent event) {
		convertPlayer(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onOpen(InventoryOpenEvent event) {
		report(convert(event.getInventory()), "a " + event.getInventory().getType().name().toLowerCase() + " opened by " + event.getPlayer().getName());
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		ItemStack converted = convert(event.getItem().getItemStack());
		if (converted != null)
			event.getItem().setItemStack(converted);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent event) {
		if (!event.isNewChunk())
			convertSkulls(event.getChunk());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntitiesLoad(EntitiesLoadEvent event) {
		for (Entity entity : event.getEntities())
			convertEntity(entity);
	}

	private void convertPlayer(Player player) {
		int count = convert(player.getInventory()) + convert(player.getEnderChest());
		report(count, player.getName() + "'s inventory");
	}

	private void convertSkulls(Chunk chunk) {
		int count = 0;
		for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.PLAYER_HEAD || block.getType() == Material.PLAYER_WALL_HEAD, true))
			if (state instanceof Skull skull && convert(skull))
				count++;
		report(count, "placed heads in chunk " + chunk.getX() + ", " + chunk.getZ() + " of " + chunk.getWorld().getName());
	}

	private void convertEntity(Entity entity) {
		int count = 0;
		if (entity instanceof ItemFrame frame) {
			ItemStack converted = convert(frame.getItem());
			if (converted != null) {
				frame.setItem(converted, false);
				count++;
			}
		} else if (entity instanceof Item item) {
			ItemStack converted = convert(item.getItemStack());
			if (converted != null) {
				item.setItemStack(converted);
				count++;
			}
		} else if (entity instanceof ItemDisplay display) {
			ItemStack converted = convert(display.getItemStack());
			if (converted != null) {
				display.setItemStack(converted);
				count++;
			}
		}
		if (entity instanceof LivingEntity living && !(entity instanceof Player) && living.getEquipment() != null) {
			EntityEquipment equipment = living.getEquipment();
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (!living.canUseEquipmentSlot(slot))
					continue;
				ItemStack converted = convert(equipment.getItem(slot));
				if (converted != null) {
					equipment.setItem(slot, converted, true);
					count++;
				}
			}
		}
		if (entity instanceof InventoryHolder holder && !(entity instanceof Player))
			count += convert(holder.getInventory());
		report(count, entity.getType().name().toLowerCase() + " at " + entity.getLocation().getBlockX() + " " + entity.getLocation().getBlockY() + " " + entity.getLocation().getBlockZ());
	}

	private int convert(Inventory inventory) {
		int count = 0;
		ItemStack[] contents = inventory.getContents();
		for (int slot = 0; slot < contents.length; slot++) {
			ItemStack converted = convert(contents[slot]);
			if (converted != null) {
				inventory.setItem(slot, converted);
				count++;
			}
		}
		return count;
	}

	/** Returns the converted copy of an old head, also inside shulker boxes and bundles, or null if nothing changes. */
	private ItemStack convert(ItemStack item) {
		if (item == null || item.isEmpty())
			return null;
		if (item.getType() == Material.PLAYER_HEAD)
			return convertHead(item);
		ItemContainerContents container = item.getData(DataComponentTypes.CONTAINER);
		if (container != null) {
			List<ItemStack> inside = convertAll(container.contents());
			if (inside == null)
				return null;
			ItemStack copy = item.clone();
			copy.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(inside));
			return copy;
		}
		BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
		if (bundle != null) {
			List<ItemStack> inside = convertAll(bundle.contents());
			if (inside == null)
				return null;
			ItemStack copy = item.clone();
			copy.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(inside));
			return copy;
		}
		return null;
	}

	private List<ItemStack> convertAll(List<ItemStack> items) {
		List<ItemStack> out = new ArrayList<>(items.size());
		boolean changed = false;
		for (ItemStack item : items) {
			ItemStack converted = convert(item);
			changed |= converted != null;
			out.add(converted != null ? converted : item);
		}
		return changed ? out : null;
	}

	private ItemStack convertHead(ItemStack item) {
		Mapping mapping = heads.get(textureHash(item.getData(DataComponentTypes.PROFILE)));
		if (mapping == null)
			return null;
		// Every item has a default item name; only a name set on the item itself is Vanilla Tweaks'.
		Component oldName = item.isDataOverridden(DataComponentTypes.ITEM_NAME) ? item.getData(DataComponentTypes.ITEM_NAME) : null;
		// Jodek uses a few of the same faces; without its Vanilla Tweaks name such a head may be one of Jodek's own.
		if (mapping.shared && oldName == null)
			return null;
		Target target = mapping.target(oldName == null ? null : PLAIN.serialize(oldName));
		ItemStack converted = target.item(rarityTier(target.rarity));
		converted.setAmount(item.getAmount());
		// Keep a name a player gave the head on an anvil.
		Component renamed = item.getData(DataComponentTypes.CUSTOM_NAME);
		if (renamed != null)
			converted.setData(DataComponentTypes.CUSTOM_NAME, renamed);
		return converted;
	}

	private boolean convert(Skull skull) {
		Mapping mapping = heads.get(textureHash(skull.getProfile()));
		if (mapping == null)
			return false;
		// A placed Jodek head keeps its name; a placed Vanilla Tweaks head has none.
		if (mapping.shared && skull.customName() != null)
			return false;
		ItemStack template = mapping.target(null).item(rarityTier(mapping.target(null).rarity));
		skull.setProfile(template.getData(DataComponentTypes.PROFILE));
		skull.setNoteBlockSound(NamespacedKey.fromString(mapping.target(null).sound));
		if (skull.customName() == null)
			skull.customName(template.getData(DataComponentTypes.CUSTOM_NAME));
		return skull.update(true, false);
	}

	private static String textureHash(ResolvableProfile profile) {
		if (profile == null)
			return null;
		for (ProfileProperty property : profile.properties()) {
			if (!property.getName().equals("textures"))
				continue;
			try {
				Matcher url = TEXTURE_URL.matcher(new String(Base64.getDecoder().decode(property.getValue()), StandardCharsets.UTF_8));
				return url.find() ? url.group(1).toLowerCase() : null;
			} catch (IllegalArgumentException e) {
				return null;
			}
		}
		return null;
	}

	/** The rarity tier Mob Heads currently gives this mob, which sets the name colour of new drops. */
	private static int rarityTier(String holder) {
		Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("mob_heads.rarity");
		if (objective == null || holder == null)
			return 0;
		Score score = objective.getScore(holder);
		return score.isScoreSet() ? score.getScore() : 0;
	}

	private void report(int count, String where) {
		if (count > 0)
			getLogger().info("Converted " + count + " old head" + (count == 1 ? "" : "s") + " in " + where);
	}

	private record Mapping(boolean shared, Target fallback, Map<String, Target> byName) {
		static Mapping parse(JsonObject json) {
			Map<String, Target> byName = new HashMap<>();
			if (json.has("byName"))
				for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("byName").entrySet())
					byName.put(entry.getKey(), Target.parse(entry.getValue().getAsJsonObject()));
			return new Mapping(json.has("shared") && json.get("shared").getAsBoolean(), Target.parse(json.getAsJsonObject("default")), byName);
		}

		Target target(String oldName) {
			return oldName != null ? byName.getOrDefault(oldName, fallback) : fallback;
		}
	}

	private record Target(String textures, String sound, String rarity, String[] names) {
		static Target parse(JsonObject json) {
			String[] names = new String[json.getAsJsonArray("names").size()];
			for (int i = 0; i < names.length; i++) {
				JsonElement name = json.getAsJsonArray("names").get(i);
				names[i] = name.isJsonNull() ? null : name.getAsString();
			}
			return new Target(json.get("textures").getAsString(), json.get("sound").getAsString(), json.has("rarity") && !json.get("rarity").isJsonNull() ? json.get("rarity").getAsString() : null, names);
		}

		/** Builds the head the way Jodek's loot table does, through the same component codec. */
		ItemStack item(int tier) {
			StringBuilder spec = new StringBuilder("minecraft:player_head[minecraft:profile={properties:[{name:\"textures\",value:\"")
					.append(textures).append("\"}]},minecraft:note_block_sound=\"").append(sound).append('"');
			if (tier >= 0 && tier < names.length && names[tier] != null)
				spec.append(",minecraft:custom_name=").append(names[tier]);
			return Bukkit.getItemFactory().createItemStack(spec.append(']').toString());
		}
	}
}

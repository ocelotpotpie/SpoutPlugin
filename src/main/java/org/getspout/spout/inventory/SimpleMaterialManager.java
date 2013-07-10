/*
 * This file is part of SpoutPlugin.
 *
 * Copyright (c) 2011 Spout LLC <http://www.spout.org/>
 * SpoutPlugin is licensed under the GNU Lesser General Public License.
 *
 * SpoutPlugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SpoutPlugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.getspout.spout.inventory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import gnu.trove.list.array.TByteArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;

import net.minecraft.server.v1_6_R2.Item;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import org.getspout.spout.block.SpoutCraftBlock;
import org.getspout.spout.player.SpoutCraftPlayer;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.block.SpoutChunk;
import org.getspout.spoutapi.inventory.ItemMap;
import org.getspout.spoutapi.inventory.MaterialManager;
import org.getspout.spoutapi.inventory.SpoutShapedRecipe;
import org.getspout.spoutapi.inventory.SpoutShapelessRecipe;
import org.getspout.spoutapi.io.store.FlatFileStore;
import org.getspout.spoutapi.material.CustomBlock;
import org.getspout.spoutapi.material.CustomItem;
import org.getspout.spoutapi.material.Material;
import org.getspout.spoutapi.material.MaterialData;
import org.getspout.spoutapi.packet.PacketCustomBlockChunkOverride;
import org.getspout.spoutapi.packet.PacketCustomBlockOverride;
import org.getspout.spoutapi.packet.PacketCustomMultiBlockOverride;
import org.getspout.spoutapi.packet.SpoutPacket;
import org.getspout.spoutapi.player.SpoutPlayer;
import org.getspout.spoutapi.util.map.TIntPairObjectHashMap;

public class SimpleMaterialManager extends AbstractBlockManager implements MaterialManager {
	private final TIntObjectHashMap<String> itemPlugin = new TIntObjectHashMap<String>();
	private final HashMap<World, TIntPairObjectHashMap<BlockOverrides>> queuedChunkBlockOverrides = new HashMap<World, TIntPairObjectHashMap<BlockOverrides>>(10);

	public static void disableFlintStackMix() {
		try {
			Method a = Item.class.getDeclaredMethod("a", new Class[] { boolean.class });
			a.setAccessible(true);
			a.invoke(Item.byId[318], Boolean.TRUE);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
	}

	@Override
	public void reset() {
		super.reset();
	}

	@Override
	public void onPlayerJoin(SpoutPlayer player) {
		if (player.isSpoutCraftEnabled()) {
			for (CustomBlock block : MaterialData.getCustomBlocks()) {
				if (block instanceof SpoutPacket) {
					player.sendPacket((SpoutPacket)block);
				}
			}
			for (CustomItem item : MaterialData.getCustomItems()) {
				CustomBlock owner = MaterialData.getCustomBlock(item.getCustomId());
				if (item instanceof SpoutPacket && owner == null) {
					player.sendPacket((SpoutPacket)item);
				}
			}
		}
		super.onPlayerJoin(player);
	}

	@Override
	public int registerCustomItemName(Plugin plugin, String key) {
		int id = ItemMap.getRootMap().register(key);

		itemPlugin.put(id, plugin.getDescription().getName());

		return id;
	}

	@Override
	public boolean removeBlockOverride(Block block) {
		SpoutCraftBlock scb = (SpoutCraftBlock) block;

		if (scb.isCustomBlock()) {
			scb.getCustomBlock().onBlockDestroyed(scb.getWorld(), scb.getX(), scb.getY(), scb.getZ());
		}

		scb.removeCustomBlockData();

		queueBlockOverrides(scb, null, (byte) 0);
		return true;
	}

	@Override
	public boolean overrideBlock(Block block, CustomBlock customBlock) {
		return overrideBlock(block, customBlock, (byte) 0);
	}

	@Override
	public boolean overrideBlock(Block block, CustomBlock customBlock, byte data) {
		block.setTypeId(customBlock.getBlockId());
		int blockId = customBlock.getCustomId();

		SpoutCraftBlock scb = (SpoutCraftBlock) block;

		customBlock.onBlockPlace(scb.getWorld(), scb.getX(), scb.getY(), scb.getZ());

		scb.setCustomBlockId(blockId);
		scb.setCustomBlockData(data);
		queueBlockOverrides(scb, blockId, data);

		return true;
	}

	@Override
	public boolean overrideBlock(World world, int x, int y, int z, CustomBlock customBlock) {
		return overrideBlock(world, x, y, z, customBlock, (byte) 0);
	}

	@Override
	public boolean overrideBlock(World world, int x, int y, int z, CustomBlock customBlock, byte data) {
		int blockId = customBlock.getCustomId();

		SpoutManager.getChunkDataManager().setBlockData(blockIdString, world, x, y, z, blockId);
		((SpoutChunk) world.getChunkAt(x<<4, z<<4)).setCustomBlockData(x, y, z, data);

		queueBlockOverrides(world, x, y, z, blockId, data);

		return true;
	}

	public void queueBlockOverrides(SpoutCraftBlock block, Integer blockId, byte data) {
		if (block != null) {
			queueBlockOverrides(block.getWorld(), block.getX(), block.getY(), block.getZ(), blockId, data);
		}
	}

	public void queueBlockOverrides(World world, int x, int y, int z, Integer blockId, byte data) {
		if (world != null) {
			TIntPairObjectHashMap<BlockOverrides> chunkOverrides = queuedChunkBlockOverrides.get(world);
			if (chunkOverrides == null) {
				chunkOverrides = new TIntPairObjectHashMap<BlockOverrides>(100);
				queuedChunkBlockOverrides.put(world, chunkOverrides);
			}
			BlockOverrides overrides = chunkOverrides.get(x >> 4, z >> 4);
			if (overrides == null) {
				overrides = new BlockOverrides(world);
				chunkOverrides.put(x >> 4, z >> 4, overrides);
			}
			overrides.putOverride(x, y, z, blockId != null ? blockId.intValue() : -1, data);
		}
	}

	@Override
	public boolean registerSpoutRecipe(Recipe recipe) {
		SpoutRecipe toAdd;
		if (recipe instanceof SpoutRecipe) {
			toAdd = (SpoutRecipe) recipe;
		} else {
			if (recipe instanceof SpoutShapedRecipe) {
				toAdd = SimpleSpoutShapedRecipe.fromSpoutRecipe((SpoutShapedRecipe) recipe);
			} else if (recipe instanceof SpoutShapelessRecipe) {
				toAdd = SimpleSpoutShapelessRecipe.fromSpoutRecipe((SpoutShapelessRecipe) recipe);
			} else {
				return false;
			}
		}
		toAdd.addToCraftingManager();
		return true;
	}

	public void onTick() {
		for (World world : Bukkit.getServer().getWorlds()) {
			TIntPairObjectHashMap<BlockOverrides> chunkOverrides = queuedChunkBlockOverrides.get(world);
			if (chunkOverrides != null && chunkOverrides.size() > 0 && world.getPlayers().size() > 0) {
				//long time = System.nanoTime();
				for (BlockOverrides override : chunkOverrides.valueCollection()) {
					override.sendPacket();
				}
				chunkOverrides.clear();
				//System.out.println("Sending block overrides took " + (System.nanoTime() - time) * 1E-6D + " ms");
			}
		}
	}

	private boolean glassUpdated = false;
	// Fired when MaterialData.addCustomItem or MaterialData.addCustomBlock is called
	public void onCustomMaterialRegistered(Material mat) {
		if (mat instanceof CustomBlock && !glassUpdated) {
			if (!((CustomBlock)mat).isOpaque()) {
				org.getspout.spout.block.mcblock.CustomMCBlock.updateGlass();
				glassUpdated = true;
			}
		}
	}

	private class BlockOverrides {
		private World world;
		private TIntArrayList xCoords = new TIntArrayList();
		private TIntArrayList yCoords = new TIntArrayList();
		private TIntArrayList zCoords = new TIntArrayList();
		private TIntArrayList typeIds = new TIntArrayList();
		private TByteArrayList data = new TByteArrayList();
		BlockOverrides(World world) {
			this.world = world;
		}

		protected void putOverride(int x, int y, int z, int id, byte data) {
			xCoords.add(x);
			yCoords.add(y);
			zCoords.add(z);
			typeIds.add(id);
			this.data.add(data);
		}

		protected void sendPacket() {
			List<Player> players = world.getPlayers();
			if (xCoords.size() > 6) {
				SpoutPacket packet;
				if (xCoords.size() > 128) {
					int chunkX = xCoords.get(0) >> 4;
					int chunkZ = zCoords.get(0) >> 4;
					packet = new PacketCustomBlockChunkOverride(SpoutManager.getChunkDataManager().getCustomBlockIds(world, chunkX, chunkZ), SpoutManager.getChunkDataManager().getCustomBlockData(world, chunkX, chunkZ),chunkX, chunkZ);
				} else {
					packet = new PacketCustomMultiBlockOverride(xCoords, yCoords, zCoords, typeIds, data);
				}

				for (Player player : players) {
					if (player instanceof SpoutCraftPlayer) {
						SpoutCraftPlayer spc = (SpoutCraftPlayer) player;
						if (spc.isSpoutCraftEnabled()) {
							spc.sendPacket(packet);
						}
					}
				}
			} else {
				for (int i = 0; i < xCoords.size(); i++) {
					SpoutPacket packet = new PacketCustomBlockOverride(xCoords.get(i), yCoords.get(i), zCoords.get(i), typeIds.get(i), data.get(i));
					for (Player player : players) {
						if (player instanceof SpoutCraftPlayer) {
							SpoutCraftPlayer spc = (SpoutCraftPlayer) player;
							if (spc.isSpoutCraftEnabled()) {
								spc.sendPacket(packet);
							}
						}
					}
				}
			}
		}
	}

	@Override
	public void renameMaterialKey(JavaPlugin plugin, String oldKey, String newKey) {
		String fullOldKey = plugin.getDescription().getName() + "." + oldKey;
		String fullNewKey = plugin.getDescription().getName() + "." + newKey;

		ItemMap.getRootMap().rename(fullOldKey, fullNewKey);

		for (File worldFolder : Bukkit.getWorldContainer().listFiles()) {
			if ((new File(worldFolder, "spout_meta/worldItemMap.txt")).exists()) {
				World world = Bukkit.getWorld(worldFolder.getName());
				if (world != null) {
					ItemMap worldItemMap = SpoutManager.getChunkDataManager().getItemMap(world);
					if (worldItemMap != null) {
						worldItemMap.rename(fullOldKey, fullNewKey);
						continue;
					}
				}
				FlatFileStore<Integer> fs = new FlatFileStore<Integer>(new File(worldFolder, "spout_meta/worldItemMap.txt"), Integer.class);
				fs.load();
				ItemMap worldItemMap = new ItemMap(ItemMap.getRootMap(), fs, null);
				worldItemMap.rename(fullOldKey, fullNewKey);
			}
		}
	}
}

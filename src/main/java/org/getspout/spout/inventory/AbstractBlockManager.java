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

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import gnu.trove.iterator.TIntByteIterator;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TLongFloatIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.hash.TIntByteHashMap;
import gnu.trove.map.hash.TIntIntHashMap;

import net.minecraft.server.v1_6_R2.Block;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.getspout.spout.block.mcblock.WrappedMCBlock;
import org.getspout.spout.player.SpoutCraftPlayer;
import org.getspout.spoutapi.SpoutManager;
import org.getspout.spoutapi.inventory.MaterialManager;
import org.getspout.spoutapi.material.CustomBlock;
import org.getspout.spoutapi.material.Material;
import org.getspout.spoutapi.material.MaterialData;
import org.getspout.spoutapi.packet.PacketBlockData;
import org.getspout.spoutapi.packet.PacketItemName;
import org.getspout.spoutapi.packet.SpoutPacket;
import org.getspout.spoutapi.player.SpoutPlayer;
import org.getspout.spoutapi.util.map.TIntPairFloatHashMap;
import org.getspout.spoutapi.util.map.TIntPairHashSet;
import org.getspout.spoutapi.util.map.TIntPairObjectHashMap;

public abstract class AbstractBlockManager implements MaterialManager {
	protected final TIntPairObjectHashMap<String> customNames = new TIntPairObjectHashMap<String>(100);

	protected final TIntPairFloatHashMap originalHardness = new TIntPairFloatHashMap();
	protected final TIntPairFloatHashMap originalFriction = new TIntPairFloatHashMap();
	protected final TIntByteHashMap originalOpacity = new TIntByteHashMap();
	protected final TIntIntHashMap originalLight = new TIntIntHashMap();
	protected Set<org.getspout.spoutapi.material.Block> cachedBlockData = null;

	@Override
	public void reset() {
		customNames.clear();
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (player instanceof SpoutCraftPlayer) {
				if (((SpoutPlayer) player).isSpoutCraftEnabled()) {
					((SpoutPlayer) player).sendPacket(new PacketItemName(0, (short) 0, "[resetall]"));
				}
			}
		}
	}

	public void onPlayerJoin(SpoutPlayer player) {
		if ((player).isSpoutCraftEnabled()) {
			for (TLongObjectIterator<String> it = customNames.iterator(); it.hasNext();) {
				it.advance();
				(player).sendPacket(new PacketItemName(TIntPairHashSet.longToKey1(it.key()), (short) TIntPairHashSet.longToKey2(it.key()), it.value()));
			}
		}
	}

	@Override
	public void setItemName(Material item, String name) {
		customNames.put(item.getRawId(), item.getRawData(), name);
		for (Player player : Bukkit.getServer().getOnlinePlayers()) {
			if (player instanceof SpoutCraftPlayer) {
				if (((SpoutPlayer) player).isSpoutCraftEnabled()) {
					((SpoutPlayer) player).sendPacket(new PacketItemName(item.getRawId(), (short) item.getRawData(), name));
				}
			}
		}
	}

	@Override
	public void resetName(Material item) {
		int id = item.getRawId();
		int data = item.getRawData();
		if (customNames.containsKey(id, data)) {
			customNames.remove(id, data);
			for (Player player : Bukkit.getServer().getOnlinePlayers()) {
				if (player instanceof SpoutCraftPlayer) {
					if (((SpoutPlayer) player).isSpoutCraftEnabled()) {
						((SpoutPlayer) player).sendPacket(new PacketItemName(id, (short) data, "[reset]"));
					}
				}
			}
		}
	}

	@Override
	public String getStepSound(org.getspout.spoutapi.material.Block block) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setStepSound(org.getspout.spoutapi.material.Block block, String url) {
		// TODO Auto-generated method stub
	}

	@Override
	public void resetStepSound(org.getspout.spoutapi.material.Block block) {
		// TODO Auto-generated method stub
	}

	@Override
	public float getFriction(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		return Block.byId[id].frictionFactor;
	}

	@Override
	public void setFriction(org.getspout.spoutapi.material.Block block, float friction) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (!originalFriction.containsKey(id, data)) {
			originalFriction.put(id, data, getFriction(block));
		}
		Block.byId[id].frictionFactor = friction;
		updateBlockAttributes(id, (short) data); // invalidate cache
	}

	@Override
	public void resetFriction(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (originalFriction.containsKey(id, data)) {
			setFriction(block, originalFriction.get(id, data));
			originalFriction.remove(id, data);
		}
		updateBlockAttributes(id, (short) data); // Invalidate cache
	}

	@Override
	public float getHardness(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		// Access the protected strength field in Block
		Block mBlock = Block.byId[id];
		float hardness = 999999999f; // Probably useless safeguard
		try {
			Field field = Block.class.getDeclaredField("strength");
			field.setAccessible(true);
			hardness = field.getFloat(mBlock);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return hardness;
	}

	@Override
	public void setHardness(org.getspout.spoutapi.material.Block block, float hardness) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (!originalHardness.containsKey(id, data)) {
			originalHardness.put(id, data, getHardness(block));
		}
		Block b = Block.byId[id];
		if (b instanceof WrappedMCBlock) {
			((WrappedMCBlock) b).setHardness(hardness);
		}
		updateBlockAttributes(id, (short) data); // Invalidate cache
	}

	@Override
	public void resetHardness(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (originalHardness.containsKey(id, data)) {
			setHardness(block, originalHardness.get(id, data));
			originalHardness.remove(id, data);
		}
		updateBlockAttributes(id, (short) data); // invalidate cache
	}

	@Override
	public boolean isOpaque(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		return Block.t[id];
	}

	@Override
	public void setOpaque(org.getspout.spoutapi.material.Block block, boolean opacity) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (!originalOpacity.containsKey(id)) {
			originalOpacity.put(id, (byte) (isOpaque(block) ? 1 : 0));
		}
		Block.t[id] = opacity;
		updateBlockAttributes(id, (short) data); // Invalidate cache
	}

	@Override
	public void resetOpacity(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (originalOpacity.containsKey(id)) {
			setOpaque(block, originalOpacity.get(id) != 0);
			originalOpacity.remove(id);
		}
		updateBlockAttributes(id, (short) data); // Invalidate cache
	}

	@Override
	public int getLightLevel(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		return Block.lightEmission[id];
	}

	@Override
	public void setLightLevel(org.getspout.spoutapi.material.Block block, int level) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (!originalLight.containsKey(id)) {
			originalLight.put(id, getLightLevel(block));
		}
		Block.lightEmission[id] = level;
		updateBlockAttributes(id, (short) data); // Invalidate cache
	}

	@Override
	public void resetLightLevel(org.getspout.spoutapi.material.Block block) {
		int id = block.getRawId();
		if (block instanceof CustomBlock) {
			id = ((CustomBlock) block).getBlockId();
		}
		int data = block.getRawData();
		if (originalLight.containsKey(id)) {
			setLightLevel(block, originalLight.get(id));
			originalLight.remove(id);
		}
		updateBlockAttributes(id, (short) data); // Invalidate cache
	}

	@Override
	public Set<org.getspout.spoutapi.material.Block> getModifiedBlocks() {
		// Hit cache first
		if (cachedBlockData != null) {
			return cachedBlockData;
		}
		Set<org.getspout.spoutapi.material.Block> modified = new HashSet<org.getspout.spoutapi.material.Block>();
		TLongFloatIterator i = originalFriction.iterator();
		while (i.hasNext()) {
			i.advance();
			int id = TIntPairHashSet.longToKey1(i.key());
			int data = TIntPairHashSet.longToKey2(i.key());

			org.getspout.spoutapi.material.Block block = MaterialData.getBlock(id, (short) data);
			if (block != null) {
				modified.add(block);
			}
		}

		i = originalHardness.iterator();
		while (i.hasNext()) {
			i.advance();
			int id = TIntPairHashSet.longToKey1(i.key());
			int data = TIntPairHashSet.longToKey2(i.key());
			org.getspout.spoutapi.material.Block block = MaterialData.getBlock(id, (short) data);
			if (block != null) {
				modified.add(block);
			}
		}

		TIntIntIterator j = originalLight.iterator();
		while (j.hasNext()) {
			j.advance();
			org.getspout.spoutapi.material.Block block = MaterialData.getBlock(j.key());
			if (block != null) {
				modified.add(block);
			}
		}

		TIntByteIterator k = originalOpacity.iterator();
		while (k.hasNext()) {
			k.advance();
			org.getspout.spoutapi.material.Block block = MaterialData.getBlock(k.key());
			if (block != null) {
				modified.add(block);
			}
		}
		cachedBlockData = modified; // Save to cache
		return modified;
	}

	private void updateBlockAttributes(int id, short data) {
		org.getspout.spoutapi.material.Block block = MaterialData.getBlock(id, data);
		if (block != null) {
			cachedBlockData = null;
			HashSet<org.getspout.spoutapi.material.Block> toUpdate = new HashSet<org.getspout.spoutapi.material.Block>(1);
			toUpdate.add(block);
			SpoutPacket updatePacket = new PacketBlockData(toUpdate);
			for (SpoutPlayer player : SpoutManager.getOnlinePlayers()) {
				if (player.isSpoutCraftEnabled()) {
					player.sendPacket(updatePacket);
				}
			}
		}
	}
}

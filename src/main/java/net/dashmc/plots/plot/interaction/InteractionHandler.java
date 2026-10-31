package net.dashmc.plots.plot.interaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.events.VirtualBlockBreakEvent;
import net.dashmc.plots.events.VirtualBlockDamageEvent;
import net.dashmc.plots.events.VirtualBlockPlaceEvent;
import net.dashmc.plots.events.VirtualInteractEvent;
import net.dashmc.plots.nms.NmsItems;
import net.dashmc.plots.nms.NmsPlayers;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.plot.data.VirtualItemRegistry;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockCommand;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.ITileInventory;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.ItemSword;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockChange;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent;
import net.minecraft.server.v1_8_R3.TileEntity;
import net.minecraft.server.v1_8_R3.WorldSettings.EnumGamemode;

/**
 * Players using, digging and breaking blocks in a virtual environment. A port
 * of PlayerInteractManager.
 */
@RequiredArgsConstructor
public class InteractionHandler {
	private final VirtualItemRegistry items;
	private final VirtualBlockRegistry blocks;
	private final EventDispatcher events;
	private final Debug debug;

	public boolean interact(IEnvironment env, EntityHuman human, ItemStack item, BlockPosition pos,
			EnumDirection dir, float cX, float cY, float cZ, boolean isBorderPlace) {
		IBlockData blockData = isBorderPlace ? env.getRealWorld().getType(pos) : env.getType(pos);

		if (!env.isOwner(human)) {
			resyncPredicted(env, blockData, human, pos);
			return false;
		}

		boolean result = false;

		debug.log(() -> "Interact IBD:" + blockData.getBlock().toString());
		debug.log(() -> "Block is not air?: " + (blockData.getBlock() != Blocks.AIR));

		if (blockData.getBlock() != Blocks.AIR) {
			boolean cancelledBlock = false;
			// the human is the owner, as checked above
			GameMode gameMode = human.getBukkitEntity().getGameMode();

			if (gameMode == GameMode.SPECTATOR) {
				TileEntity tile = env.getTileEntity(pos);
				cancelledBlock = !(tile instanceof ITileInventory || tile instanceof IInventory);
			}

			if (!human.getBukkitEntity().isOp() && item != null
					&& Block.asBlock(item.getItem()) instanceof BlockCommand) {
				cancelledBlock = true;
			}

			VirtualInteractEvent ev = new VirtualInteractEvent(human, Action.RIGHT_CLICK_BLOCK, pos, dir, item,
					cancelledBlock, env);
			if (cancelledBlock)
				ev.setUseClickedBlock(Event.Result.DENY);
			events.call(ev);

			if (ev.getUseClickedBlock() == Event.Result.DENY) {
				resyncPredicted(env, blockData, human, pos);
				result = ev.getUseItemInHand() != Event.Result.ALLOW;
			} else if (gameMode == GameMode.SPECTATOR) {
				// TODO: implement
			} else if (!human.isSneaking() || item == null) {
				Block block = blockData.getBlock();
				result = blocks.handlerOf(block).interact(block, env, pos, blockData, human, dir, cX, cY, cZ);
			}

			debug.log(() -> "Item is: " + item);
			// not lazy, as result changes below
			debug.log("Result is: " + result);

			if (item != null && !result) {
				int data = item.getData();
				int count = item.count;

				result = placeItem(item, human, env, pos, dir, cX, cY, cZ, isBorderPlace);

				if (gameMode == GameMode.CREATIVE) {
					item.setData(data);
					item.count = count;
				}
			}
		}

		return result;
	}

	/**
	 * Places the held item with its handler, calling a VirtualBlockPlaceEvent for
	 * what it placed, like ItemStack#placeItem.
	 */
	private boolean placeItem(ItemStack itemStack, EntityHuman entityhuman, IEnvironment env,
			BlockPosition clicked, EnumDirection dir, float cX, float cY, float cZ, boolean isBorderPlace) {
		CraftHumanEntity player = entityhuman.getBukkitEntity();
		VirtualItem<?> virtualItem = items.getHandler(itemStack.getItem().getClass());

		int data = itemStack.getData();
		int count = itemStack.count;

		boolean flag;
		Map<BlockPosition, IBlockData> changes;
		env.startRecordingChanges();
		try {
			flag = virtualItem.interactWith(itemStack, entityhuman, env, clicked, dir, cX, cY, cZ, isBorderPlace);
		} finally {
			changes = env.stopRecordingChanges();
		}
		debug.log(() -> "Interact with flag: " + flag);

		int newData = itemStack.getData();
		int newCount = itemStack.count;
		itemStack.count = count;
		itemStack.setData(data);

		if (!flag)
			return false;

		if (!changes.isEmpty()) {
			// the first block the item changed is the one it placed, e.g. a door's lower
			// half or the slab it made double
			BlockPosition placedAt = changes.keySet().iterator().next();
			IBlockData newBlockData = env.getType(placedAt);

			@SuppressWarnings("deprecation")
			org.bukkit.Material mat = org.bukkit.Material.getMaterial(Block.getId(newBlockData.getBlock()));

			Location bukkitPlacedAt = Utils.convertPosToLoc(env.getRealWorld(), placedAt);
			VirtualBlockPlaceEvent ev = new VirtualBlockPlaceEvent((Player) player, bukkitPlacedAt, mat, env);
			events.call(ev);

			if (ev.isCancelled()) {
				undo(env, changes);
				return false;
			}

			if (newBlockData.getBlock() instanceof IContainer)
				env.broadcastTile(env.getTileEntity(placedAt));
		}

		if (itemStack.count == count && itemStack.getData() == data) {
			itemStack.setData(newData);
			itemStack.count = newCount;
		}

		return true;
	}

	/**
	 * Puts the blocks back as they were, the last changed first. Their neighbours
	 * aren't updated, as the changes to them are undone too.
	 */
	private static void undo(IVirtualWorld env, Map<BlockPosition, IBlockData> changes) {
		List<Map.Entry<BlockPosition, IBlockData>> lastFirst = new ArrayList<>(changes.entrySet());
		Collections.reverse(lastFirst);

		for (Map.Entry<BlockPosition, IBlockData> change : lastFirst) {
			env.setBlock(change.getKey(), change.getValue(), IVirtualWorld.SEND_TO_VIEWERS);
		}
	}

	/**
	 * Sends the player what their client changed when it predicted them using the
	 * block, for when the use was refused.
	 */
	private void resyncPredicted(IEnvironment env, IBlockData data, EntityHuman human, BlockPosition pos) {
		Block block = data.getBlock();
		for (BlockPosition predicted : blocks.handlerOf(block).getPredictedPositions(block, pos, data)) {
			VirtualBlockChangePacket.resync((EntityPlayer) human, env, predicted);
		}
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L105
	public void startDestroy(VirtualConnection conn, BlockPosition pos, EnumDirection dir) {
		IEnvironment env = conn.getEnvironment();
		EntityPlayer player = conn.getPlayer();
		VirtualPlayerInteractManager interactManager = conn.getInteractManager();

		VirtualInteractEvent event = new VirtualInteractEvent(player, Action.LEFT_CLICK_BLOCK, pos, dir,
				player.inventory.getItemInHand(), false, env);
		events.call(event);

		debug.log(() -> "startDestroy called, is interact event cancelled? " + event.isCancelled());

		if (event.isCancelled()) {
			VirtualBlockChangePacket.resync(player, env, pos);
			return;
		}

		debug.log(() -> "Is player creative?" + player.playerInteractManager.isCreative());

		if (player.playerInteractManager.isCreative()) {
			breakBlock(env, player, pos);
			return;
		}

		Block block = env.getType(pos).getBlock();

		debug.log(() -> "Is adventure or spectator mode? "
				+ NmsPlayers.isAdventureOrSpectator(player.playerInteractManager));

		if (!mayBreak(player, block))
			return;

		interactManager.setLastDigTick(interactManager.getCurrentTick());

		float f = 1f;

		if (event.getUseClickedBlock() == Event.Result.DENY) {
			resyncPredicted(env, env.getType(pos), player, pos);
		} else if (block.getMaterial() != Material.AIR) {
			// block.attack(nmsWorld, pos, player);
			f = block.getDamage(player, env.getRealWorld(), pos);
		}

		if (event.getUseItemInHand() == Event.Result.DENY) {
			if (f > 1.0f)
				VirtualBlockChangePacket.resync(player, env, pos);

			return;
		}

		VirtualBlockDamageEvent blockEvent = new VirtualBlockDamageEvent(
				Utils.convertPosToLoc(env.getRealWorld(), pos), player.getBukkitEntity(),
				player.getBukkitEntity().getItemInHand(), env);
		blockEvent.setInstaBreak(f >= 1.0f);
		events.call(blockEvent);

		if (blockEvent.isCancelled()) {
			VirtualBlockChangePacket.resync(player, env, pos);
			return;
		}

		if (blockEvent.isInstaBreak())
			f = 2.0f;

		if (block.getMaterial() != Material.AIR && f >= 1.0f) {

			breakBlock(env, player, pos);
		} else { // https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L190
			debug.log(() -> "Setting destroy-fields, pos " + pos);

			interactManager.setIsDestroying(true);
			interactManager.setDestroyPosition(pos);
			interactManager.setForce((int) (f * 10f));

			env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, (int) (f * 10f)));
		}

		return;
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L203
	public void stopDestroy(VirtualConnection conn, BlockPosition pos) {
		IEnvironment env = conn.getEnvironment();
		EntityPlayer player = conn.getPlayer();
		VirtualPlayerInteractManager interactManager = conn.getInteractManager();

		debug.log(() -> "stop destroy at pos: " + pos.toString() + ", destroy pos: "
				+ interactManager.getDestroyPosition());
		if (pos.equals(interactManager.getDestroyPosition())) {
			interactManager.setCurrentTick(MinecraftServer.currentTick);
			int diggingFor = interactManager.getCurrentTick() - interactManager.getLastDigTick();

			Block block = env.getType(pos).getBlock();
			if (block.getMaterial() != Material.AIR) {
				float f = block.getDamage(player, env.getRealWorld(), pos) * (float) (diggingFor + 1);

				if (f >= 0.7F) {
					interactManager.setIsDestroying(false);
					env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, -1));
					breakBlock(env, player, pos);
				} else { // TODO:

				}
			}
		} else {
			VirtualBlockChangePacket.resync(player, env, pos);
		}
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L231
	public void abortDestroy(VirtualConnection conn) {
		IEnvironment env = conn.getEnvironment();
		EntityPlayer player = conn.getPlayer();
		VirtualPlayerInteractManager interactManager = conn.getInteractManager();
		interactManager.setIsDestroying(false);

		BlockPosition pos = interactManager.getDestroyPosition();
		env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, -1));
		return;
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L249
	private boolean breakBlock(IEnvironment env, EntityPlayer player, BlockPosition pos) {
		ItemStack inHand = player.inventory.getItemInHand();
		boolean isSword = player.playerInteractManager.isCreative() && inHand != null
				&& inHand.getItem() instanceof ItemSword;
		if (env.getTileEntity(pos) == null && !isSword) {
			PacketPlayOutBlockChange packet = new VirtualBlockChangePacket(env, pos).getPacket();
			packet.block = Blocks.AIR.getBlockData();
			player.playerConnection.sendPacket(packet);

		}

		VirtualBlockBreakEvent ev = new VirtualBlockBreakEvent(Utils.convertPosToLoc(env.getRealWorld(), pos), env);
		ev.setCancelled(isSword);

		IBlockData nmsData = env.getType(pos);
		Block nmsBlock = nmsData.getBlock();

		events.call(ev);
		TileEntity tile = env.getTileEntity(pos);

		debug.log(() -> "Is block break event cancelled? " + ev.isCancelled());

		if (ev.isCancelled()) {
			if (isSword)
				return false;

			VirtualBlockChangePacket.resync(player, env, pos);
			return false;
		}

		if (nmsBlock == Blocks.AIR)
			return false;

		if (!mayBreak(player, nmsBlock))
			return false;

		env.broadcast(new PacketPlayOutWorldEvent(2001, pos, Block.getCombinedId(nmsData), false));

		boolean couldSet = env.setBlock(pos, Blocks.AIR.getBlockData(), IVirtualWorld.NOTIFY_AND_SEND);
		if (couldSet)
			blocks.handlerOf(nmsBlock).postBreak(nmsBlock, env, pos, nmsData);

		if (player.playerInteractManager.isCreative())
			VirtualBlockChangePacket.resync(player, env, pos);
		else {
			ItemStack held = player.inventory.getItemInHand();
			boolean flag = NmsPlayers.canHarvest(player, nmsBlock);

			if (held != null) {
				NmsItems.onBlockBroken(held, null, nmsBlock, pos, player);
				if (held.count == 0)
					NmsPlayers.destroyItemInHand(player);
			}

			if (flag && couldSet) {
				// the tile entity was taken before the block was removed, so handlers like
				// the skull's still get what was in it
				blocks.handlerOf(nmsBlock).onBlockHarvested(nmsBlock, env, pos, nmsData, env.getBlockBag(), tile);
			}
		}

		return couldSet;
	}

	/**
	 * @return whether the player's game mode lets them break the block: a spectator
	 *         can't, and in adventure mode they need an item that can break it,
	 *         unless they may build
	 */
	private static boolean mayBreak(EntityPlayer player, Block block) {
		if (!NmsPlayers.isAdventureOrSpectator(player.playerInteractManager))
			return true;
		if (player.playerInteractManager.getGameMode() == EnumGamemode.SPECTATOR)
			return false;
		if (player.abilities.mayBuild)
			return true;

		ItemStack item = player.inventory.getItemInHand();
		return item != null && NmsItems.canDestroy(item, block);
	}
}

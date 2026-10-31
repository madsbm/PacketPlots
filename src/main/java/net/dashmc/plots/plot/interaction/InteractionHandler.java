package net.dashmc.plots.plot.interaction;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.VirtualBlockBreakEvent;
import net.dashmc.plots.events.VirtualBlockDamageEvent;
import net.dashmc.plots.events.VirtualBlockPlaceEvent;
import net.dashmc.plots.events.VirtualInteractEvent;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.plot.data.VirtualItemRegistry;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockCommand;
import net.minecraft.server.v1_8_R3.BlockContainer;
import net.minecraft.server.v1_8_R3.BlockDoor;
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

	public boolean interact(IEnvironment env, EntityHuman human, ItemStack item, BlockPosition pos,
			EnumDirection dir, float cX, float cY, float cZ, boolean isBorderPlace) {
		IBlockData blockData = isBorderPlace ? env.getRealWorld().getType(pos) : env.getType(pos);

		if (!env.isOwner(human)) {
			handleGateMismatch(env, blockData, human, pos);
			return false;
		}

		boolean result = false;

		env.getDebug().log(() -> "Interact IBD:" + blockData.getBlock().toString());
		env.getDebug().log(() -> "Block is not air?: " + (blockData.getBlock() != Blocks.AIR));

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
			env.getEvents().call(ev);

			if (ev.getUseClickedBlock() == Event.Result.DENY) {
				handleGateMismatch(env, blockData, human, pos);
				result = ev.getUseItemInHand() != Event.Result.ALLOW;
			} else if (gameMode == GameMode.SPECTATOR) {
				// TODO: implement
			} else if (!human.isSneaking() || item == null) {
				result = env.getBlockHandlers().interact(env, pos, blockData, human, dir, cX, cY, cZ);
			}

			env.getDebug().log(() -> "Item is: " + item);
			// not lazy, as result changes below
			env.getDebug().log("Result is: " + result);

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
		BlockPosition placedAt = clicked.shift(dir);
		Location bukkitPlacedAt = Utils.convertPosToLoc(env.getRealWorld(), clicked);

		IBlockData prevBlockData = env.getType(placedAt);
		boolean flag = virtualItem.interactWith(itemStack, entityhuman, env, clicked, dir, cX, cY, cZ,
				isBorderPlace);
		env.getDebug().log(() -> "Interact with flag: " + flag);

		int newData = itemStack.getData();
		int newCount = itemStack.count;
		itemStack.count = count;
		itemStack.setData(data);

		if (flag) {
			IBlockData newBlockData = env.getType(placedAt);

			@SuppressWarnings("deprecation")
			org.bukkit.Material mat = org.bukkit.Material.getMaterial(Block.getId(newBlockData.getBlock()));

			VirtualBlockPlaceEvent ev = new VirtualBlockPlaceEvent((Player) player, bukkitPlacedAt, mat, env);
			env.getEvents().call(ev);

			if (ev.isCancelled()) {
				env.setBlock(placedAt, prevBlockData, 3);
				return false;
			} else {
				if (itemStack.count == count && itemStack.getData() == data) {
					itemStack.setData(newData);
					itemStack.count = newCount;
				}

				if (!(newBlockData.getBlock() instanceof BlockContainer)) {
					env.getBlockHandlers().onPlace(env, placedAt, newBlockData);
				}

				if (newBlockData.getBlock() instanceof IContainer)
					env.broadcastTile(env.getTileEntity(placedAt));

			}
		}

		return flag;
	}

	private void handleGateMismatch(IEnvironment env, IBlockData ibd, EntityHuman entity, BlockPosition pos) {
		EntityPlayer player = (EntityPlayer) entity;

		if (ibd.getBlock() instanceof BlockDoor) {
			boolean bottom = ibd.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;

			VirtualBlockChangePacket.resync(player, env, pos);
			VirtualBlockChangePacket.resync(player, env, bottom ? pos.up() : pos.down());
		} else if (ibd.getBlock() == Blocks.TRAPDOOR) {
			VirtualBlockChangePacket.resync(player, env, pos);
		}
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L105
	public void startDestroy(VirtualConnection conn, BlockPosition pos, EnumDirection dir) {
		IEnvironment env = conn.getEnvironment();
		EntityPlayer player = conn.getPlayer();
		VirtualPlayerInteractManager interactManager = conn.getInteractManager();

		VirtualInteractEvent event = new VirtualInteractEvent(player, Action.LEFT_CLICK_BLOCK, pos, dir,
				player.inventory.getItemInHand(), false, env);
		env.getEvents().call(event);

		env.getDebug().log(() -> "startDestroy called, is interact event cancelled? " + event.isCancelled());

		if (event.isCancelled()) {
			VirtualBlockChangePacket.resync(player, env, pos);
			return;
		}

		env.getDebug().log(() -> "Is player creative?" + player.playerInteractManager.isCreative());

		if (player.playerInteractManager.isCreative()) {
			breakBlock(env, player, pos);
			return;
		}

		Block block = env.getType(pos).getBlock();

		env.getDebug().log(() -> "Is survival or adventure mode?" + player.playerInteractManager.c());

		if (!mayBreak(player, block))
			return;

		interactManager.setLastDigTick(interactManager.getCurrentTick());

		float f = 1f;

		if (event.getUseClickedBlock() == Event.Result.DENY) {
			handleGateMismatch(env, env.getType(pos), player, pos);
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
		env.getEvents().call(blockEvent);

		if (blockEvent.isCancelled()) {
			VirtualBlockChangePacket.resync(player, env, pos);
			return;
		}

		if (blockEvent.isInstaBreak())
			f = 2.0f;

		if (block.getMaterial() != Material.AIR && f >= 1.0f) {

			breakBlock(env, player, pos);
		} else { // https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L190
			env.getDebug().log(() -> "Setting destroy-fields, pos " + pos);

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

		env.getDebug().log(() -> "stop destroy at pos: " + pos.toString() + ", destroy pos: "
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
		boolean isSword = player.playerInteractManager.getGameMode().d() && player.bA() != null
				&& player.bA().getItem() instanceof ItemSword;
		if (env.getTileEntity(pos) == null && !isSword) {
			PacketPlayOutBlockChange packet = new VirtualBlockChangePacket(env, pos).getPacket();
			packet.block = Blocks.AIR.getBlockData();
			player.playerConnection.sendPacket(packet);

		}

		VirtualBlockBreakEvent ev = new VirtualBlockBreakEvent(Utils.convertPosToLoc(env.getRealWorld(), pos), env);
		ev.setCancelled(isSword);

		IBlockData nmsData = env.getType(pos);
		Block nmsBlock = nmsData.getBlock();

		env.getEvents().call(ev);
		TileEntity tile = env.getTileEntity(pos);

		env.getDebug().log(() -> "Is block break event cancelled? " + ev.isCancelled());

		if (ev.isCancelled()) {
			if (isSword)
				return false;

			VirtualBlockChangePacket.resync(player, env, pos);
			return false;
		}

		if (nmsBlock == Blocks.AIR)
			return false;

		if (nmsBlock == Blocks.SKULL && !player.playerInteractManager.isCreative()) {
			env.getBlockHandlers().harvestBlock(nmsBlock, nmsData, env, pos, env.getBlockBag(), tile);

			return env.setBlock(pos, Blocks.AIR.getBlockData(), 3);
		}

		if (!mayBreak(player, nmsBlock))
			return false;

		env.broadcast(new PacketPlayOutWorldEvent(2001, pos, Block.getCombinedId(nmsData), false));

		boolean couldSet = env.setBlock(pos, Blocks.AIR.getBlockData(), 3);

		if (player.playerInteractManager.isCreative())
			VirtualBlockChangePacket.resync(player, env, pos);
		else {
			ItemStack held = player.bZ();
			boolean flag = player.b(nmsBlock);

			if (held != null) {
				held.a(null, nmsBlock, pos, player);
				if (held.count == 0)
					player.ca();
			}

			if (flag && couldSet) {
				// nmsBlock.a(nmsWorld, player, pos, nmsData, tile);
				env.getBlockHandlers().harvestBlock(nmsBlock, nmsData, env, pos, env.getBlockBag(), tile);
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
		if (!player.playerInteractManager.c())
			return true;
		if (player.playerInteractManager.getGameMode() == EnumGamemode.SPECTATOR)
			return false;
		if (player.cn())
			return true;

		ItemStack item = player.bZ();
		return item != null && item.c(block);
	}
}

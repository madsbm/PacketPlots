package net.dashmc.plots.plot.interaction;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;

import net.dashmc.plots.events.VirtualBlockBreakEvent;
import net.dashmc.plots.events.VirtualBlockDamageEvent;
import net.dashmc.plots.events.VirtualInteractEvent;
import net.dashmc.plots.packets.extensions.VirtualBlockChangePacket;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.VirtualBlock;
import net.dashmc.plots.plot.data.VirtualItem;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockCommand;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
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
public class InteractionHandler {

	public boolean interact(IEnvironment env, EntityHuman human, ItemStack item, BlockPosition pos,
			EnumDirection dir, float cX, float cY, float cZ, boolean isBorderPlace) {
		IBlockData blockData = isBorderPlace ? env.getRealWorld().getType(pos) : env.getType(pos);

		if (!env.isOwner(human)) {
			handleGateMismatch(env, blockData, human, pos);
			return false;
		}

		boolean result = false;

		Debug.log(() -> "Interact IBD:" + blockData.getBlock().toString());
		Debug.log(() -> "Block is not air?: " + (blockData.getBlock() != Blocks.AIR));

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
			Bukkit.getPluginManager().callEvent(ev);

			if (ev.getUseClickedBlock() == Event.Result.DENY) {
				handleGateMismatch(env, blockData, human, pos);
				result = ev.getUseItemInHand() != Event.Result.ALLOW;
			} else if (gameMode == GameMode.SPECTATOR) {
				// TODO: implement
			} else if (!human.isSneaking() || item == null) {
				result = VirtualBlock.interact(env, pos, blockData, human, dir, cX, cY, cZ);
			}

			Debug.log(() -> "Item is: " + item);
			Debug.log(() -> "Result is: " + result);

			if (item != null && !result) {
				int data = item.getData();
				int count = item.count;

				result = VirtualItem.placeItem(item, human, env, pos, dir, cX, cY, cZ, isBorderPlace);

				if (gameMode == GameMode.CREATIVE) {
					item.setData(data);
					item.count = count;
				}
			}
		}

		return result;
	}

	private void handleGateMismatch(IEnvironment env, IBlockData ibd, EntityHuman entity, BlockPosition pos) {
		EntityPlayer player = (EntityPlayer) entity;

		if (ibd.getBlock() instanceof BlockDoor) {
			boolean bottom = ibd.get(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER;

			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
			player.playerConnection
					.sendPacket(new VirtualBlockChangePacket(env, bottom ? pos.up() : pos.down()).getPacket());
		} else if (ibd.getBlock() == Blocks.TRAPDOOR) {
			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
		}
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L105
	public void startDestroy(IEnvironment env, EntityPlayer player, BlockPosition pos, EnumDirection dir) {
		VirtualInteractEvent event = new VirtualInteractEvent(player, Action.LEFT_CLICK_BLOCK, pos, dir,
				player.inventory.getItemInHand(), false, env);
		Bukkit.getPluginManager().callEvent(event);

		Debug.log(() -> "startDestroy called, is interact event cancelled? " + event.isCancelled());

		if (event.isCancelled()) {
			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
			TileEntity tile = env.getTileEntity(pos);
			if (tile != null)
				player.playerConnection.sendPacket(tile.getUpdatePacket());
			return;
		}

		Debug.log(() -> "Is player creative?" + player.playerInteractManager.isCreative());

		if (player.playerInteractManager.isCreative()) {
			breakBlock(env, player, pos);
			return;
		}

		Block block = env.getType(pos).getBlock();

		Debug.log(() -> "Is survival or adventure mode?" + player.playerInteractManager.c());

		if (player.playerInteractManager.c()) {
			if (player.playerInteractManager.getGameMode() == EnumGamemode.SPECTATOR)
				return;

			if (!player.cn()) {
				ItemStack item = player.bZ();
				if (item == null)
					return;
				if (!item.c(block))
					return;
			}
		}

		VirtualPlayerInteractManager playerInteractManager = (VirtualPlayerInteractManager) player.playerInteractManager;
		playerInteractManager.setLastDigTick(playerInteractManager.getCurrentTick());

		float f = 1f;

		if (event.getUseClickedBlock() == Event.Result.DENY) {
			handleGateMismatch(env, env.getType(pos), player, pos);
		} else if (block.getMaterial() != Material.AIR) {
			// block.attack(nmsWorld, pos, player);
			f = block.getDamage(player, env.getRealWorld(), pos);
		}

		if (event.getUseItemInHand() == Event.Result.DENY) {
			if (f > 1.0f)
				player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());

			return;
		}

		VirtualBlockDamageEvent blockEvent = new VirtualBlockDamageEvent(
				Utils.convertPosToLoc(env.getRealWorld(), pos), player.getBukkitEntity(),
				player.getBukkitEntity().getItemInHand(), env);
		blockEvent.setInstaBreak(f >= 1.0f);
		Bukkit.getPluginManager().callEvent(blockEvent);

		if (blockEvent.isCancelled()) {
			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
			return;
		}

		if (blockEvent.isInstaBreak())
			f = 2.0f;

		if (block.getMaterial() != Material.AIR && f >= 1.0f) {

			breakBlock(env, player, pos);
		} else { // https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L190
			Debug.log(() -> "Setting destroy-fields, pos " + pos);
			VirtualPlayerInteractManager interactManager = (VirtualPlayerInteractManager) player.playerInteractManager;

			interactManager.setIsDestroying(true);
			interactManager.setDestroyPosition(pos);
			interactManager.setForce((int) (f * 10f));

			env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, (int) (f * 10f)));
		}

		return;
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L203
	public void stopDestroy(IEnvironment env, EntityPlayer player, BlockPosition pos) {
		VirtualPlayerInteractManager interactManager = (VirtualPlayerInteractManager) player.playerInteractManager;

		Debug.log(() -> "stop destroy at pos: " + pos.toString() + ", destroy pos: "
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
			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
		}
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L231
	public void abortDestroy(IEnvironment env, EntityPlayer player) {
		VirtualPlayerInteractManager interactManager = (VirtualPlayerInteractManager) player.playerInteractManager;
		interactManager.setIsDestroying(false);

		BlockPosition pos = interactManager.getDestroyPosition();
		env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, -1));
		return;
	}

	// https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PlayerInteractManager.java#L249
	private boolean breakBlock(IEnvironment env, EntityPlayer player, BlockPosition pos) {
		VirtualBlockBreakEvent ev = null;

		boolean isSword = player.playerInteractManager.getGameMode().d() && player.bA() != null
				&& player.bA().getItem() instanceof ItemSword;
		if (env.getTileEntity(pos) == null && !isSword) {
			PacketPlayOutBlockChange packet = new VirtualBlockChangePacket(env, pos).getPacket();
			packet.block = Blocks.AIR.getBlockData();
			player.playerConnection.sendPacket(packet);

		}

		ev = new VirtualBlockBreakEvent(Utils.convertPosToLoc(env.getRealWorld(), pos), env);
		ev.setCancelled(isSword);

		IBlockData nmsData = env.getType(pos);
		Block nmsBlock = nmsData.getBlock();

		Bukkit.getPluginManager().callEvent(ev);
		TileEntity tile = env.getTileEntity(pos);

		Debug.log(() -> "Is block break event cancelled? " + ev.isCancelled());

		if (ev.isCancelled()) {
			if (isSword)
				return false;

			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());

			if (tile != null)
				player.playerConnection.sendPacket(tile.getUpdatePacket());

			return false;
		}

		if (nmsBlock == Blocks.AIR)
			return false;

		if (nmsBlock == Blocks.SKULL && !player.playerInteractManager.isCreative()) {
			VirtualBlock.harvestBlock(nmsBlock, nmsData, env, pos, env.getBlockBag(), tile);

			return env.setBlock(pos, Blocks.AIR.getBlockData(), 3);
		}

		if (player.playerInteractManager.c()) {
			if (player.playerInteractManager.getGameMode() == EnumGamemode.SPECTATOR)
				return false;
			if (!player.cn()) {
				ItemStack item = player.bZ();
				if (item == null)
					return false;
				if (!item.c(nmsBlock))
					return false;
			}
		}

		env.broadcast(new PacketPlayOutWorldEvent(2001, pos, Block.getCombinedId(nmsData), false));

		boolean couldSet = env.setBlock(pos, Blocks.AIR.getBlockData(), 3);

		if (player.playerInteractManager.isCreative())
			player.playerConnection.sendPacket(new VirtualBlockChangePacket(env, pos).getPacket());
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
				VirtualBlock.harvestBlock(nmsBlock, nmsData, env, pos, env.getBlockBag(), tile);
			}
		}

		return couldSet;
	}
}

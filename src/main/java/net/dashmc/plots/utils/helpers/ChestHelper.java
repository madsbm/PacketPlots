package net.dashmc.plots.utils.helpers;

import java.util.logging.Logger;

import org.bukkit.craftbukkit.v1_8_R3.event.CraftEventFactory;

import lombok.experimental.UtilityClass;
import net.dashmc.plots.nms.NmsInventories;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IEnvironment;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockChest;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.ContainerChest;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.InventoryEnderChest;
import net.minecraft.server.v1_8_R3.InventoryLargeChest;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockAction;
import net.minecraft.server.v1_8_R3.PlayerInventory;
import net.minecraft.server.v1_8_R3.TileEntityChest;

@UtilityClass
public class ChestHelper {
	/**
	 * 
	 * @param player
	 * @param environment
	 * @param sessionTerminated if the user quits whilst a container is open this
	 *                          will be true. otherwise false.
	 * @param logger            where to warn about an inventory it doesn't know
	 * @return
	 */
	public boolean closeCurrentChest(EntityPlayer player, IEnvironment environment, boolean sessionTerminated,
			Logger logger) {
		PlayerInventory playerInventory = player.inventory;
		if (!(player.activeContainer instanceof ContainerChest))
			return false;

		IInventory inventory = NmsInventories.getInventory((ContainerChest) player.activeContainer);
		if (isCustomInventory(inventory))
			return false;

		if (!sessionTerminated)
			CraftEventFactory.handleInventoryCloseEvent(player);

		boolean success = false;
		if (inventory instanceof TileEntityChest) { // single chest
			if (!isChestLocationValid(inventory, environment))
				return false;

			success = closeContainer(player, inventory, environment, sessionTerminated);
		} else if (inventory instanceof InventoryLargeChest) { // chest consisting of two chests
			InventoryLargeChest doubleChest = (InventoryLargeChest) inventory;

			boolean isLeftValid = isChestLocationValid(doubleChest.left, environment);
			boolean isRightValid = isChestLocationValid(doubleChest.right, environment);
			if (isLeftValid ^ isRightValid) {
				throw new RuntimeException("Location of double chest is half outside of virtual environment ("
						+ environment.getOwnerUuid() + ")");
			} else if (!isLeftValid && !isRightValid)
				return false;

			success = closeContainer(player, doubleChest.left, environment, sessionTerminated)
					&& closeContainer(player, doubleChest.right, environment, sessionTerminated);
		} else if (inventory instanceof InventoryEnderChest) {
			InventoryEnderChest enderChest = (InventoryEnderChest) inventory;
			enderChest.closeContainer(player);

			success = true;

		} else {
			logger.warning("Window close packet sent with unrecognized inventory: " + inventory.getClass());
		}

		if (success) {
			if (playerInventory.getCarried() != null) {
				player.drop(playerInventory.getCarried(), false);
				playerInventory.setCarried(null);
			}

			player.activeContainer = player.defaultContainer;
			return true;
		}
		// intercept packet to prevent dupes - do nothing tho
		return true;
	}

	private boolean isCustomInventory(IInventory inventory) {
		Class<?> enclosing = inventory.getClass().getEnclosingClass();
		return enclosing != null && enclosing.getName().endsWith(".CraftInventoryCustom");
	}

	private boolean isChestLocationValid(IInventory inventory, IEnvironment env) {
		if (!(inventory instanceof TileEntityChest))
			return false;

		TileEntityChest chest = (TileEntityChest) inventory;
		BlockPosition pos = chest.getPosition();
		if (!env.contains(pos))
			return false;
		return env.getType(pos).getBlock() instanceof BlockChest;
	}

	private boolean closeContainer(EntityHuman human, IInventory inventory, IEnvironment env,
			boolean sessionTerminated) {
		if (!(inventory instanceof TileEntityChest))
			return false;

		TileEntityChest chest = (TileEntityChest) inventory;
		BlockPosition pos = chest.getPosition();
		if (!env.contains(pos))
			return false;
		else if (human.isSpectator())
			return true;

		// let them close even though it wasnt a success
		if (!(env.getType(pos).getBlock() instanceof BlockChest))
			return true;

		NmsTileEntities.addPlayersUsing(chest, -1);

		if (sessionTerminated)
			return true;

		Block block = env.getType(chest.getPosition()).getBlock();
		PacketPlayOutBlockAction chestClosePacket = new PacketPlayOutBlockAction(chest.getPosition(), block, 1, 0);
		env.broadcast(chestClosePacket);

		return true;
	}

}

package net.dashmc.plots.plot.interaction;

import java.util.logging.Logger;

import org.bukkit.craftbukkit.v1_8_R3.event.CraftEventFactory;

import net.dashmc.plots.nms.NmsInventories;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.ClassRegistry;
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

/**
 * Closes the chest a player has open in a virtual environment, which the
 * server doesn't know of. Each kind of inventory is closed by the closer of its
 * class, or else of its closest superclass with one.
 */
public class ChestCloser {
	private final ClassRegistry<InventoryCloser<?>> closers = new ClassRegistry<>();
	// where to warn about an inventory without a closer
	private final Logger logger;

	public ChestCloser(Logger logger) {
		this.logger = logger;

		register(TileEntityChest.class, ChestCloser::closeSingleChest);
		register(InventoryLargeChest.class, ChestCloser::closeDoubleChest);
		register(InventoryEnderChest.class, (player, enderChest, environment, sessionTerminated) -> {
			enderChest.closeContainer(player);
			return Result.CLOSED;
		});
	}

	public <T extends IInventory> void register(Class<T> inventoryClass, InventoryCloser<? super T> closer) {
		closers.register(inventoryClass, closer);
	}

	/**
	 * @param sessionTerminated if the user quits whilst a container is open this
	 *                          will be true. otherwise false.
	 * @return true if the chest was the environment's, so the server shouldn't
	 *         close it too
	 */
	public boolean closeCurrentChest(EntityPlayer player, IEnvironment environment, boolean sessionTerminated) {
		PlayerInventory playerInventory = player.inventory;
		if (!(player.activeContainer instanceof ContainerChest))
			return false;

		IInventory inventory = NmsInventories.getInventory((ContainerChest) player.activeContainer);
		if (isCustomInventory(inventory))
			return false;

		if (!sessionTerminated)
			CraftEventFactory.handleInventoryCloseEvent(player);

		switch (close(player, inventory, environment, sessionTerminated)) {
			case NOT_VIRTUAL:
				return false;
			case CLOSED:
				if (playerInventory.getCarried() != null) {
					player.drop(playerInventory.getCarried(), false);
					playerInventory.setCarried(null);
				}

				player.activeContainer = player.defaultContainer;
				return true;
			default:
				// intercept packet to prevent dupes - do nothing tho
				return true;
		}
	}

	@SuppressWarnings("unchecked")
	private <T extends IInventory> Result close(EntityPlayer player, T inventory, IEnvironment environment,
			boolean sessionTerminated) {
		// the closer is for the inventory's class or a superclass of it, so it takes
		// the inventory
		InventoryCloser<T> closer = (InventoryCloser<T>) closers.get(inventory.getClass());
		if (closer == null) {
			logger.warning("Window close packet sent with unrecognized inventory: " + inventory.getClass());
			return Result.FAILED;
		}

		return closer.close(player, inventory, environment, sessionTerminated);
	}

	private static Result closeSingleChest(EntityPlayer player, TileEntityChest chest, IEnvironment environment,
			boolean sessionTerminated) {
		if (!isChestLocationValid(chest, environment))
			return Result.NOT_VIRTUAL;

		return closeContainer(player, chest, environment, sessionTerminated) ? Result.CLOSED : Result.FAILED;
	}

	// a chest consisting of two chests
	private static Result closeDoubleChest(EntityPlayer player, InventoryLargeChest doubleChest,
			IEnvironment environment, boolean sessionTerminated) {
		boolean isLeftValid = isChestLocationValid(doubleChest.left, environment);
		boolean isRightValid = isChestLocationValid(doubleChest.right, environment);
		if (isLeftValid ^ isRightValid) {
			throw new RuntimeException("Location of double chest is half outside of virtual environment ("
					+ environment.getOwnerUuid() + ")");
		} else if (!isLeftValid && !isRightValid)
			return Result.NOT_VIRTUAL;

		boolean closed = closeContainer(player, doubleChest.left, environment, sessionTerminated)
				&& closeContainer(player, doubleChest.right, environment, sessionTerminated);
		return closed ? Result.CLOSED : Result.FAILED;
	}

	private static boolean isCustomInventory(IInventory inventory) {
		Class<?> enclosing = inventory.getClass().getEnclosingClass();
		return enclosing != null && enclosing.getName().endsWith(".CraftInventoryCustom");
	}

	private static boolean isChestLocationValid(IInventory inventory, IEnvironment env) {
		if (!(inventory instanceof TileEntityChest))
			return false;

		TileEntityChest chest = (TileEntityChest) inventory;
		BlockPosition pos = chest.getPosition();
		if (!env.contains(pos))
			return false;
		return env.getType(pos).getBlock() instanceof BlockChest;
	}

	private static boolean closeContainer(EntityHuman human, IInventory inventory, IEnvironment env,
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

	/**
	 * Closes a kind of inventory shown in a chest container, for a ChestCloser.
	 */
	@FunctionalInterface
	public interface InventoryCloser<T extends IInventory> {
		Result close(EntityPlayer player, T inventory, IEnvironment environment, boolean sessionTerminated);
	}

	public enum Result {
		/**
		 * The inventory isn't the environment's, so the server closes it.
		 */
		NOT_VIRTUAL,
		CLOSED,
		/**
		 * The inventory is the environment's, but couldn't be closed.
		 */
		FAILED
	}
}

package net.dashmc.plots.plot.interaction;

import org.bukkit.craftbukkit.v1_8_R3.event.CraftEventFactory;

import net.dashmc.plots.nms.NmsInventories;
import net.dashmc.plots.nms.NmsTileEntities;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.ClassRegistry;
import net.minecraft.server.v1_8_R3.BlockChest;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.ContainerChest;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.InventoryEnderChest;
import net.minecraft.server.v1_8_R3.InventoryLargeChest;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockAction;
import net.minecraft.server.v1_8_R3.PlayerInventory;
import net.minecraft.server.v1_8_R3.TileEntityChest;

/**
 * Closes the chest a player has open in a virtual environment, which the
 * server would close as if it were in the real world. Each kind of inventory
 * is closed by the closer of its class, or else of its closest superclass with
 * one. Inventories without one, e.g. a minecart's chest or another plugin's
 * menu, are left to the server.
 */
public class ChestCloser {
	private final ClassRegistry<InventoryCloser<?>> closers = new ClassRegistry<>();

	public ChestCloser() {
		register(TileEntityChest.class, new SingleChestCloser());
		register(InventoryLargeChest.class, new DoubleChestCloser());
		register(InventoryEnderChest.class, new EnderChestCloser());
	}

	public <T extends IInventory> void register(Class<T> inventoryClass, InventoryCloser<? super T> closer) {
		closers.register(inventoryClass, closer);
	}

	/**
	 * @param sessionTerminated if the user quits whilst a container is open this
	 *                          will be true. otherwise false.
	 * @return true if the chest was closed here, so the server shouldn't close it
	 *         too
	 */
	public boolean closeCurrentChest(EntityPlayer player, IEnvironment environment, boolean sessionTerminated) {
		if (!(player.activeContainer instanceof ContainerChest))
			return false;

		IInventory inventory = NmsInventories.getInventory((ContainerChest) player.activeContainer);
		InventoryCloser<IInventory> closer = closerOf(inventory);
		if (closer == null || !closer.closesHere(inventory, environment))
			return false;

		if (!sessionTerminated)
			CraftEventFactory.handleInventoryCloseEvent(player);

		closer.close(player, inventory, environment, sessionTerminated);

		PlayerInventory playerInventory = player.inventory;
		if (playerInventory.getCarried() != null) {
			player.drop(playerInventory.getCarried(), false);
			playerInventory.setCarried(null);
		}

		player.activeContainer = player.defaultContainer;
		return true;
	}

	@SuppressWarnings("unchecked")
	private InventoryCloser<IInventory> closerOf(IInventory inventory) {
		// the closer is for the inventory's class or a superclass of it, so it takes
		// the inventory
		return (InventoryCloser<IInventory>) closers.get(inventory.getClass());
	}

	/**
	 * Closes a kind of inventory shown in a chest container, for a ChestCloser.
	 */
	public interface InventoryCloser<T extends IInventory> {
		/**
		 * @return whether the inventory is closed here rather than by the server,
		 *         e.g. because it's the environment's
		 */
		boolean closesHere(T inventory, IEnvironment environment);

		void close(EntityHuman human, T inventory, IEnvironment environment, boolean sessionTerminated);
	}

	private static class SingleChestCloser implements InventoryCloser<TileEntityChest> {
		@Override
		public boolean closesHere(TileEntityChest chest, IEnvironment environment) {
			return environment.contains(chest.getPosition());
		}

		@Override
		public void close(EntityHuman human, TileEntityChest chest, IEnvironment environment,
				boolean sessionTerminated) {
			closeChest(human, chest, environment, sessionTerminated);
		}
	}

	// a chest consisting of two chests
	private static class DoubleChestCloser implements InventoryCloser<InventoryLargeChest> {
		@Override
		public boolean closesHere(InventoryLargeChest doubleChest, IEnvironment environment) {
			boolean isLeftInside = isChestInside(doubleChest.left, environment);
			boolean isRightInside = isChestInside(doubleChest.right, environment);
			if (isLeftInside ^ isRightInside) {
				throw new RuntimeException("Location of double chest is half outside of virtual environment ("
						+ environment.getOwnerUuid() + ")");
			}

			return isLeftInside;
		}

		@Override
		public void close(EntityHuman human, InventoryLargeChest doubleChest, IEnvironment environment,
				boolean sessionTerminated) {
			closeChest(human, (TileEntityChest) doubleChest.left, environment, sessionTerminated);
			closeChest(human, (TileEntityChest) doubleChest.right, environment, sessionTerminated);
		}

		private static boolean isChestInside(IInventory half, IEnvironment environment) {
			return half instanceof TileEntityChest && environment.contains(((TileEntityChest) half).getPosition());
		}
	}

	// closing an ender chest is the same whether it's virtual or real, as the
	// virtual one is wrapped to show its lid to the environment
	private static class EnderChestCloser implements InventoryCloser<InventoryEnderChest> {
		@Override
		public boolean closesHere(InventoryEnderChest enderChest, IEnvironment environment) {
			return true;
		}

		@Override
		public void close(EntityHuman human, InventoryEnderChest enderChest, IEnvironment environment,
				boolean sessionTerminated) {
			enderChest.closeContainer(human);
		}
	}

	/**
	 * Closes the lid of a chest of the environment, like TileEntityChest#closeContainer
	 * does in the real world.
	 */
	private static void closeChest(EntityHuman human, TileEntityChest chest, IEnvironment environment,
			boolean sessionTerminated) {
		BlockPosition pos = chest.getPosition();
		IBlockData data = environment.getType(pos);
		// let them close a chest that was broken while it was open
		if (human.isSpectator() || !(data.getBlock() instanceof BlockChest))
			return;

		NmsTileEntities.addPlayersUsing(chest, -1);

		if (sessionTerminated)
			return;

		environment.broadcast(new PacketPlayOutBlockAction(pos, data.getBlock(), 1, 0));
	}
}

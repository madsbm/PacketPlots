package net.dashmc.plots.nms;

import net.minecraft.server.v1_8_R3.Container;
import net.minecraft.server.v1_8_R3.ContainerChest;
import net.minecraft.server.v1_8_R3.IInventory;
import net.minecraft.server.v1_8_R3.InventoryEnderChest;
import net.minecraft.server.v1_8_R3.TileEntityEnderChest;

/**
 * Containers and inventories.
 */
public class NmsInventories {

	/**
	 * Sends the container's viewers the slots that changed.
	 */
	public static void sendChanges(Container container) {
		container.b();
	}

	/**
	 * @return the inventory the chest container shows, e.g. a chest's
	 */
	public static IInventory getInventory(ContainerChest container) {
		return container.e();
	}

	/**
	 * Sets the ender chest the inventory is opened at.
	 */
	public static void setEnderChest(InventoryEnderChest inventory, TileEntityEnderChest enderChest) {
		inventory.a(enderChest);
	}
}

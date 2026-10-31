package net.dashmc.plots.plot.data;

import net.dashmc.plots.plot.IEnvironment;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;

/**
 * How an item is placed in a virtual environment. Registered for its class of
 * item in a VirtualItemRegistry.
 */
public abstract class VirtualItem<T extends Item> {

	/**
	 *
	 * @param item          the held item
	 * @param player        the player that tried interacting
	 * @param environment   the environment in which the player tried interacting
	 * @param pos           the position at which the user tried interacting
	 * @param direction     the direction of the block
	 * @param cX            the position of the crosshair on the block
	 * @param cY
	 * @param cZ
	 * @param isBorderPlace
	 * @return
	 */
	public abstract boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment,
			BlockPosition pos, EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace);

}

package net.dashmc.plots.plot.data;

import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.ClassRegistry;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;

/**
 * The item handlers, by the class of item each one handles. An item uses the
 * handler of its class, or else of its closest superclass with one, or else
 * can't be placed.
 */
public class VirtualItemRegistry {
	/**
	 * The handler of items without one of their own, which can't be placed.
	 */
	private static final VirtualItem<Item> UNPLACEABLE = new VirtualItem<Item>() {
		@Override
		public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment,
				BlockPosition pos, EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
			return false;
		}
	};

	private final ClassRegistry<VirtualItem<?>> handlers = new ClassRegistry<>();

	public <T extends Item> void register(Class<T> itemClass, VirtualItem<? super T> handler) {
		handlers.register(itemClass, handler);
	}

	/**
	 * Makes the items of the class unplaceable, even if a superclass of it has a
	 * handler.
	 */
	public void registerUnplaceable(Class<? extends Item> itemClass) {
		handlers.register(itemClass, UNPLACEABLE);
	}

	/**
	 * @return the handler of the item class, or else of its closest superclass with
	 *         one
	 */
	public VirtualItem<?> getHandler(Class<? extends Item> itemClass) {
		VirtualItem<?> handler = handlers.get(itemClass);
		return handler != null ? handler : UNPLACEABLE;
	}
}

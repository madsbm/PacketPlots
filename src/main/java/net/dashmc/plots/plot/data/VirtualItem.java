package net.dashmc.plots.plot.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.entity.Player;

import net.dashmc.plots.events.VirtualBlockPlaceEvent;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.items.VirtualItemBlock;
import net.dashmc.plots.plot.data.items.VirtualItemDoor;
import net.dashmc.plots.plot.data.items.VirtualItemSkull;
import net.dashmc.plots.plot.data.items.VirtualItemStep;
import net.dashmc.plots.utils.helpers.ClassRegistry;
import net.dashmc.plots.utils.misc.Debug;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockContainer;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.IContainer;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemBanner;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.ItemTallPlant;
import net.minecraft.server.v1_8_R3.ItemWaterLily;

public abstract class VirtualItem<T extends Item> {
	private static final ClassRegistry<VirtualItem<?>> virtualItems = new ClassRegistry<>();

	/**
	 * The handler of items without one of their own, which can't be placed.
	 */
	private static final VirtualItem<Item> UNPLACEABLE = new VirtualItem<Item>() {
		@Override
		public boolean interactWith(ItemStack item, EntityHuman player, IEnvironment environment,
				BlockPosition pos, EnumDirection direction, float cX, float cY, float cZ, boolean isBorderPlace) {
			return false;
		}

		@Override
		public Class<Item> getClazz() {
			return Item.class;
		}
	};

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

	public abstract Class<T> getClazz();

	/**
	 * @return the handler of the item class, or else of its closest superclass with
	 *         one
	 */
	public static VirtualItem<?> getHandler(Class<? extends Item> itemClass) {
		VirtualItem<?> handler = virtualItems.get(itemClass);
		return handler != null ? handler : UNPLACEABLE;
	}

	public static final boolean placeItem(ItemStack itemStack, EntityHuman entityhuman, IEnvironment env,
			BlockPosition clicked, EnumDirection dir, float cX, float cY, float cZ, boolean isBorderPlace) {
		CraftHumanEntity player = entityhuman.getBukkitEntity();
		VirtualItem<?> virtualItem = getHandler(itemStack.getItem().getClass());

		int data = itemStack.getData();
		int count = itemStack.count;
		BlockPosition placedAt = clicked.shift(dir);
		Location bukkitPlacedAt = Utils.convertPosToLoc(env.getRealWorld(), clicked);

		IBlockData prevBlockData = env.getType(placedAt);
		boolean flag = virtualItem.interactWith(itemStack, entityhuman, env, clicked, dir, cX, cY, cZ,
				isBorderPlace);
		Debug.log(() -> "Interact with flag: " + flag);

		int newData = itemStack.getData();
		int newCount = itemStack.count;
		itemStack.count = count;
		itemStack.setData(data);

		if (flag) {
			IBlockData newBlockData = env.getType(placedAt);

			@SuppressWarnings("deprecation")
			Material mat = Material.getMaterial(Block.getId(newBlockData.getBlock()));

			VirtualBlockPlaceEvent ev = new VirtualBlockPlaceEvent((Player) player, bukkitPlacedAt, mat, env);
			Bukkit.getPluginManager().callEvent(ev);

			if (ev.isCancelled()) {
				env.setBlock(placedAt, prevBlockData, 3);
				return false;
			} else {
				if (itemStack.count == count && itemStack.getData() == data) {
					itemStack.setData(newData);
					itemStack.count = newCount;
				}

				if (!(newBlockData.getBlock() instanceof BlockContainer)) {
					VirtualBlock.onPlace(env, placedAt, newBlockData);
				}

				if (newBlockData.getBlock() instanceof IContainer)
					env.broadcastTile(env.getTileEntity(placedAt));

			}
		}

		return flag;
	}

	public void register() {
		virtualItems.register(getClazz(), this);
	}

	static {
		// also handles ItemBlock's subclasses without a handler of their own, e.g. wool,
		// leaves, anvils and pistons
		new VirtualItemBlock().register();
		new VirtualItemDoor().register();
		new VirtualItemSkull().register();
		new VirtualItemStep().register();

		// ItemBlocks the ItemBlock handler would place wrong, so they stay unplaceable:
		// a banner's color comes from the item, a double plant needs its upper half and
		// a lily pad goes on the water
		virtualItems.register(ItemBanner.class, UNPLACEABLE);
		virtualItems.register(ItemTallPlant.class, UNPLACEABLE);
		virtualItems.register(ItemWaterLily.class, UNPLACEABLE);
	}

}

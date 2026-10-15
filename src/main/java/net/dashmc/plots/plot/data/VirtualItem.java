package net.dashmc.plots.plot.data;

import java.util.HashMap;
import java.util.function.BiFunction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftHumanEntity;
import org.bukkit.entity.Player;

import lombok.Getter;
import net.dashmc.plots.events.VirtualBlockPlaceEvent;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.data.items.VirtualItemAUX;
import net.dashmc.plots.plot.data.items.VirtualItemBlock;
import net.dashmc.plots.plot.data.items.VirtualItemCloth;
import net.dashmc.plots.plot.data.items.VirtualItemDoor;
import net.dashmc.plots.plot.data.items.VirtualItemLeaves;
import net.dashmc.plots.plot.data.items.VirtualItemMultiTexture;
import net.dashmc.plots.plot.data.items.VirtualItemSkull;
import net.dashmc.plots.plot.data.items.VirtualItemStep;
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
import net.minecraft.server.v1_8_R3.ItemStack;

public abstract class VirtualItem<T extends Item> {
	@Getter
	private static HashMap<Class<? extends Item>, VirtualItem<? extends Item>> virtualItems = new HashMap<>();

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

	@SuppressWarnings("unchecked")
	private static final <T extends Item, R> R getAndRun(Item item,
			BiFunction<VirtualItem<T>, T, R> callback) {
		if (item == null)
			return callback.apply(null, null);

		VirtualItem<? extends Item> virtualItem = virtualItems.get(item.getClass());
		if (virtualItem == null)
			return callback.apply(null, null);
		VirtualItem<T> typedVirtualItem = (VirtualItem<T>) virtualItem;

		Class<T> clazz = (Class<T>) virtualItem.getClazz();
		if (clazz == null)
			return callback.apply(typedVirtualItem, null);

		T castItem = clazz.cast(item);
		return callback.apply(typedVirtualItem, castItem);
	}

	public static final <T extends Item> boolean placeItem(ItemStack itemStack, EntityHuman entityhuman,
			IEnvironment env, BlockPosition clicked, EnumDirection dir, float cX, float cY, float cZ,
			boolean isBorderPlace) {
		CraftHumanEntity player = entityhuman.getBukkitEntity();

		return getAndRun(itemStack.getItem(), (BiFunction<VirtualItem<T>, T, Boolean>) (virtualItem, actualItem) -> {
			if (actualItem == null || virtualItem == null)
				return false;

			int data = itemStack.getData();
			int count = itemStack.count;
			BlockPosition placedAt = clicked.shift(dir);
			Location bukkitPlacedAt = Utils.convertPosToLoc(env.getRealWorld(), clicked);

			IBlockData prevBlockData = env.getType(placedAt);
			boolean flag = virtualItem.interactWith(itemStack, entityhuman, env, clicked, dir, cX, cY, cZ,
					isBorderPlace);
			Debug.log("Interact with flag: " + flag);

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
		});
	}

	public void register() {
		virtualItems.put(getClazz(), this);
	}

	static {
		new VirtualItemBlock().register();
		new VirtualItemMultiTexture().register();
		new VirtualItemCloth().register();
		new VirtualItemDoor().register();
		new VirtualItemSkull().register();
		new VirtualItemLeaves().register();
		new VirtualItemStep().register();
		new VirtualItemAUX().register();
	}

}

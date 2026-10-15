package net.dashmc.plots.plot.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.data.IDataHolder;
import net.dashmc.plots.events.BlockBagUpdatedEvent;
import net.dashmc.plots.events.PreBlockBagDepositEvent;
import net.dashmc.plots.utils.helpers.NBTHelper;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.NBTReadLimiter;
import net.minecraft.server.v1_8_R3.NBTTagCompound;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class BlockBag implements IDataHolder {
	private static final HashMap<EntityPlayer, BlockBag> blockBags = new HashMap<>();

	public static BlockBag getBlockBag(EntityPlayer player) {
		return blockBags.computeIfAbsent(player, uuid -> new BlockBag(player));
	}

	public static BlockBag getBlockBag(Player player) {
		return getBlockBag(((CraftPlayer) player).getHandle());
	}

	public static void removeBlockBag(Player player) {
		blockBags.remove(((CraftPlayer) player).getHandle());
	}

	public static void removeBlockBag(EntityPlayer player) {
		blockBags.remove(player);
	}

	public static BlockBag deserialize(DataInputStream inputStream, EntityPlayer player) throws IOException {
		BlockBag bag = new BlockBag(player);
		bag.deserialize(inputStream);
		return bag;
	}

	private final EntityPlayer player;
	private LinkedList<ItemStack> bag = new LinkedList<>();

	public int size() {
		return bag.size();
	}

	public void emptyIfPossible() {
		Debug.log("size of bag: " + bag.size());

		while (bag.size() > 0) {
			ItemStack item = bag.peekFirst();
			boolean hasSpace = player.inventory.pickup(item);

			if (hasSpace) {
				bag.removeFirst();
				continue;
			}

			break;
		}

		callUpdateEvent(false);
	}

	public void addAll(ItemStack[] items) {
		PreBlockBagDepositEvent pre = new PreBlockBagDepositEvent(items, this);
		Bukkit.getPluginManager().callEvent(pre);
		if (pre.isCancelled())
			return;

		for (ItemStack item : pre.getItems()) {
			if (item == null)
				continue;
			add(item, false, true);
		}

		callUpdateEvent(true);
	}

	private boolean add(ItemStack item, boolean callEvents, boolean tryAddToPlayerInventory) {
		if (item == null)
			return false;

		if (callEvents) {
			PreBlockBagDepositEvent preUpdateEvent = new PreBlockBagDepositEvent(item, this);
			Bukkit.getPluginManager().callEvent(preUpdateEvent);
			if (preUpdateEvent.isCancelled())
				return false;

			if (preUpdateEvent.getItems().length != 1)
				// this should never happen as it's currently declared final - but that might
				// change later
				throw new RuntimeException("Length mismatch. Array length got modified.");

			item = preUpdateEvent.getItems()[0];
			if (item == null)
				return false;
		}

		if (tryAddToPlayerInventory && player.inventory.pickup(item))
			return false;

		bag.addLast(item);
		if (callEvents)
			callUpdateEvent(true);

		return true;
	}

	private BlockBagUpdatedEvent callUpdateEvent(boolean isDeposit) {
		BlockBagUpdatedEvent event = new BlockBagUpdatedEvent(isDeposit, this);
		Bukkit.getPluginManager().callEvent(event);
		return event;
	}

	public void addToBag(ItemStack item) {
		add(item, true, false);
	}

	public void add(Item item) {
		if (item == null)
			return;

		add(new ItemStack(item), true, true);
	}

	public void add(ItemStack item) {
		add(item, true, true);
	}

	@Override
	public void deserialize(DataInputStream stream) throws IOException {
		int listSize = stream.readInt();
		for (int i = 0; i < listSize; i++) {
			NBTTagCompound compound = NBTHelper.loadPayload(stream, new NBTReadLimiter(2097152L));
			ItemStack item = ItemStack.createStack(compound);
			bag.addLast(item);
		}
	}

	@Override
	public void serialize(DataOutputStream stream) throws IOException {
		stream.writeInt(bag.size());
		Iterator<ItemStack> iterator = bag.iterator();
		while (iterator.hasNext()) {
			NBTTagCompound compound = new NBTTagCompound();
			iterator.next().save(compound);
			NBTHelper.writePayload(stream, compound);
		}

	}
}

package net.dashmc.plots.plot.data;

import java.util.LinkedList;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.BlockBagUpdatedEvent;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.events.PreBlockBagDepositEvent;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Item;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.PlayerInventory;

/**
 * Items an environment's owner has gotten from it, such as broken blocks. Part
 * of the environment, and saved with it. Items go to the owner's inventory
 * when there's room.
 */
@Getter
@RequiredArgsConstructor
public class BlockBag {
	private final PlayerInventory inventory;
	private @Getter(AccessLevel.NONE) final EventDispatcher events;
	private @Getter(AccessLevel.NONE) final Debug debug;
	private LinkedList<ItemStack> bag = new LinkedList<>();

	/**
	 * @return the owner, whose inventory the items go to
	 */
	public EntityPlayer getPlayer() {
		return (EntityPlayer) inventory.player;
	}

	public int size() {
		return bag.size();
	}

	public void emptyIfPossible() {
		debug.log(() -> "size of bag: " + bag.size());

		while (bag.size() > 0) {
			ItemStack item = bag.peekFirst();
			boolean hasSpace = inventory.pickup(item);

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
		events.call(pre);
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
			events.call(preUpdateEvent);
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

		if (tryAddToPlayerInventory && inventory.pickup(item))
			return false;

		bag.addLast(item);
		if (callEvents)
			callUpdateEvent(true);

		return true;
	}

	private BlockBagUpdatedEvent callUpdateEvent(boolean isDeposit) {
		BlockBagUpdatedEvent event = new BlockBagUpdatedEvent(isDeposit, this);
		events.call(event);
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

	/**
	 * Puts the item at the end of the bag without calling any events, for the
	 * items of a save file.
	 */
	public void restore(ItemStack item) {
		bag.addLast(item);
	}
}

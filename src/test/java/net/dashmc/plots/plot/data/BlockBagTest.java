package net.dashmc.plots.plot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.Event;
import org.junit.BeforeClass;
import org.junit.Test;

import net.dashmc.plots.events.BlockBagUpdatedEvent;
import net.dashmc.plots.events.PreBlockBagDepositEvent;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.PlayerInventory;

public class BlockBagTest {
	private final List<Event> calledEvents = new ArrayList<>();
	// an inventory without a player behind it
	private final PlayerInventory inventory = new PlayerInventory(null);
	private final BlockBag bag = new BlockBag(inventory, calledEvents::add);

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	private static BlockBag saveAndLoad(BlockBag bag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bag.serialize(new DataOutputStream(bytes));

		BlockBag loaded = new BlockBag(new PlayerInventory(null), event -> {
		});
		loaded.deserialize(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		return loaded;
	}

	private int countInInventory(ItemStack item) {
		int count = 0;
		for (ItemStack stack : inventory.items)
			if (stack != null && stack.getItem() == item.getItem() && stack.getData() == item.getData())
				count += stack.count;
		return count;
	}

	@Test
	public void itemsSurviveASaveAndLoad() throws IOException {
		ItemStack named = new ItemStack(Items.DIAMOND, 5);
		named.c("Named");
		ItemStack redWool = new ItemStack(Blocks.WOOL, 3, 14);

		bag.addToBag(named);
		bag.addToBag(redWool);

		BlockBag loaded = saveAndLoad(bag);

		assertEquals(2, loaded.size());
		assertTrue(ItemStack.matches(named, loaded.getBag().get(0)));
		assertTrue(ItemStack.matches(redWool, loaded.getBag().get(1)));
	}

	@Test
	public void anEmptyBagSurvivesASaveAndLoad() throws IOException {
		assertEquals(0, saveAndLoad(bag).size());
	}

	@Test
	public void addGivesTheItemToTheInventoryWhenItFits() {
		bag.add(new ItemStack(Items.DIAMOND, 5));

		assertEquals(0, bag.size());
		assertEquals(5, countInInventory(new ItemStack(Items.DIAMOND)));
		assertEquals(1, calledEvents.size());
		assertTrue(calledEvents.get(0) instanceof PreBlockBagDepositEvent);
	}

	@Test
	public void addToBagKeepsTheItemInTheBag() {
		bag.addToBag(new ItemStack(Items.DIAMOND, 5));

		assertEquals(1, bag.size());
		assertEquals(0, countInInventory(new ItemStack(Items.DIAMOND)));
		assertTrue(calledEvents.get(0) instanceof PreBlockBagDepositEvent);
		assertTrue(((BlockBagUpdatedEvent) calledEvents.get(1)).isDeposit());
	}

	@Test
	public void aCancelledDepositIsntAdded() {
		BlockBag cancelling = new BlockBag(inventory, event -> {
			if (event instanceof PreBlockBagDepositEvent)
				((PreBlockBagDepositEvent) event).setCancelled(true);
		});

		cancelling.addToBag(new ItemStack(Items.DIAMOND));
		cancelling.add(new ItemStack(Items.EMERALD));

		assertEquals(0, cancelling.size());
		assertEquals(0, countInInventory(new ItemStack(Items.EMERALD)));
	}

	@Test
	public void emptyIfPossibleMovesTheBagToTheInventory() {
		bag.addToBag(new ItemStack(Items.DIAMOND, 5));

		bag.emptyIfPossible();

		assertEquals(0, bag.size());
		assertEquals(5, countInInventory(new ItemStack(Items.DIAMOND)));
	}
}

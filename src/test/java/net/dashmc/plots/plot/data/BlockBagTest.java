package net.dashmc.plots.plot.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;

public class BlockBagTest {

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	private static BlockBag saveAndLoad(BlockBag bag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bag.serialize(new DataOutputStream(bytes));

		// the player is only needed to hand the items out
		BlockBag loaded = new BlockBag(null);
		loaded.deserialize(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		return loaded;
	}

	@Test
	public void itemsSurviveASaveAndLoad() throws IOException {
		ItemStack named = new ItemStack(Items.DIAMOND, 5);
		named.c("Named");
		ItemStack redWool = new ItemStack(Blocks.WOOL, 3, 14);

		// added to the list directly, as the add methods call Bukkit events
		BlockBag bag = new BlockBag(null);
		bag.getBag().add(named);
		bag.getBag().add(redWool);

		BlockBag loaded = saveAndLoad(bag);

		assertEquals(2, loaded.size());
		assertTrue(ItemStack.matches(named, loaded.getBag().get(0)));
		assertTrue(ItemStack.matches(redWool, loaded.getBag().get(1)));
	}

	@Test
	public void anEmptyBagSurvivesASaveAndLoad() throws IOException {
		assertEquals(0, saveAndLoad(new BlockBag(null)).size());
	}
}

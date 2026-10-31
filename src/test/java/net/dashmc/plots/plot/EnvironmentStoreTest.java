package net.dashmc.plots.plot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;

import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.Items;
import net.minecraft.server.v1_8_R3.PlayerInventory;

// loading a whole environment needs a server, for its player and world
public class EnvironmentStoreTest {

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
	}

	private static BlockBag emptyBag() {
		return new BlockBag(new PlayerInventory(null), event -> {
		}, Debug.OFF);
	}

	private static BlockBag saveAndLoad(BlockBag bag) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		EnvironmentStore.writeBlockBag(bag, new DataOutputStream(bytes));

		BlockBag loaded = emptyBag();
		EnvironmentStore.readBlockBag(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), loaded);
		return loaded;
	}

	@Test
	public void theBlockBagsItemsSurviveASaveAndLoad() throws IOException {
		BlockBag bag = emptyBag();
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
	public void anEmptyBlockBagSurvivesASaveAndLoad() throws IOException {
		assertEquals(0, saveAndLoad(emptyBag()).size());
	}
}

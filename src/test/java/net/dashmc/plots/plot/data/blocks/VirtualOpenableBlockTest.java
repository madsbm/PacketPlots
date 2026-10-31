package net.dashmc.plots.plot.data.blocks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import net.dashmc.plots.plot.FakeEnvironment;
import net.dashmc.plots.plot.data.BuiltinHandlers;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.BlockDoor;
import net.minecraft.server.v1_8_R3.BlockFenceGate;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockTrapdoor;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.DispenserRegistry;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldEvent;

public class VirtualOpenableBlockTest {
	private static final VirtualBlockRegistry blockHandlers = new VirtualBlockRegistry();
	private static final FieldAccess<Integer> eventIdField = new FieldAccess<>(PacketPlayOutWorldEvent.class, "a",
			int.class);

	private static final int OPEN_SOUND = 1003;
	private static final int CLOSE_SOUND = 1006;

	private final FakeEnvironment env = new FakeEnvironment(blockHandlers);
	private final BlockPosition pos = new BlockPosition(0, 64, 0);

	@BeforeClass
	public static void bootstrap() {
		// registers the blocks and items, like the server does on startup
		DispenserRegistry.c();
		BuiltinHandlers.registerBlocks(blockHandlers);
	}

	/**
	 * Uses the block at the position, without a player, which only a fence gate
	 * being opened needs.
	 */
	private boolean use(BlockPosition at) {
		return blockHandlers.interact(env, at, env.getType(at), null, EnumDirection.UP, 0, 0, 0);
	}

	/**
	 * @return the id of the only world event, e.g. a sound, the environment
	 *         broadcast
	 */
	private int onlyWorldEvent() {
		assertEquals(1, env.getBroadcastPackets().size());
		return eventIdField.get(env.getBroadcastPackets().get(0));
	}

	private void placeDoor(boolean open) {
		IBlockData door = Blocks.WOODEN_DOOR.getBlockData().set(BlockDoor.OPEN, open);
		env.setBlock(pos, door.set(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER), 3);
		env.setBlock(pos.up(), door.set(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), 3);
	}

	// the door used to play its sound from before it was opened or closed
	@Test
	public void aDoorOpensWithTheOpenSound() {
		placeDoor(false);

		assertTrue(use(pos));

		assertTrue(env.getType(pos).get(BlockDoor.OPEN));
		assertEquals(OPEN_SOUND, onlyWorldEvent());
	}

	@Test
	public void aDoorClosesWithTheCloseSound() {
		placeDoor(true);

		use(pos);

		assertFalse(env.getType(pos).get(BlockDoor.OPEN));
		assertEquals(CLOSE_SOUND, onlyWorldEvent());
	}

	// the lower half keeps whether the door is open
	@Test
	public void usingTheUpperHalfOpensTheLowerHalf() {
		placeDoor(false);

		use(pos.up());

		assertTrue(env.getType(pos).get(BlockDoor.OPEN));
		assertEquals(OPEN_SOUND, onlyWorldEvent());
	}

	@Test
	public void aTrapdoorOpensWithTheOpenSound() {
		env.setBlock(pos, Blocks.TRAPDOOR.getBlockData().set(BlockTrapdoor.OPEN, false), 3);

		use(pos);

		assertTrue(env.getType(pos).get(BlockTrapdoor.OPEN));
		assertEquals(OPEN_SOUND, onlyWorldEvent());
	}

	// the trapdoor's copy of the toggle let them be opened by hand
	@Test
	public void ironDoorsAndTrapdoorsDontOpenByHand() {
		IBlockData ironTrapdoor = Blocks.IRON_TRAPDOOR.getBlockData().set(BlockTrapdoor.OPEN, false);
		env.setBlock(pos, ironTrapdoor, 3);
		IBlockData ironDoor = Blocks.IRON_DOOR.getBlockData().set(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER);
		env.setBlock(pos.east(), ironDoor, 3);

		assertTrue(use(pos));
		assertTrue(use(pos.east()));

		assertEquals(ironTrapdoor, env.getType(pos));
		assertEquals(ironDoor, env.getType(pos.east()));
		assertTrue(env.getBroadcastPackets().isEmpty());
	}

	@Test
	public void aFenceGateClosesWithTheCloseSound() {
		env.setBlock(pos, Blocks.FENCE_GATE.getBlockData().set(BlockFenceGate.OPEN, true), 3);

		use(pos);

		assertFalse(env.getType(pos).get(BlockFenceGate.OPEN));
		assertEquals(CLOSE_SOUND, onlyWorldEvent());
	}
}

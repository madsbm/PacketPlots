package net.dashmc.plots.utils.helpers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import net.minecraft.server.v1_8_R3.ChunkSection;

public class FieldAccessTest {

	@SuppressWarnings("unused")
	private static class Sample {
		private int number = 7;
		private double decimal = 1.5;
		private boolean flag = true;
		private int[] numbers = { 1, 2 };
		private String text = "text";
		private final Object handle = new Object();
	}

	@Test
	public void getsPrimitiveFields() {
		Sample sample = new Sample();

		assertEquals(7, (int) new FieldAccess<>(Sample.class, "number", int.class).get(sample));
		assertEquals(1.5, new FieldAccess<>(Sample.class, "decimal", double.class).get(sample), 0);
		assertEquals(true, new FieldAccess<>(Sample.class, "flag", boolean.class).get(sample));
	}

	@Test
	public void setsPrimitiveFields() {
		Sample sample = new Sample();
		FieldAccess<Integer> number = new FieldAccess<>(Sample.class, "number", int.class);

		number.set(sample, 9);

		assertEquals(9, (int) number.get(sample));
	}

	@Test
	public void acceptsTheBoxedTypeOfAPrimitiveField() {
		assertEquals(7, (int) new FieldAccess<>(Sample.class, "number", Integer.class).get(new Sample()));
	}

	@Test
	public void getsArrayAndObjectFields() {
		Sample sample = new Sample();

		assertArrayEquals(new int[] { 1, 2 }, new FieldAccess<>(Sample.class, "numbers", int[].class).get(sample));
		assertEquals("text", new FieldAccess<>(Sample.class, "text", String.class).get(sample));
	}

	// VirtualPlayerInteractManager replaces the final EntityPlayer#playerInteractManager
	@Test
	public void setsFinalInstanceFields() {
		Sample sample = new Sample();
		Object replacement = new Object();

		new FieldAccess<>(Sample.class, "handle", Object.class).set(sample, replacement);

		assertSame(replacement, new FieldAccess<>(Sample.class, "handle", Object.class).get(sample));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsTheWrongType() {
		new FieldAccess<>(Sample.class, "text", int.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsAMissingField() {
		new FieldAccess<>(Sample.class, "missing", int.class);
	}

	private static class SampleSubclass extends Sample {
		private String own = "own";
	}

	@Test
	public void copyFieldsCopiesTheFieldsOfTheClassAndItsSuperclasses() {
		Sample source = new SampleSubclass();
		new FieldAccess<>(Sample.class, "number", int.class).set(source, 9);
		new FieldAccess<>(Sample.class, "text", String.class).set(source, "copied");
		Sample target = new Sample();

		FieldAccess.copyFields(Sample.class, source, target);

		assertEquals(9, (int) new FieldAccess<>(Sample.class, "number", int.class).get(target));
		assertEquals("copied", new FieldAccess<>(Sample.class, "text", String.class).get(target));
		assertSame(new FieldAccess<>(Sample.class, "handle", Object.class).get(source),
				new FieldAccess<>(Sample.class, "handle", Object.class).get(target));
	}

	// e.g. a VirtualPlayerInteractManager left behind by a reload
	@Test
	public void copyFieldsLeavesOutTheFieldsOfSubclasses() {
		SampleSubclass source = new SampleSubclass();
		source.own = "source";
		SampleSubclass target = new SampleSubclass();

		FieldAccess.copyFields(Sample.class, source, target);

		assertEquals("own", target.own);
	}

	// what VirtualChunk does when it copies a real chunk section
	@Test
	public void readsTheBlockCountOfAChunkSection() {
		FieldAccess<Integer> nonEmptyBlockCount = new FieldAccess<>(ChunkSection.class, "nonEmptyBlockCount",
				int.class);

		assertEquals(0, (int) nonEmptyBlockCount.get(new ChunkSection(0, true)));
	}
}

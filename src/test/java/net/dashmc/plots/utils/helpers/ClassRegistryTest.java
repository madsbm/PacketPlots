package net.dashmc.plots.utils.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ClassRegistryTest {
	private final ClassRegistry<String> registry = new ClassRegistry<>();

	@Test
	public void findsTheHandlerOfTheClass() {
		registry.register(Number.class, "number");

		assertEquals("number", registry.get(Number.class));
	}

	@Test
	public void aSubclassGetsTheHandlerOfItsClosestSuperclassWithOne() {
		registry.register(Number.class, "number");

		assertEquals("number", registry.get(Integer.class));
	}

	@Test
	public void aSubclassWithAHandlerOfItsOwnGetsThatOne() {
		registry.register(Number.class, "number");
		registry.register(Integer.class, "integer");

		assertEquals("integer", registry.get(Integer.class));
		assertEquals("number", registry.get(Long.class));
	}

	@Test
	public void aClassWithoutAHandlerGetsNull() {
		registry.register(Number.class, "number");

		assertNull(registry.get(String.class));
	}
}

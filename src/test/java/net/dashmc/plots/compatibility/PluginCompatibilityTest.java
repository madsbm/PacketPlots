package net.dashmc.plots.compatibility;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.utils.misc.Debug;

public class PluginCompatibilityTest {
	private final PluginCompatibility compatibility = new PluginCompatibility(Debug.OFF);

	@RequiredArgsConstructor
	private static class FakeCompatibility implements CompatibilityLoader {
		private final boolean installed;
		private int activations;

		@Override
		public boolean shouldActivate() {
			return installed;
		}

		@Override
		public void activate() {
			activations++;
		}
	}

	private static class OtherCompatibility extends FakeCompatibility {
		OtherCompatibility() {
			super(true);
		}
	}

	@Test
	public void onlyTheCompatibilitiesOfInstalledPluginsAreActivated() {
		FakeCompatibility installed = new FakeCompatibility(true);
		FakeCompatibility missing = new FakeCompatibility(false);
		compatibility.register(installed);
		compatibility.register(missing);

		compatibility.load();

		assertEquals(1, installed.activations);
		assertEquals(0, missing.activations);
		assertEquals(1, compatibility.getActive().size());
		assertTrue(compatibility.getActive().contains(installed));
	}

	// e.g. when another plugin registers a compatibility of its own and loads it
	@Test
	public void loadingAgainOnlyActivatesTheNewOnes() {
		FakeCompatibility first = new FakeCompatibility(true);
		compatibility.register(first);
		compatibility.load();

		FakeCompatibility second = new FakeCompatibility(true);
		compatibility.register(second);
		compatibility.load();

		assertEquals(1, first.activations);
		assertEquals(1, second.activations);
	}

	@Test
	public void isActiveGoesByTheClassOfTheCompatibility() {
		compatibility.register(new FakeCompatibility(true));
		compatibility.load();

		assertTrue(compatibility.isActive(FakeCompatibility.class));
		assertFalse(compatibility.isActive(OtherCompatibility.class));
	}
}

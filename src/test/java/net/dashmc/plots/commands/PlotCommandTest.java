package net.dashmc.plots.commands;

import static org.junit.Assert.assertSame;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import eu.okaeri.injector.OkaeriInjector;
import net.dashmc.plots.plot.EnvironmentManager;
import net.dashmc.plots.utils.helpers.FieldAccess;

public class PlotCommandTest {
	@Rule
	public TemporaryFolder dataDirectory = new TemporaryFolder();

	// okaeri creates the command like this when PacketPlots registers it
	@Test
	public void getsTheEnvironmentManagerFromTheInjector() {
		EnvironmentManager environments = new EnvironmentManager(dataDirectory.getRoot(), null, "test", event -> {
		});
		OkaeriInjector injector = OkaeriInjector.create(true);
		injector.registerInjectable("environments", environments);

		PlotCommand command = injector.createInstance(PlotCommand.class);

		assertSame(environments,
				new FieldAccess<>(PlotCommand.class, "environments", EnvironmentManager.class).get(command));
	}
}

package net.dashmc.plots;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import eu.okaeri.commands.Commands;
import eu.okaeri.commands.brigadier.CommandsBrigadierPaper;
import eu.okaeri.commands.bukkit.CommandsBukkit;
import eu.okaeri.commands.injector.CommandsInjector;
import eu.okaeri.commands.validator.CommandsValidator;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import eu.okaeri.configs.yaml.bukkit.serdes.SerdesBukkit;
import eu.okaeri.injector.OkaeriInjector;
import eu.okaeri.validator.OkaeriValidator;
import eu.okaeri.validator.policy.NullPolicy;
import lombok.Getter;
import net.dashmc.plots.commands.PlotCommand;
import net.dashmc.plots.compatibility.PluginCompatibility;
import net.dashmc.plots.config.PlotConfig;
import net.dashmc.plots.config.serializers.BlockPositionSerializer;
import net.dashmc.plots.config.serializers.CuboidRegionSerializer;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.listeners.ConnectionListener;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.EnvironmentManager;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;

public class PacketPlots extends JavaPlugin {

	private static final boolean BRIGADIER = Boolean
			.parseBoolean(System.getProperty("okaeri.platform.brigadier", "true"));

	// for other plugins: the plugin itself passes these to the classes that need them
	private @Getter static boolean errored;
	private @Getter static Commands commands;
	private @Getter static PacketPlots instance;
	private @Getter static PlotConfig plotConfig;
	private @Getter static EnvironmentManager environmentManager;

	@Override
	public void onEnable() {
		errored = true;
		instance = this;

		plotConfig = ConfigManager.create(PlotConfig.class, (conf) -> {
			conf.withConfigurer(new YamlBukkitConfigurer(), new SerdesBukkit());
			conf.withSerdesPack(reg -> {
				reg.register(new BlockPositionSerializer());
				reg.register(new CuboidRegionSerializer());
			});
			conf.withBindFile(new File(this.getDataFolder(), "config.yml"));
			conf.saveDefaults();
			conf.load(true);
		});

		plotConfig.validate();
		Debug.setEnabled(plotConfig.isDebug());

		Debug.log(() -> "Found config file... virtualizing region " + plotConfig.getRegion() + " with chunks "
				+ Arrays.toString(plotConfig.getRegion().getChunks()));

		EventDispatcher events = getServer().getPluginManager()::callEvent;
		Executor mainThread = task -> getServer().getScheduler().runTask(this, task);
		CuboidRegion acDisabledRegion = plotConfig.getDefaultedACRegion();

		environmentManager = new EnvironmentManager(new File(this.getDataFolder(), "data"), plotConfig,
				getDescription().getVersion(), events);

		ConnectionListener connectionListener = ConnectionListener.register(this, environmentManager);
		PacketInterceptor.register(acDisabledRegion, mainThread);

		commands = CommandsBukkit.of(this);

		// injects the registered commands
		OkaeriInjector injector = OkaeriInjector.create(true);
		commands.registerExtension(new CommandsInjector(injector));

		// validates args
		commands.registerExtension(new CommandsValidator(OkaeriValidator.of(NullPolicy.NULLABLE)));

		// tab completion of commands
		if (BRIGADIER && canUseBrigadierPaper()) {
			commands.registerExtension(new CommandsBrigadierPaper(this));
		}

		injector.registerInjectable("commands", commands);
		injector.registerInjectable("environments", environmentManager);

		commands.registerCommand(PlotCommand.class);

		PluginCompatibility.load(this, acDisabledRegion);
		Debug.log(() -> "Loaded compatibilities: "
				+ Arrays.toString(PluginCompatibility.getCompatibilities().toArray()));

		// players don't join again after a reload, so load the ones already online
		for (Player player : getServer().getOnlinePlayers()) {
			connectionListener.join(player);
		}

		errored = false;
	}

	protected boolean canUseBrigadierPaper() {
		try {
			Class.forName("com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent");
			return true;
		} catch (ClassNotFoundException ignored) {
			return false;
		}
	}

	@Override
	public void onDisable() {
		if (isErrored())
			return;

		try {
			commands.close();
		} catch (IOException e) {
			getLogger().log(Level.WARNING, "Failed to close the commands", e);
		}

		// also removes everything the plugin added to players, so a reload starts clean
		environmentManager.unloadAll();
	}

	@Override
	public FileConfiguration getConfig() {
		throw new UnsupportedOperationException();
	}

}
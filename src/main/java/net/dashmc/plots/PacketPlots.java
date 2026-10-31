package net.dashmc.plots;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.stream.Collectors;

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
import net.dashmc.plots.compatibility.compatibilities.ChunkMapSendCompatibility;
import net.dashmc.plots.compatibility.compatibilities.HDBCompatibility;
import net.dashmc.plots.compatibility.compatibilities.NCPCompatibility;
import net.dashmc.plots.compatibility.compatibilities.VulcanCompatibility;
import net.dashmc.plots.config.PlotConfig;
import net.dashmc.plots.config.serializers.BlockPositionSerializer;
import net.dashmc.plots.config.serializers.CuboidRegionSerializer;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.listeners.ConnectionListener;
import net.dashmc.plots.packets.InterceptorRegistry;
import net.dashmc.plots.packets.PacketInjector;
import net.dashmc.plots.packets.interceptors.BlockDigPacketInterceptor;
import net.dashmc.plots.packets.interceptors.BlockPlacementPacketInterceptor;
import net.dashmc.plots.packets.interceptors.ChunkPacketRenderer;
import net.dashmc.plots.packets.interceptors.MapChunkBulkPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MapChunkPacketInterceptor;
import net.dashmc.plots.packets.interceptors.MovementPacketInterceptor;
import net.dashmc.plots.packets.interceptors.WindowClosePacketInterceptor;
import net.dashmc.plots.plot.ConnectionFactory;
import net.dashmc.plots.plot.EnvironmentContext;
import net.dashmc.plots.plot.EnvironmentManager;
import net.dashmc.plots.plot.EnvironmentStore;
import net.dashmc.plots.plot.data.BuiltinHandlers;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.plot.data.VirtualItemRegistry;
import net.dashmc.plots.plot.interaction.ChestCloser;
import net.dashmc.plots.plot.interaction.InteractionHandler;
import net.dashmc.plots.plot.interaction.PlacementRules;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockDig;
import net.minecraft.server.v1_8_R3.PacketPlayInBlockPlace;
import net.minecraft.server.v1_8_R3.PacketPlayInCloseWindow;
import net.minecraft.server.v1_8_R3.PacketPlayInFlying;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunkBulk;

public class PacketPlots extends JavaPlugin {

	private static final boolean BRIGADIER = Boolean
			.parseBoolean(System.getProperty("okaeri.platform.brigadier", "true"));

	// for other plugins: the plugin itself passes these to the classes that need them
	private @Getter static boolean errored;
	private @Getter static Commands commands;
	private @Getter static PacketPlots instance;
	private @Getter static PlotConfig plotConfig;
	private @Getter static EnvironmentManager environmentManager;
	// other plugins may register and load compatibilities of their own
	private @Getter static PluginCompatibility compatibility;

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
		Debug debug = new Debug(getLogger(), plotConfig.isDebug());

		debug.log(() -> "Found config file... virtualizing region " + plotConfig.getRegion() + " with chunks "
				+ Arrays.toString(plotConfig.getRegion().getChunks()));

		EventDispatcher events = getServer().getPluginManager()::callEvent;
		Executor mainThread = task -> getServer().getScheduler().runTask(this, task);
		// the server's own queue of packets to handle on the main thread, so the
		// intercepted ones stay in order with the rest
		Executor packetQueue = MinecraftServer.getServer()::postToMainThread;
		CuboidRegion acDisabledRegion = plotConfig.getDefaultedACRegion();

		compatibility = loadCompatibility(acDisabledRegion, debug);

		VirtualBlockRegistry blockHandlers = new VirtualBlockRegistry();
		BuiltinHandlers.registerBlocks(blockHandlers, debug);
		VirtualItemRegistry itemHandlers = new VirtualItemRegistry();
		BuiltinHandlers.registerItems(itemHandlers, blockHandlers, new PlacementRules(blockHandlers, events, debug),
				debug);

		InteractionHandler interactions = new InteractionHandler(itemHandlers, blockHandlers, events, debug);
		ChestCloser chests = new ChestCloser(getLogger());
		InterceptorRegistry interceptors = registerInterceptors(interactions, chests, mainThread, acDisabledRegion,
				events, debug);

		EnvironmentContext context = new EnvironmentContext(events, blockHandlers, debug);
		EnvironmentStore store = new EnvironmentStore(new File(this.getDataFolder(), "data"),
				getDescription().getVersion(), getServer()::getWorld, context, getLogger());
		PacketInjector packets = new PacketInjector(interceptors, packetQueue, chests, getLogger());
		ConnectionFactory connectionFactory = new ConnectionFactory(packets, plotConfig.getSafeLocation(),
				compatibility.isActive(ChunkMapSendCompatibility.class), events, debug);
		environmentManager = new EnvironmentManager(store, plotConfig, context, connectionFactory);

		ConnectionListener connectionListener = ConnectionListener.register(this, environmentManager);
		registerCommands();

		// players don't join again after a reload, so load the ones already online
		for (Player player : getServer().getOnlinePlayers()) {
			connectionListener.join(player);
		}

		errored = false;
	}

	/**
	 * Activates the compatibilities with the installed plugins.
	 *
	 * @param acDisabledRegion where anticheats are disabled
	 */
	private PluginCompatibility loadCompatibility(CuboidRegion acDisabledRegion, Debug debug) {
		PluginCompatibility compat = new PluginCompatibility(debug);
		compat.register(new VulcanCompatibility(this, acDisabledRegion, getLogger(), debug));
		compat.register(new ChunkMapSendCompatibility(this));
		compat.register(new HDBCompatibility(this, getLogger()));
		compat.register(new NCPCompatibility(this, acDisabledRegion));
		compat.load();

		debug.log(() -> "Loaded compatibilities: " + compat.getActive().stream()
				.map(active -> active.getClass().getSimpleName()).collect(Collectors.toList()));
		return compat;
	}

	/**
	 * @param mainThread       runs tasks on the server's main thread
	 * @param acDisabledRegion where anticheats are disabled
	 */
	private InterceptorRegistry registerInterceptors(InteractionHandler interactions, ChestCloser chests,
			Executor mainThread, CuboidRegion acDisabledRegion, EventDispatcher events, Debug debug) {
		ChunkPacketRenderer chunkRenderer = new ChunkPacketRenderer(mainThread, getLogger(), debug);

		InterceptorRegistry interceptors = new InterceptorRegistry();
		interceptors.register(PacketPlayOutMapChunkBulk.class, new MapChunkBulkPacketInterceptor(chunkRenderer));
		interceptors.register(PacketPlayOutMapChunk.class, new MapChunkPacketInterceptor(chunkRenderer));
		interceptors.register(PacketPlayInBlockDig.class, new BlockDigPacketInterceptor(interactions));
		interceptors.register(PacketPlayInBlockPlace.class, new BlockPlacementPacketInterceptor(interactions, debug));
		interceptors.register(PacketPlayInCloseWindow.class, new WindowClosePacketInterceptor(chests));
		// also gets the look, position and position-look packets, which subclass
		// PacketPlayInFlying
		interceptors.register(PacketPlayInFlying.class, new MovementPacketInterceptor(acDisabledRegion, events));
		return interceptors;
	}

	private void registerCommands() {
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

}

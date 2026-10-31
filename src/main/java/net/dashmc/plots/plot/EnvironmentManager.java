package net.dashmc.plots.plot;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.dashmc.plots.config.PlotConfig;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;

/**
 * Keeps track of the environments of online players, which it loads and saves
 * with the EnvironmentStore, and turns virtualization on and off for them,
 * keeping track of their connections.
 */
public class EnvironmentManager {
	private final EnvironmentStore store;
	private final PlotConfig config;
	private final EnvironmentContext context;
	private final ConnectionFactory connectionFactory;
	private final HashMap<UUID, CuboidEnvironment> environments = new HashMap<>();
	private final HashMap<UUID, VirtualConnection> connections = new HashMap<>();

	/**
	 * @param config  the world and region of new environments
	 * @param context what new environments are given
	 */
	public EnvironmentManager(EnvironmentStore store, PlotConfig config, EnvironmentContext context,
			ConnectionFactory connectionFactory) {
		this.store = store;
		this.config = config;
		this.context = context;
		this.connectionFactory = connectionFactory;
	}

	public IEnvironment get(Player player) {
		return environments.get(player.getUniqueId());
	}

	public IEnvironment get(EntityPlayer player) {
		return environments.get(player.getUniqueID());
	}

	public Collection<IEnvironment> getActive() {
		return Collections.unmodifiableCollection(environments.values());
	}

	/**
	 * @return the player's connection, or null if virtualization is off for them
	 */
	public VirtualConnection getConnection(Player player) {
		return connections.get(player.getUniqueId());
	}

	/**
	 * Loads the player's environment from their save file, or creates one if they
	 * have none, and starts virtualizing it for them.
	 */
	public IEnvironment load(Player player) throws IOException {
		UUID uuid = player.getUniqueId();
		if (!player.isOnline())
			throw new IOException("Tried loading the environment of offline player: " + uuid);

		CuboidEnvironment environment = store.load(player);
		if (environment == null) {
			context.getDebug().log(() -> "No save file could be found for " + player.getName());

			environment = CuboidEnvironment.create(player, config.getWorld(), config.getRegion(), context);
			store.save(environment);
		}

		environments.put(uuid, environment);
		startVirtualization(player);
		return environment;
	}

	/**
	 * Stops virtualization for the player, then saves and closes their
	 * environment, sending its visitors back to their own.
	 */
	public void unload(Player player) {
		VirtualConnection connection = connections.remove(player.getUniqueId());
		if (connection != null)
			connection.close();

		CuboidEnvironment environment = environments.remove(player.getUniqueId());
		if (environment != null) {
			store.save(environment);
			environment.close();
		}
	}

	/**
	 * Unloads every environment, for when the plugin is disabled. Every player is
	 * sent their real chunks, and keeps no trace of the plugin: closing their
	 * connections takes out their Netty handlers and
	 * VirtualPlayerInteractManagers.
	 */
	public void unloadAll() {
		// connections are closed first, so closing an environment doesn't send its
		// visitors back to their own
		for (VirtualConnection connection : connections.values()) {
			IEnvironment shown = connection.getEnvironment();
			connection.close();
			sendRealChunks(connection.getPlayer(), shown);
		}
		connections.clear();

		for (CuboidEnvironment environment : environments.values()) {
			store.save(environment);
			environment.close();
		}
		environments.clear();
	}

	public void saveAll() {
		for (CuboidEnvironment environment : environments.values()) {
			store.save(environment);
		}
	}

	public void startVirtualization(Player player) {
		if (connections.containsKey(player.getUniqueId()))
			return;

		IEnvironment environment = get(player);
		if (environment == null)
			return;

		VirtualConnection connection = connectionFactory.establish(((CraftPlayer) player).getHandle(), environment);
		connections.put(player.getUniqueId(), connection);
		environment.addViewer(connection);

		connection.refreshVirtualizedChunks();
	}

	/**
	 * Stops virtualization for the player, and sends them the real chunks in place
	 * of their virtual ones.
	 */
	public void stopVirtualization(Player player) {
		EntityPlayer handle = ((CraftPlayer) player).getHandle();
		VirtualConnection connection = connections.get(player.getUniqueId());
		IEnvironment environment = get(player);
		if (connection == null || environment == null)
			return;

		connections.remove(player.getUniqueId());
		connection.close();

		sendRealChunks(handle, environment);
	}

	private static void sendRealChunks(EntityPlayer player, IEnvironment environment) {
		for (VirtualChunk chunk : environment.getChunks()) {
			player.playerConnection.sendPacket(new PacketPlayOutMapChunk(chunk.getChunk(), false, 65535));
		}
	}
}

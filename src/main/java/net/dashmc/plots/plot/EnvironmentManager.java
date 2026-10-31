package net.dashmc.plots.plot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.dashmc.plots.config.PlotConfig;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;

/**
 * Loads, saves and keeps track of the environments of online players, and
 * turns virtualization on and off for them, keeping track of their connections.
 *
 * Save file structure:
 * version length (4 bytes - int)
 * version (2 bytes per char)
 * the environment's own data, see CuboidEnvironment
 */
public class EnvironmentManager {
	private final File dataDirectory;
	private final PlotConfig config;
	private final String pluginVersion;
	private final EventDispatcher events;
	private final HashMap<UUID, CuboidEnvironment> environments = new HashMap<>();
	private final HashMap<UUID, VirtualConnection> connections = new HashMap<>();

	public EnvironmentManager(File dataDirectory, PlotConfig config, String pluginVersion, EventDispatcher events) {
		this.dataDirectory = dataDirectory;
		this.config = config;
		this.pluginVersion = pluginVersion;
		this.events = events;

		dataDirectory.mkdirs();
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

		File dataFile = getDataFile(uuid);
		CuboidEnvironment environment;

		if (dataFile.exists()) {
			try (DataInputStream dataInputStream = new DataInputStream(
					new BufferedInputStream(new FileInputStream(dataFile)))) {
				readVersionCode(dataInputStream);
				environment = new CuboidEnvironment(player, dataInputStream, events);
			}

			if (!uuid.equals(environment.getOwnerUuid()))
				throw new IOException("Mismatched UUIDs: (" + uuid + " => " + environment.getOwnerUuid()
						+ "). File might be corrupt");
		} else {
			Debug.log(() -> "No save file could be found for " + player.getName());

			environment = new CuboidEnvironment(player, config.getWorld(), config.getRegion(), events);
			save(environment);
		}

		VirtualPlayerInteractManager.inject(player, this);
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
			save(environment);
			environment.close();
		}

		VirtualPlayerInteractManager.uninject(player);
	}

	/**
	 * Unloads every environment, for when the plugin is disabled. Every player is
	 * sent their real chunks, and keeps no trace of the plugin: no Netty handler
	 * and no VirtualPlayerInteractManager.
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
			save(environment);
			environment.close();
		}
		environments.clear();

		for (Player player : Bukkit.getOnlinePlayers()) {
			VirtualPlayerInteractManager.uninject(player);
		}
	}

	public void saveAll() {
		for (CuboidEnvironment environment : environments.values()) {
			save(environment);
		}
	}

	public void startVirtualization(Player player) {
		if (connections.containsKey(player.getUniqueId()))
			return;

		IEnvironment environment = get(player);
		if (environment == null)
			return;

		VirtualConnection connection = VirtualConnection.establish(((CraftPlayer) player).getHandle(), environment,
				config.getSafeLocation());
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

	private void save(CuboidEnvironment environment) {
		UUID ownerUuid = environment.getOwnerUuid();
		File dataFile = getDataFile(ownerUuid);
		File tempFile = new File(dataDirectory, ownerUuid + ".dat.tmp");

		// write to a temporary file first, so a failed save never replaces the previous
		// one
		try (DataOutputStream dataOutputStream = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(tempFile)))) {
			dataOutputStream.writeInt(pluginVersion.length());
			for (char versionCodeChar : pluginVersion.toCharArray())
				dataOutputStream.writeChar(versionCodeChar);

			environment.serialize(dataOutputStream);
		} catch (IOException e) {
			Bukkit.getLogger().log(Level.SEVERE, "Failed to save the PacketPlot of " + ownerUuid, e);
			tempFile.delete();
			return;
		}

		try {
			Files.move(tempFile.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Bukkit.getLogger().log(Level.SEVERE, "Failed to replace the save file of " + ownerUuid, e);
		}
	}

	private String readVersionCode(DataInputStream stream) throws IOException {
		StringBuilder versionCode = new StringBuilder();
		int versionLength = stream.readInt();
		for (int i = 0; i < versionLength; i++)
			versionCode.append(stream.readChar());

		String generatedWith = versionCode.toString();
		if (!generatedWith.equalsIgnoreCase(pluginVersion)) {
			// TODO: apply some sort of migration or different loading style

			Bukkit.getLogger().warning("[PacketPlots] Save file differs in versions! Trying to load anyway... ("
					+ generatedWith + " -> " + pluginVersion + ")");
		}

		return generatedWith;
	}

	private File getDataFile(UUID ownerUuid) {
		return new File(dataDirectory, ownerUuid + ".dat");
	}
}

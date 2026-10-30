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
import net.dashmc.plots.plot.data.BlockBag;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;

/**
 * Loads, saves and keeps track of the environments of online players, and
 * turns virtualization on and off for them.
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
	private final HashMap<UUID, CuboidEnvironment> environments = new HashMap<>();

	public EnvironmentManager(File dataDirectory, PlotConfig config, String pluginVersion) {
		this.dataDirectory = dataDirectory;
		this.config = config;
		this.pluginVersion = pluginVersion;

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
				environment = new CuboidEnvironment(dataInputStream);
			}

			if (!uuid.equals(environment.getOwnerUuid()))
				throw new IOException("Mismatched UUIDs: (" + uuid + " => " + environment.getOwnerUuid()
						+ "). File might be corrupt");
		} else {
			Debug.log("No save file could be found for " + player.getName());

			environment = new CuboidEnvironment(uuid, config.getWorld(), config.getRegion());
			save(environment);
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
		VirtualConnection connection = VirtualConnection.get(player);
		if (connection != null)
			connection.close();

		CuboidEnvironment environment = environments.remove(player.getUniqueId());
		if (environment != null) {
			save(environment);
			environment.close();
		}

		// only after saving, as the block bag is saved with the environment
		BlockBag.removeBlockBag(player);
	}

	public void saveAll() {
		for (CuboidEnvironment environment : environments.values()) {
			save(environment);
		}
	}

	public void startVirtualization(Player player) {
		EntityPlayer handle = ((CraftPlayer) player).getHandle();
		if (VirtualConnection.get(handle) != null)
			return;

		IEnvironment environment = get(player);
		if (environment == null)
			return;

		VirtualConnection connection = VirtualConnection.establish(handle, environment);
		environment.addViewer(connection);

		connection.refreshVirtualizedChunks();
	}

	/**
	 * Stops virtualization for the player, and sends them the real chunks in place
	 * of their virtual ones.
	 */
	public void stopVirtualization(Player player) {
		EntityPlayer handle = ((CraftPlayer) player).getHandle();
		VirtualConnection connection = VirtualConnection.get(handle);
		IEnvironment environment = get(player);
		if (connection == null || environment == null)
			return;

		connection.close();

		for (VirtualChunk chunk : environment.getChunks()) {
			handle.playerConnection.sendPacket(new PacketPlayOutMapChunk(chunk.getChunk(), false, 65535));
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

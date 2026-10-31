package net.dashmc.plots.plot;

import org.bukkit.Location;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.dashmc.plots.events.ACRegionEnterExit;
import net.dashmc.plots.events.EnvironmentEnterExit;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.packets.PacketInjector;
import net.dashmc.plots.packets.extensions.VirtualMapChunkPacket;
import net.dashmc.plots.player.VirtualPlayerInteractManager;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Debug;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

/**
 * A player's view of a virtual environment: which environment they're shown,
 * and where they are in relation to it. While it's open, the player has a
 * packet handler and a VirtualPlayerInteractManager. Opened by a
 * ConnectionFactory.
 */
@Getter
public class VirtualConnection {
	private @Setter boolean moved = false;

	VirtualConnection(EntityPlayer player, IEnvironment environment, Location safeLocation,
			PacketInjector packets, boolean forceChunkMapSend, EventDispatcher events, Debug debug) {
		this.player = player;
		this.environment = environment;
		this.original = environment;
		this.safeLocation = safeLocation;
		this.packets = packets;
		this.forceChunkMapSend = forceChunkMapSend;
		this.events = events;
		this.debug = debug;

		open();
	}

	private volatile boolean injected;
	private final EntityPlayer player;
	// read by outgoing interceptors on the Netty thread, changed on the main thread
	private volatile IEnvironment environment;
	private final IEnvironment original;
	// where the player is teleported to when they visit another environment
	private final Location safeLocation;
	private final @Getter(AccessLevel.NONE) PacketInjector packets;
	// renders chunks before sending them, for plugins that read them on the way
	private final @Getter(AccessLevel.NONE) boolean forceChunkMapSend;
	private final @Getter(AccessLevel.NONE) EventDispatcher events;
	private final @Getter(AccessLevel.NONE) Debug debug;
	// keeps track of the player digging blocks in the environment
	private VirtualPlayerInteractManager interactManager;
	private boolean insideEnvironment;
	private boolean insideACRegion;

	public void setInsideACRegion(boolean to) {
		if (to == insideACRegion)
			return;

		insideACRegion = to;
		events.call(new ACRegionEnterExit(this, to));
	}

	public void setInsideEnvironment(boolean to) {
		if (to == insideEnvironment)
			return;

		insideEnvironment = to;
		events.call(new EnvironmentEnterExit(this, to));
	}

	@Setter
	private boolean isVisiting = false;

	/**
	 * Internal method that doesnt touch the environments. this is to prevent a
	 * ConcurrentModificationException when e.g. closing a virtual environment.
	 *
	 * @param other
	 * @return the new environment, if any.
	 */
	protected IEnvironment visitLocally(IEnvironment other) {
		if (other.equals(environment))
			return null;

		setVisiting(!other.equals(original));
		debug.log(() -> "teleporting player to " + safeLocation);
		player.getBukkitEntity().teleport(safeLocation);

		this.environment = other;
		refreshVirtualizedChunks();
		return other;
	}

	public void visit(IEnvironment other) {
		if (other.equals(environment))
			return;

		environment.removeViewer(this);
		other.addViewer(this);
		visitLocally(other);
	}

	public PacketPlayOutMapChunk getRenderPacket(VirtualChunk vChunk) {
		// as the packet will be intercepted anyways, its a waste to spend time
		// calculating the chunkmap just for it to get thrown away. thus, only calculate
		// if needed
		ChunkMap map;
		if (this.injected && !forceChunkMapSend) {
			map = null;
		} else {
			map = environment.getRenderPipeline().render(vChunk);
		}

		return new VirtualMapChunkPacket(vChunk.getCoordPair().x, vChunk.getCoordPair().z, map).getPacket();
	}

	public void refreshVirtualizedChunks() {
		environment.getChunks().forEach((chunk) -> {
			PacketPlayOutMapChunk chunkPacket = getRenderPacket(chunk);
			player.playerConnection.sendPacket(chunkPacket);
		});
	}

	private void open() {
		packets.inject(this);
		this.interactManager = VirtualPlayerInteractManager.inject(this);
		this.injected = true;
	}

	public void close() {
		setInsideACRegion(false);

		packets.remove(player);
		VirtualPlayerInteractManager.uninject(player);

		environment.removeViewer(this);
		this.injected = false;
	}
}

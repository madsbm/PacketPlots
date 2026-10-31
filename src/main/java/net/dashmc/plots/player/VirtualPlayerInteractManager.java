package net.dashmc.plots.player;

import net.dashmc.plots.plot.IVirtualWorld;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;

/**
 * A PlayerInteractManager that animates the player digging blocks in the
 * environment their connection shows them. Injected while the connection is
 * open.
 */
public class VirtualPlayerInteractManager extends PlayerInteractManager {
	// final, but setAccessible is enough to write a final instance field
	private static final FieldAccess<PlayerInteractManager> playerInteractManagerField = new FieldAccess<>(
			EntityPlayer.class, "playerInteractManager", PlayerInteractManager.class);

	private static final FieldAccess<Integer> lastDigTick = new FieldAccess<>(PlayerInteractManager.class,
			"lastDigTick", int.class);
	private static final FieldAccess<Integer> currentTick = new FieldAccess<>(PlayerInteractManager.class,
			"currentTick", int.class);
	private static final FieldAccess<Boolean> isDestroying = new FieldAccess<>(PlayerInteractManager.class, "d",
			boolean.class);
	private static final FieldAccess<BlockPosition> destroyPosition = new FieldAccess<>(PlayerInteractManager.class,
			"f", BlockPosition.class);
	private static final FieldAccess<Integer> force = new FieldAccess<>(PlayerInteractManager.class, "k",
			int.class);

	private final EntityPlayer player;
	private final VirtualConnection connection;

	public static VirtualPlayerInteractManager inject(VirtualConnection connection) {
		VirtualPlayerInteractManager manager = new VirtualPlayerInteractManager(connection);
		playerInteractManagerField.set(connection.getPlayer(), manager);
		return manager;
	}

	/**
	 * Gives the player a vanilla PlayerInteractManager again, with the same state.
	 */
	public static void uninject(EntityPlayer handle) {
		if (!(handle.playerInteractManager instanceof VirtualPlayerInteractManager))
			return;

		PlayerInteractManager vanilla = new PlayerInteractManager(handle.getWorld());
		FieldAccess.copyFields(PlayerInteractManager.class, handle.playerInteractManager, vanilla);
		playerInteractManagerField.set(handle, vanilla);
	}

	private VirtualPlayerInteractManager(VirtualConnection connection) {
		super(connection.getPlayer().getWorld());
		// only PlayerInteractManager's fields, so a manager left behind by a reload,
		// whose class is from the old plugin, can be copied from too
		FieldAccess.copyFields(PlayerInteractManager.class, connection.getPlayer().playerInteractManager, this);
		this.player = connection.getPlayer();
		this.connection = connection;
	}

	// called every tick
	@Override
	public void a() {
		if (!isDestroying()) {
			super.a();
			return;
		}

		BlockPosition pos = getDestroyPosition();
		IVirtualWorld env = connection.getEnvironment();
		if (!env.contains(pos)) {
			super.a();
			return;
		}

		setCurrentTick(MinecraftServer.currentTick);

		Block block = env.getType(pos).getBlock();
		if (block.getMaterial() == Material.AIR) {
			env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, -1));
			setForce(0);
			setIsDestroying(false);
			return;
		}

		int k = getCurrentTick() - getLastDigTick();
		float f = block.getDamage(player, world, pos) * (float) (k + 1);
		int force = (int) (f * 10.0F);
		if (force != getForce()) {
			env.broadcast(new PacketPlayOutBlockBreakAnimation(player.getId(), pos, force));
			setForce(force);
		}
	}

	// player interact manager utils
	public int getLastDigTick() {
		return lastDigTick.get(this);
	}

	public void setLastDigTick(int val) {
		lastDigTick.set(this, val);
	}

	public void setCurrentTick(int val) {
		currentTick.set(this, val);
	}

	public int getCurrentTick() {
		return currentTick.get(this);
	}

	public BlockPosition getDestroyPosition() {
		return destroyPosition.get(this);
	}

	public void setDestroyPosition(BlockPosition to) {
		destroyPosition.set(this, to);
	}

	public void setIsDestroying(boolean to) {
		isDestroying.set(this, to);
	}

	public boolean isDestroying() {
		return isDestroying.get(this);
	}

	public void setForce(int to) {
		force.set(this, to);
	}

	public int getForce() {
		return force.get(this);
	}

}

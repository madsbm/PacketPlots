package net.dashmc.plots.player;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import net.dashmc.plots.plot.EnvironmentManager;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.utils.helpers.FieldAccess;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.Material;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockBreakAnimation;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;

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

	private EntityPlayer player;
	private EnvironmentManager environments;

	public static VirtualPlayerInteractManager inject(Player player, EnvironmentManager environments) {
		return inject(((CraftPlayer) player).getHandle(), environments);
	}

	public static VirtualPlayerInteractManager inject(EntityPlayer player, EnvironmentManager environments) {
		VirtualPlayerInteractManager manager = new VirtualPlayerInteractManager(player, environments);
		playerInteractManagerField.set(player, manager);
		return manager;
	}

	/**
	 * Gives the player a vanilla PlayerInteractManager again, with the same state.
	 */
	public static void uninject(Player player) {
		EntityPlayer handle = ((CraftPlayer) player).getHandle();
		if (!(handle.playerInteractManager instanceof VirtualPlayerInteractManager))
			return;

		PlayerInteractManager vanilla = new PlayerInteractManager(handle.getWorld());
		copyFields(handle.playerInteractManager, vanilla);
		playerInteractManagerField.set(handle, vanilla);
	}

	/**
	 * Copies the fields PlayerInteractManager declares. Fields of subclasses are
	 * left out, so a manager left behind by a reload, whose class is from the old
	 * plugin, can be copied from too.
	 */
	private static void copyFields(PlayerInteractManager source, PlayerInteractManager target) {
		for (Field field : PlayerInteractManager.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()))
				continue;

			FieldAccess<Object> access = new FieldAccess<>(field, Object.class);
			access.set(target, access.get(source));
		}
	}

	private VirtualPlayerInteractManager(EntityPlayer player, EnvironmentManager environments) {
		super(player.getWorld());
		copyFields(player.playerInteractManager, this);
		this.player = player;
		this.environments = environments;
	}

	// called every tick
	@Override
	public void a() {
		if (!isDestroying()) {
			super.a();
			return;
		}

		BlockPosition pos = getDestroyPosition();
		IEnvironment env = environments.get(player);
		if (env == null || !env.contains(pos)) {
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

package net.dashmc.plots.plot.data.blocks.wrappers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import lombok.Getter;
import net.dashmc.plots.plot.IEnvironment;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EntityHuman;
import net.minecraft.server.v1_8_R3.PacketPlayOutBlockAction;
import net.minecraft.server.v1_8_R3.TileEntityEnderChest;

@Getter
public class EnderChestTileWrapper extends TileEntityEnderChest {
	private final IEnvironment environment;
	private TileEntityEnderChest wrapped;

	public EnderChestTileWrapper(IEnvironment env, TileEntityEnderChest src) {
		this.environment = env;
		this.wrapped = src;

		copyFields(src);
	}

	@Override
	public boolean a(EntityHuman player) {
		if (!(environment.getTileEntity(this.position) instanceof TileEntityEnderChest))
			return false;

		return !(player.e((double) this.position.getX() + 0.5, (double) this.position.getY() + 0.5,
				(double) this.position.getZ() + 0.5) > 64.0);
	}

	@Override
	public void b() {
		++wrapped.g;

		PacketPlayOutBlockAction chestOpenPacket = new PacketPlayOutBlockAction(this.position, Blocks.ENDER_CHEST, 1,
				1);
		environment.broadcast(chestOpenPacket);
	}

	@Override
	public void d() {
		--wrapped.g;

		PacketPlayOutBlockAction chestClosePacket = new PacketPlayOutBlockAction(this.position, Blocks.ENDER_CHEST, 1,
				0);
		environment.broadcast(chestClosePacket);
	}

	private void copyFields(TileEntityEnderChest source) {
		Class<?> clazz = source.getClass();
		while (clazz != null) {
			for (Field field : clazz.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()))
					continue;

				field.setAccessible(true);
				try {
					field.set(this, field.get(source));
				} catch (IllegalAccessException e) {
					throw new RuntimeException("Failed to copy field: " + field.getName(), e);
				}
			}
			clazz = clazz.getSuperclass();
		}
	}
}

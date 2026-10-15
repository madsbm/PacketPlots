package net.dashmc.plots.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.utils.CuboidRegion;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;

@Getter
public class PlotConfig extends OkaeriConfig {

	@Comment("The virtualized region")
	CuboidRegion region;

	@Comment({
			"The region in which to disable any anti cheats.",
			"As this plugin is purely packed-based, anticheats",
			"will almost always false-flag players operating inside",
			"the virtualized region. When checking if the player is inside",
			"of this region, we use a 1-block buffer."
	})
	@NotRequired
	CuboidRegion antiCheatDisabledRegion;

	public CuboidRegion getDefaultedACRegion() {
		if (this.antiCheatDisabledRegion == null) {
			return region;
		}

		return antiCheatDisabledRegion;
	}

	public class ChunkConfig extends OkaeriConfig {
		public ChunkCoordIntPair coords;
		public ArrayList<Integer> sections = new ArrayList<>();

		public char getSectionsAsMask() {
			char mask = 0;
			for (int section : sections) {
				mask |= 1 << section;
			}

			return mask;
		}
	}

	@Comment("World to virtualize the chunks in")
	World world = Bukkit.getWorlds().get(0);

	@Comment("Safe location to teleport the player to when they visit/etc.")
	Location safeLocation;

	public Location getSafeLocation() {
		if (safeLocation.getWorld() == null)
			safeLocation.setWorld(world);

		return safeLocation;
	}

	public void validate() {
		for (Field field : getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(NotRequired.class))
				continue;

			try {
				Object value = field.get(this);
				Objects.requireNonNull(value, "Field " + field.getName() + " is null in "
						+ PacketPlots.getInstance().getName() + " configuration!");
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Fields with this annotation may be left out of the config
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	private @interface NotRequired {
	}

}

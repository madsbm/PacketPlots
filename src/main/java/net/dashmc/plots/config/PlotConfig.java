package net.dashmc.plots.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import net.dashmc.plots.utils.CuboidRegion;

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

	@Comment("World to virtualize the chunks in")
	World world = Bukkit.getWorlds().get(0);

	@Comment("Safe location to teleport the player to when they visit/etc.")
	Location safeLocation;

	public Location getSafeLocation() {
		if (safeLocation.getWorld() == null)
			safeLocation.setWorld(world);

		return safeLocation;
	}

	@Comment("Log what the plugin is doing, for debugging. Very verbose.")
	boolean debug = false;

	public void validate() {
		for (Field field : getClass().getDeclaredFields()) {
			if (field.isAnnotationPresent(NotRequired.class))
				continue;

			Object value;
			try {
				value = field.get(this);
			} catch (IllegalAccessException e) {
				// can't happen, as the fields are declared in this class
				throw new IllegalStateException("Couldn't read config field " + field.getName(), e);
			}

			Objects.requireNonNull(value, "Field " + field.getName() + " is null in the PacketPlots configuration!");
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

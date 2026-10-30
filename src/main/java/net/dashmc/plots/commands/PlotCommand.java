package net.dashmc.plots.commands;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import eu.okaeri.commands.annotation.Arg;
import eu.okaeri.commands.annotation.Completion;
import eu.okaeri.commands.annotation.Context;
import eu.okaeri.commands.annotation.Executor;
import eu.okaeri.commands.bukkit.annotation.Permission;
import eu.okaeri.commands.bukkit.response.BukkitResponse;
import eu.okaeri.commands.bukkit.response.RawResponse;
import eu.okaeri.commands.service.CommandService;
import net.dashmc.plots.PacketPlots;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.data.BlockBag;

@eu.okaeri.commands.annotation.Command(label = "plot", description = "Plot command")
public class PlotCommand implements CommandService {

	@Permission(value = { "*", "plot.virtualize" })
	@Completion(arg = "value", value = { "on", "off" })
	@Executor(pattern = "virtualize *")
	public BukkitResponse virtualize(@Context Player player, @Arg String value) {
		if (value.equals("on"))
			PacketPlots.getEnvironmentManager().startVirtualization(player);
		else if (value.equals("off"))
			PacketPlots.getEnvironmentManager().stopVirtualization(player);
		else
			return RawResponse.of("Invalidt argument {arg}. Vælg on eller off.").with("arg", value);

		return RawResponse.of("Toggled plot virtualization {arg}!").with("arg", value.toLowerCase());
	}

	@Completion(arg = "action", value = { "empty" })
	@Executor(pattern = "blockbag *")
	public BukkitResponse blockBag(@Context Player sender, @Arg String action) {
		if (action.equals("empty")) {
			BlockBag.getBlockBag(sender).emptyIfPossible();
			return RawResponse.of("Du tømte din block bag!");
		}

		return RawResponse.of("Invalidt argument! Vælg empty for at tømme din block bag!");
	}

	@Executor(pattern = "visit *")
	public BukkitResponse visit(@Context Player sender, @Arg OfflinePlayer player) {
		Player playertoVisit = player.getPlayer();
		String name = player.getName();
		if (playertoVisit == null) {
			return RawResponse.of("Kunne ikke finde en spiller ved navn {name}!")
					.with("name", name);
		}

		IEnvironment other = PacketPlots.getEnvironmentManager().get(playertoVisit);
		if (other == null)
			return RawResponse.of("Spilleren {name}s plot er ikke indlæst, og du kan derfor ikke besøge det.")
					.with("name", name);

		VirtualConnection conn = VirtualConnection.get(sender);
		conn.visit(other);

		return RawResponse.of("Du besøger nu {name}'s plot!")
				.with("name", name);
	}
}

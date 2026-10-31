package net.dashmc.plots.packets.interceptors;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.plot.interaction.ChestCloser;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayInCloseWindow;

@RequiredArgsConstructor
public class WindowClosePacketInterceptor extends PacketInterceptor<PacketPlayInCloseWindow> {
	private final ChestCloser chests;

	@Override
	public boolean intercept(PacketPlayInCloseWindow packet, VirtualConnection connection) {

		IEnvironment environment = connection.getEnvironment();
		EntityPlayer player = connection.getPlayer();

		return chests.closeCurrentChest(player, environment, false);
	}

}

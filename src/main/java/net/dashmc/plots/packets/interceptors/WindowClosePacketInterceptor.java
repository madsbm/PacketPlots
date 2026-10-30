package net.dashmc.plots.packets.interceptors;

import net.dashmc.plots.packets.InterceptorRegistry;
import net.dashmc.plots.packets.PacketInterceptor;
import net.dashmc.plots.plot.IEnvironment;
import net.dashmc.plots.plot.VirtualConnection;
import net.dashmc.plots.utils.helpers.ChestHelper;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.PacketPlayInCloseWindow;

public class WindowClosePacketInterceptor extends PacketInterceptor<PacketPlayInCloseWindow> {

	@Override
	public boolean intercept(PacketPlayInCloseWindow packet, VirtualConnection connection) {

		IEnvironment environment = connection.getEnvironment();
		EntityPlayer player = connection.getPlayer();

		return ChestHelper.closeCurrentChest(player, environment, false);
	}

	@Override
	public Class<PacketPlayInCloseWindow> getClazz() {
		return PacketPlayInCloseWindow.class;
	}

	public static void register() {
		InterceptorRegistry.register(new WindowClosePacketInterceptor());
	}
}

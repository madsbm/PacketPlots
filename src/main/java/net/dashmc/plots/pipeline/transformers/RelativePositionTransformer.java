package net.dashmc.plots.pipeline.transformers;

import org.bukkit.util.BlockVector;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

@RequiredArgsConstructor
public class RelativePositionTransformer implements IRenderTransformer {
	final BlockVector offset;

	@Override
	public void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx) {
		byte[] blockIds = new byte[chunkMap.a.length];

		int offsetX = offset.getBlockX();
		int offsetY = offset.getBlockY();
		int offsetZ = offset.getBlockZ();

		for (int idx = 0; idx < metaStartIdx; idx += 2) {
			byte b1 = chunkMap.a[idx];
			byte b2 = chunkMap.a[idx + 1];

			if ((b1 | b2) == 0)
				continue;

			int shortIdx = idx >> 1;
			int x = (shortIdx & 0xF) + offsetX;
			int y = ((shortIdx >> 8) & 0xFF) + offsetY;
			int z = ((shortIdx >> 4) & 0xF) + offsetZ;

			if (x >= 0 && x < 16 && y >= 0 && z >= 0 && z < 16) {
				int newIdx = ((y << 8) | (z << 4) | x) << 1;
				blockIds[newIdx] = b1;
				blockIds[newIdx + 1] = b2;
			}
		}

		System.arraycopy(chunkMap.a, metaStartIdx, blockIds, metaStartIdx, blockIds.length - metaStartIdx);
		chunkMap.a = blockIds;
	}

}

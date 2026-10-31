package net.dashmc.plots.api.pipeline.transformers;

import org.bukkit.util.BlockVector;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsChunks;
import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

@RequiredArgsConstructor
public class RelativePositionTransformer implements IRenderTransformer {
	final BlockVector offset;

	@Override
	public void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx) {
		byte[] data = NmsChunks.getData(chunkMap);
		byte[] blockIds = new byte[data.length];

		int offsetX = offset.getBlockX();
		int offsetY = offset.getBlockY();
		int offsetZ = offset.getBlockZ();

		for (int idx = 0; idx < metaStartIdx; idx += 2) {
			byte b1 = data[idx];
			byte b2 = data[idx + 1];

			if ((b1 | b2) == 0)
				continue;

			int shortIdx = idx >> 1;
			int x = Utils.getIndexX(shortIdx) + offsetX;
			// counted across the map's sections, which follow each other
			int y = ((shortIdx >> 8) & 0xFF) + offsetY;
			int z = Utils.getIndexZ(shortIdx) + offsetZ;

			if (x >= 0 && x < 16 && y >= 0 && z >= 0 && z < 16) {
				int newIdx = ((y << 8) | (z << 4) | x) << 1;
				blockIds[newIdx] = b1;
				blockIds[newIdx + 1] = b2;
			}
		}

		System.arraycopy(data, metaStartIdx, blockIds, metaStartIdx, blockIds.length - metaStartIdx);
		NmsChunks.setData(chunkMap, blockIds);
	}

}

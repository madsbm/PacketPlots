package net.dashmc.plots.api.pipeline.transformers;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsChunks;
import net.dashmc.plots.pipeline.IRenderTransformer;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.Chunk;
import net.minecraft.server.v1_8_R3.ChunkCoordIntPair;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

@RequiredArgsConstructor
@AllArgsConstructor
public class BackdropAdderTransformer implements IRenderTransformer {
	private final Chunk backdropChunk;
	private CuboidRegion toExclude = null;

	@Override
	public void accept(VirtualChunk virtualChunk, ChunkMap chunkMap, int metaStartIdx) {
		byte[] data = NmsChunks.getData(chunkMap);
		int sectionMask = NmsChunks.getSectionMask(chunkMap);
		int mapPointer = 0;
		ChunkCoordIntPair backdropXZ = NmsChunks.getCoords(backdropChunk);

		for (int i = 0; i < 16; i++) {
			ChunkSection section = backdropChunk.getSections()[i];
			if (!Utils.hasSection(sectionMask, i))
				continue;
			else if ((section == null)) {
				mapPointer += 4096 * 2;
				continue;
			}

			char[] blockIds = section.getIdArray();
			for (int sectionPointer = 0; sectionPointer < blockIds.length; sectionPointer++, mapPointer += 2) {
				if (toExclude != null) {
					int shortIdx = mapPointer >> 1;

					// dont apply the offset here as the region isnt offset!
					int x = Utils.getIndexX(shortIdx) + (backdropXZ.x << 4);
					int y = Utils.getIndexY(shortIdx) + (i << 4);
					int z = Utils.getIndexZ(shortIdx) + (backdropXZ.z << 4);

					boolean includes = toExclude.includes(x, y, z);
					if (includes)
						continue;
				}

				if (Utils.getMapBlockId(data, mapPointer) != (char) 0)
					continue;

				Utils.setMapBlockId(data, mapPointer, blockIds[sectionPointer]);
			}
		}
	}

}

package net.dashmc.plots.pipeline;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.nms.NmsChunks;
import net.dashmc.plots.nms.NmsWorlds;
import net.dashmc.plots.plot.data.VirtualChunk;
import net.dashmc.plots.plot.data.VirtualChunk.Section;
import net.dashmc.plots.utils.misc.Utils;
import net.minecraft.server.v1_8_R3.ChunkSection;
import net.minecraft.server.v1_8_R3.PacketPlayOutMapChunk.ChunkMap;

@RequiredArgsConstructor
public class RenderPipeline {
	private static byte[][] lightArrays = new byte[16][];

	private final List<IRenderTransformer> transformers;

	public RenderPipeline() {
		this(new LinkedList<>());
	}

	public ChunkMap render(VirtualChunk chunk) {
		return render(chunk, true);
	}

	public ChunkMap render(VirtualChunk chunk, boolean includeBiome) {
		return render(chunk, 0xffff, includeBiome);
	}

	public ChunkMap render(VirtualChunk chunk, int mask, boolean includeBiome) {
		ChunkMap map = getChunkMap(chunk, mask, includeBiome);

		int nonEmptyChunkSections = Integer.bitCount(NmsChunks.getSectionMask(map));
		// block ids are 2 bytes each - light and biome data follow them
		int metaStartIdx = nonEmptyChunkSections * 2 * 16 * 16 * 16;

		transformers.forEach((consumer) -> consumer.accept(chunk, map, metaStartIdx));
		return map;
	}

	protected ChunkMap getChunkMap(VirtualChunk chunk, int mask, boolean includeBiome) {
		boolean hasSky = NmsWorlds.hasSky(chunk.getChunk().getWorld());

		ChunkMap chunkMap = new ChunkMap();
		List<Section> arraylist = Lists.newArrayList();
		Section[] sections = chunk.getSections();

		int sectionMask = 0;
		int j;
		for (j = 0; j < sections.length; j++) {
			if (Utils.hasSection(chunk.getSectionMask(), j)) {
				Section section = sections[j];
				if (section != null && !section.isEmpty() && Utils.hasSection(mask, j)) {
					sectionMask |= 1 << j;
					arraylist.add(section);
				}
			} else {
				ChunkSection chunkSection = chunk.getChunk().getSections()[j];
				if (chunkSection == null)
					continue;
				Section section = chunk.new Section(chunkSection);
				if (!section.isEmpty() && Utils.hasSection(mask, j)) {
					sectionMask |= 1 << j;
					arraylist.add(section);
				}
			}
		}

		int nonEmptyChunkSections = Integer.bitCount(sectionMask);
		byte[] data = new byte[calculateNeededBytes(nonEmptyChunkSections, hasSky, includeBiome)];
		NmsChunks.setSectionMask(chunkMap, sectionMask);
		NmsChunks.setData(chunkMap, data);

		j = 0;
		Section section;
		Iterator<Section> iterator = arraylist.iterator();
		while (iterator.hasNext()) {
			section = iterator.next();
			char[] idArray = section.getBlockIds();

			for (int l = 0; l < idArray.length; l++, j += 2) {
				Utils.setMapBlockId(data, j, idArray[l]);
			}
		}

		byte[] lightArray = lightArrays[nonEmptyChunkSections];
		System.arraycopy(lightArray, 0, data, j, lightArray.length);

		if (hasSky) {
			j += lightArray.length;
			System.arraycopy(lightArray, 0, data, j, lightArray.length);
		}

		if (includeBiome) {
			j += lightArray.length;
			System.arraycopy(chunk.getChunk().getBiomeIndex(), 0, data, j, 256);
		}

		return chunkMap;
	}

	private static int calculateNeededBytes(int i, boolean hasSky, boolean includeBiome) {
		int j = i * 2 * 16 * 16 * 16;
		int k = i * 16 * 16 * 8;
		int l = hasSky ? i * 16 * 16 * 8 : 0;
		int biome = includeBiome ? 256 : 0;

		return j + k + l + biome;
	}

	static {
		for (int i = 0; i < lightArrays.length; i++) {
			int lightArrayLength = i * 16 * 16 * 8;
			lightArrays[i] = new byte[lightArrayLength];
			Arrays.fill(lightArrays[i], (byte) 255);
		}
	}

}

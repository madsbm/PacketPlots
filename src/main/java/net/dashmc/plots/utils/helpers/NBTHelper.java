package net.dashmc.plots.utils.helpers;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import net.minecraft.server.v1_8_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_8_R3.NBTReadLimiter;
import net.minecraft.server.v1_8_R3.NBTTagCompound;

/**
 * Seeing as NBTCompressedStreamTools are so cryptic,
 * this wrapper exists to give proper names to the methods
 */
public class NBTHelper {
	// the most a payload may take in memory, like vanilla's limit for a packet's NBT
	private static final long MAX_PAYLOAD_SIZE = 2097152L;

	public static void writePayload(DataOutput os, NBTTagCompound tag) throws IOException {
		NBTCompressedStreamTools.a(tag, os);
	}

	public static NBTTagCompound loadPayload(DataInput input) throws IOException {
		return NBTCompressedStreamTools.a(input, new NBTReadLimiter(MAX_PAYLOAD_SIZE));
	}
}

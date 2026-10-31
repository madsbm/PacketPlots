package net.dashmc.plots.nms;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.Test;

import net.minecraft.server.v1_8_R3.NBTTagCompound;

public class NmsNbtTest {

	@Test
	public void aPayloadLoadsAsItWasWritten() throws IOException {
		NBTTagCompound tag = new NBTTagCompound();
		tag.setInt("x", 5);
		tag.setString("name", "chest");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		NmsNbt.writePayload(new DataOutputStream(bytes), tag);
		NBTTagCompound loaded = NmsNbt.loadPayload(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

		assertEquals(tag, loaded);
	}

	@Test
	public void mergeAddsAndReplacesTheSourcesTags() {
		NBTTagCompound target = new NBTTagCompound();
		target.setInt("x", 1);
		target.setInt("y", 2);
		NBTTagCompound source = new NBTTagCompound();
		source.setInt("y", 3);
		source.setInt("z", 4);

		NmsNbt.merge(target, source);

		assertEquals(1, target.getInt("x"));
		assertEquals(3, target.getInt("y"));
		assertEquals(4, target.getInt("z"));
	}
}

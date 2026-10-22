package dev.stray.client.visual.motionblur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryStack;

import java.util.function.Consumer;

public final class GpuBufferUtil {
	private GpuBufferUtil() {
	}

	public static GpuBuffer createUBO(String debugName, int sizeBytes) {
		int usage = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
		return RenderSystem.getDevice().createBuffer(() -> "stray motionblur:" + debugName, usage, sizeBytes);
	}

	public static void write(GpuBuffer buffer, int sizeBytes, Consumer<Std140Builder> writer) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder builder = Std140Builder.onStack(stack, sizeBytes);
			writer.accept(builder);
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), builder.get());
		}
	}

	public static void closeQuietly(GpuBuffer buffer) {
		if (buffer == null) {
			return;
		}
		try {
			buffer.close();
		} catch (RuntimeException ignored) {
		}
	}

	public static boolean isClosedBufferException(RuntimeException e) {
		String message = e.getMessage();
		return message != null && message.toLowerCase().contains("closed");
	}
}

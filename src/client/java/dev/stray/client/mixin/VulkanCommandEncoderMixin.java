package dev.stray.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Vanilla 26.3 stages texture uploads with 1-byte alignment, so after an odd-sized
 * upload (font glyphs) the next RGBA copy starts at an offset the Vulkan spec
 * forbids (VUID-vkCmdCopyBufferToImage-dstImage-07975). Align texture staging to 16.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder")
public class VulkanCommandEncoderMixin {
	@ModifyArg(
		method = "writeToTexture",
		at = @At(
			value = "INVOKE",
			target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanTransientMemory;uploadStaging(Ljava/nio/ByteBuffer;JI)Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"
		),
		index = 1,
		require = 0
	)
	private long stray$alignTextureStaging(long alignment) {
		return Math.max(alignment, 16L);
	}
}

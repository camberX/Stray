plugins {
	id("dev.kikugie.stonecutter")
	id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

stonecutter active "26.3" /* [SC] DO NOT EDIT */

stonecutter parameters {
	// Sources are written against 26.3. 26.2 has the same render API under the old
	// com.mojang.blaze3d packages and a few renamed game classes; those names are
	// swapped here. Everything else that differs is an explicit `//? if` block.
	replacements {
		val newer = current.parsed >= "26.3"
		/** Text that reads [old] on 26.2 and [new] from 26.3. */
		fun rename(old: String, new: String) = string(newer) { replace(old, new) }
		/** A class or package in both source (dots) and mixin descriptor (slashes) form. */
		fun renameType(old: String, new: String) {
			rename(old, new)
			rename(old.replace('.', '/'), new.replace('.', '/'))
		}

		// Render API: renderpearl (26.3) was blaze3d (26.2)
		renameType("com.mojang.blaze3d.GpuFormat", "com.mojang.renderpearl.api.GpuFormat")
		renameType("com.mojang.blaze3d.buffers.GpuBuffer", "com.mojang.renderpearl.api.buffers.GpuBuffer")
		renameType("com.mojang.blaze3d.systems.CommandEncoder", "com.mojang.renderpearl.api.commands.CommandEncoder")
		renameType("com.mojang.blaze3d.systems.RenderPass", "com.mojang.renderpearl.api.commands.RenderPass")
		renameType("com.mojang.blaze3d.systems.GpuDevice", "com.mojang.renderpearl.api.device.GpuDevice")
		renameType("com.mojang.blaze3d.pipeline.BindGroupLayout", "com.mojang.renderpearl.api.pipeline.BindGroupLayout")
		renameType("com.mojang.blaze3d.platform.BlendFactor", "com.mojang.renderpearl.api.pipeline.BlendFactor")
		renameType("com.mojang.blaze3d.pipeline.BlendFunction", "com.mojang.renderpearl.api.pipeline.BlendFunction")
		renameType("com.mojang.blaze3d.pipeline.ColorTargetState", "com.mojang.renderpearl.api.pipeline.ColorTargetState")
		renameType("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
		renameType("com.mojang.blaze3d.pipeline.DepthStencilState", "com.mojang.renderpearl.api.pipeline.DepthStencilState")
		renameType("com.mojang.blaze3d.IndexType", "com.mojang.renderpearl.api.pipeline.IndexType")
		renameType("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
		renameType("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
		renameType("com.mojang.blaze3d.shaders.ShaderSource", "com.mojang.renderpearl.api.pipeline.ShaderSource")
		renameType("com.mojang.blaze3d.shaders.ShaderType", "com.mojang.renderpearl.api.pipeline.ShaderType")
		renameType("com.mojang.blaze3d.shaders.UniformType", "com.mojang.renderpearl.api.pipeline.UniformType")
		renameType("com.mojang.blaze3d.textures.FilterMode", "com.mojang.renderpearl.api.textures.FilterMode")
		renameType("com.mojang.blaze3d.textures.GpuSampler", "com.mojang.renderpearl.api.textures.GpuSampler")
		renameType("com.mojang.blaze3d.textures.GpuTexture", "com.mojang.renderpearl.api.textures.GpuTexture")
		renameType("com.mojang.blaze3d.vertex.VertexFormat", "com.mojang.renderpearl.api.vertex.VertexFormat")
		renameType("com.mojang.blaze3d.opengl.", "com.mojang.renderpearl.backend.opengl.")
		renameType("com.mojang.blaze3d.vulkan.", "com.mojang.renderpearl.backend.vulkan.")

		// Game classes and constants renamed in 26.3
		rename("net.minecraft.world.entity.monster.EnderMan;", "net.minecraft.world.entity.monster.Enderman;")
		rename("instanceof EnderMan", "instanceof Enderman")
		rename("(EnderMan.class", "(Enderman.class")
		rename("InputConstants.Type.KEYSYM", "InputConstants.Type.KEYBOARD")
		rename("case KEYSYM ->", "case KEYBOARD ->")
		rename("InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), ", "InputConstants.isKeyDown(")
		renameType("net.minecraft.client.renderer.DynamicUniforms;", "net.minecraft.client.renderer.DynamicGpuData;")
		rename("(DynamicUniforms uniforms", "(DynamicGpuData uniforms")
		rename("@Mixin(DynamicUniforms.class)", "@Mixin(DynamicGpuData.class)")
	}
}

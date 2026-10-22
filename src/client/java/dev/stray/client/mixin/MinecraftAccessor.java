package dev.stray.client.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("profileKeyPairManager")
	ProfileKeyPairManager stray$profileKeys();

	@Accessor("profileKeyPairManager")
	@Mutable
	void stray$profileKeys(ProfileKeyPairManager keys);

	@Accessor("user")
	@Mutable
	void stray$user(User user);

	@Accessor("services")
	@Mutable
	void stray$services(Services services);

	@Accessor("userApiService")
	UserApiService stray$userApi();

	@Accessor("userApiService")
	@Mutable
	void stray$userApiService(UserApiService service);

	@Accessor("mainRenderTarget")
	RenderTarget stray$mainRenderTarget();

	@Accessor("mainRenderTarget")
	@Mutable
	void stray$mainRenderTarget(RenderTarget target);
}

package dev.stray.client.mixin;

import dev.stray.client.visual.NickHider;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Text placed in the world (vanilla nametags, and other mods' world labels such as
 * NoammAddons' teammate names) is submitted here; the nick hider and Hide other names apply.
 */
@Mixin(SubmitNodeCollection.class)
public class SubmitTextNamesMixin {
	@ModifyVariable(method = "submitText", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private FormattedCharSequence stray$names(FormattedCharSequence text) {
		return NickHider.rewrite(text);
	}
}

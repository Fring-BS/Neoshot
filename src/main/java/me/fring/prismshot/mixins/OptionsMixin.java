/*
 * MIT License
 *
 * Copyright (c) 2026 Fring (Prismshot fork)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.fring.prismshot.mixins;

import me.fring.prismshot.Prismshot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects Prismshot's keybindings into Minecraft's Options.keyMappings array.
 * <p>
 * This replaces Fabric API's {@code KeyMappingHelper.registerKeyMapping()} for
 * environments where Fabric API is not available at build time.
 */
@Mixin(Options.class)
public class OptionsMixin {

    @Shadow
    @Final
    @Mutable
    public KeyMapping[] keyMappings;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void prismshot$registerKeyBindings(CallbackInfo ci) {
        KeyMapping[] original = this.keyMappings;
        KeyMapping[] combined = new KeyMapping[original.length + Prismshot.KEY_BINDINGS.length];
        System.arraycopy(original, 0, combined, 0, original.length);
        System.arraycopy(Prismshot.KEY_BINDINGS, 0, combined, original.length, Prismshot.KEY_BINDINGS.length);
        this.keyMappings = combined;
    }
}

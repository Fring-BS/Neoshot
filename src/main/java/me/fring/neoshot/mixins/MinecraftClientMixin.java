/*
 * MIT License
 *
 * Copyright (c) 2021 Ramid Khan
 * Copyright (c) 2026 Fring (Neoshot fork)
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

package me.fring.neoshot.mixins;

import me.fring.neoshot.Neoshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {

    /**
     * Advance the capture state machine after the game renderer finishes rendering.
     * The screenshot is captured here (reads from the framebuffer texture).
     */
    @Inject(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V", shift = At.Shift.AFTER))
    private void postRender(CallbackInfo callbackInfo) {
        Neoshot.onRenderPreOrPost();
    }

    /**
     * Restore the framebuffer at the START of the next frame, BEFORE GameRenderer.render.
     * This prevents the black screen that occurs when the framebuffer is destroyed and
     * recreated between render and blitToScreen.
     *
     * Flow without this fix:
     *   Frame N: render 4K, capture, [restore destroys 4K, creates blank 1080p], blitToScreen → BLACK
     *
     * Flow with this fix:
     *   Frame N: render 4K, capture, blitToScreen (shows 4K, zoom flash), set pendingRestore
     *   Frame N+1: [restore to 1080p], render 1080p, blitToScreen (normal) → no black screen
     */
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void preRenderFrame(CallbackInfo callbackInfo) {
        Neoshot.onRenderFrameStart();
    }
}

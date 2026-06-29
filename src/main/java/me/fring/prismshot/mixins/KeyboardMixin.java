/*
 * MIT License
 *
 * Copyright (c) 2021 Ramid Khan
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
import me.fring.prismshot.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * In MC 26.2, screenshot key handling moved from KeyboardHandler to Minecraft.
 * The screenshot is taken via:
 *   options.keyScreenshot.matches(key) → Screenshot.grab(this, controlDown)
 *
 * We inject into Minecraft.handleGlobalKeyPress (which returns boolean) to:
 * Override vanilla F2 if override_screenshot_key is enabled.
 * F9 capture is handled via KeyMapping.consumeClick in the client tick event.
 */
@Mixin(Minecraft.class)
public class KeyboardMixin {

    /**
     * Intercept the vanilla screenshot key (F2). If override_screenshot_key is enabled,
     * trigger Prismshot capture instead of vanilla Screenshot.grab.
     *
     * handleGlobalKeyPress returns boolean, so we use CallbackInfoReturnable<Boolean>.
     */
    @Inject(method = "handleGlobalKeyPress", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V"), cancellable = true)
    private void onVanillaScreenshot(CallbackInfoReturnable<Boolean> cir) {
        if (Config.OVERRIDE_SCREENSHOT_KEY) {
            Prismshot.startCapture();
            cir.setReturnValue(true);
        }
    }
}

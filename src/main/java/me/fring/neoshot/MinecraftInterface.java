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

package me.fring.neoshot;

import me.fring.neoshot.config.Config;
import me.fring.neoshot.mixins.WindowAccessor;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

public interface MinecraftInterface {

    Minecraft CLIENT = Minecraft.getInstance();

    /** Saved original framebuffer dimensions, restored after capture. -1 = not saved yet. */
    int[] SAVED = { -1, -1, -1 }; // [0]=fbWidth, [1]=fbHeight, [2]=guiScale

    static int getDisplayWidth() {
        return CLIENT.getWindow().getWidth();
    }

    static int getDisplayHeight() {
        return CLIENT.getWindow().getHeight();
    }

    static void refresh() {
        var framebuffer = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (framebuffer == null) return;

        Window window = Minecraft.getInstance().getWindow();
        WindowAccessor accessor = (WindowAccessor) (Object) window;

        if (Neoshot.isInCapture()) {
            // CAPTURE MODE: Save original values and resize to capture dimensions
            if (SAVED[0] == -1) {
                SAVED[0] = accessor.neoshot$getFramebufferWidth();
                SAVED[1] = accessor.neoshot$getFramebufferHeight();
                SAVED[2] = accessor.neoshot$getGuiScale();
            }

            // Set framebuffer dimensions to the capture resolution
            accessor.neoshot$setFramebufferWidth(Config.CAPTURE_WIDTH);
            accessor.neoshot$setFramebufferHeight(Config.CAPTURE_HEIGHT);

            // Scale the GUI scale proportionally so the HUD stays at the correct relative size
            int realGuiScale = SAVED[2];
            int realFbWidth = SAVED[0];
            if (realFbWidth > 0 && realGuiScale > 0) {
                int captureGuiScale = Math.max(1, realGuiScale * Config.CAPTURE_WIDTH / realFbWidth);
                window.setGuiScale(captureGuiScale);
            }

            // Resize the render target to the capture dimensions
            framebuffer.resize(Config.CAPTURE_WIDTH, Config.CAPTURE_HEIGHT);
        } else {
            // Restore original framebuffer dimensions and GUI scale
            if (SAVED[0] != -1) {
                accessor.neoshot$setFramebufferWidth(SAVED[0]);
                accessor.neoshot$setFramebufferHeight(SAVED[1]);
                window.setGuiScale(SAVED[2]);
                SAVED[0] = -1;
                SAVED[1] = -1;
                SAVED[2] = -1;
            }

            // Resize the render target back to the original dimensions
            framebuffer.resize(window.getWidth(), window.getHeight());
        }
    }
}

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

package me.fring.prismshot.capture;

import me.fring.prismshot.config.Config;
import me.fring.prismshot.MinecraftInterface;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.nio.file.Path;

public class CaptureTask {

    private final Path file;
    private boolean hudHidden;
    private int frame;
    private boolean saving;
    private boolean savingDone;

    public CaptureTask(Path file) {
        this.file = file;
    }

    public boolean onRenderTick() {
        if (frame == 0) {
            hudHidden = Minecraft.getInstance().options.hideGui;
            Minecraft.getInstance().options.hideGui |= Config.HIDE_HUD;
            frame++;
        } else if (frame < Config.CAPTURE_DELAY) {
            frame++;
        } else if (saving) {
            Minecraft.getInstance().options.hideGui = hudHidden;
            return savingDone;
        } else {
            saving = true;

            Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget(), nativeImage -> {
                savingDone = true;

                Util.backgroundExecutor().execute(() -> {
                    try (nativeImage) {
                        FramebufferWriter.write(nativeImage, file);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            });
        }

        return false;
    }
}

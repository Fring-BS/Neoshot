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

package me.fring.neoshot.capture;

import me.fring.neoshot.config.Config;
import me.fring.neoshot.MinecraftInterface;
import me.fring.neoshot.mixins.HudAccessor;
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

    private final boolean directCapture;

    public CaptureTask(Path file) {
        this.file = file;

        int displayW = MinecraftInterface.getDisplayWidth();
        int displayH = MinecraftInterface.getDisplayHeight();
        this.directCapture = (Config.CAPTURE_WIDTH == displayW && Config.CAPTURE_HEIGHT == displayH);
    }

    public boolean isDirectCapture() {
        return directCapture;
    }

    private static boolean isHudHidden() {
        return ((HudAccessor) Minecraft.getInstance().gui.hud).neoshot$isHidden();
    }

    private static void setHudHidden(boolean value) {
        ((HudAccessor) Minecraft.getInstance().gui.hud).neoshot$setHidden(value);
    }

    public boolean onRenderTick() {
        // Direct capture mode: capture immediately, no resize, no HUD toggle (unless hide_hud=true)
        if (directCapture) {
            if (frame == 0) {
                frame++;
                if (Config.HIDE_HUD) {
                    hudHidden = isHudHidden();
                    setHudHidden(true);
                }
            } else if (frame == 1) {
                if (!saving) {
                    saving = true;
                    Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), nativeImage -> {
                        savingDone = true;
                        Util.backgroundExecutor().execute(() -> {
                            try (nativeImage) {
                                FramebufferWriter.write(nativeImage, file);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    });
                } else if (savingDone) {
                    if (Config.HIDE_HUD) {
                        setHudHidden(hudHidden);
                    }
                    return true;
                }
            }
            return false;
        }

        // High-res capture mode: framebuffer was already resized in Neoshot.startCapture()
        if (frame == 0) {
            hudHidden = isHudHidden();
            if (Config.HIDE_HUD) {
                setHudHidden(true);
            }
            frame++;
        } else if (frame < Config.CAPTURE_DELAY) {
            frame++;
        } else if (saving) {
            setHudHidden(hudHidden);
            if (savingDone) {
                return true;
            }
        } else {
            saving = true;
            Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), nativeImage -> {
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

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

package me.fring.prismshot;

import me.fring.prismshot.capture.CaptureTask;
import me.fring.prismshot.config.Config;
import me.fring.prismshot.event.ScreenshotSaveCallback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prismshot — a Fabric fork of Fabrishot that supports high resolution screenshots.
 *
 * <p>Client-only mod. Registered as the {@code client} entrypoint in
 * {@code fabric.mod.json}. The {@link #onInitializeClient()} method loads the
 * config, registers key bindings, and wires up the per-tick listener that
 * drives the capture / config / gallery hotkeys.
 */
public class Prismshot implements ClientModInitializer {

    public static final KeyMapping SCREENSHOT_BINDING = new KeyMapping(
            "key.prismshot.screenshot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC);

    /**
     * Opens the Prismshot config screen when pressed. Default key: M.
     * Rebindable in Options -> Controls -> Prismshot.
     */
    public static final KeyMapping CONFIG_BINDING = new KeyMapping(
            "key.prismshot.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KeyMapping.Category.MISC);

    /**
     * Opens the Screenshot Gallery when pressed. Default key: U.
     * Rebindable in Options -> Controls -> Prismshot.
     */
    public static final KeyMapping GALLERY_BINDING = new KeyMapping(
            "key.prismshot.gallery",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            KeyMapping.Category.MISC);

    private static CaptureTask task;
    private static boolean pendingRestore = false;

    private static void printFileLink(Path path) {
        MutableComponent text = Component.literal(path.toFile().getName()).withStyle(ChatFormatting.UNDERLINE);
        text.setStyle(text.getStyle().withClickEvent(new ClickEvent.OpenFile(path)));
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.translatable("screenshot.success", text)));
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        ScreenshotSaveCallback.register(Prismshot::printFileLink);

        // Register keybindings (Fabric API). In Fabric, there is no separate
        // RegisterKeyMappingsEvent — KeyMappingHelper.registerKeyMapping() can be
        // called directly during client init.
        KeyMappingHelper.registerKeyMapping(SCREENSHOT_BINDING);
        KeyMappingHelper.registerKeyMapping(CONFIG_BINDING);
        KeyMappingHelper.registerKeyMapping(GALLERY_BINDING);

        // Listen for key presses every client tick. Fabric API exposes
        // ClientTickEvents.END_CLIENT_TICK which fires once per tick on the client.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Only trigger when in-game (not already on a screen), so we don't yank
            // the user out of a GUI they're already in.
            if (client.player != null && client.screen == null) {
                // Check F9 (screenshot key) — use consumeClick so it works like a vanilla keybind
                while (SCREENSHOT_BINDING.consumeClick()) {
                    startCapture();
                }
                // Check M (config key)
                while (CONFIG_BINDING.consumeClick()) {
                    client.setScreen(openConfigScreen(null));
                }
                while (GALLERY_BINDING.consumeClick()) {
                    client.setScreen(new me.fring.prismshot.gallery.GalleryScreen());
                }
            }
        });
    }

    /**
     * Shared entry point used by both the ModMenu Config button and the M keybind.
     * Returns the Cloth Config screen if Cloth Config is installed, otherwise
     * a screen telling the user to install it.
     */
    public static Screen openConfigScreen(Screen parent) {
        if (isClothConfigLoaded()) {
            return new me.fring.prismshot.config.ClothConfigBridge().create(parent);
        } else {
            return new me.fring.prismshot.config.InstallClothConfigScreen(parent);
        }
    }

    private static boolean isClothConfigLoaded() {
        try {
            Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public static void startCapture() {
        if (task == null) {
            task = new CaptureTask(getScreenshotFile(Minecraft.getInstance()));
            MinecraftInterface.refresh();
        }
    }

    public static void onRenderPreOrPost() {
        if (task != null && task.onRenderTick()) {
            task = null;
            // Don't restore immediately — the framebuffer still needs to be blitted
            // to screen this frame. Restore at the start of the NEXT frame instead.
            pendingRestore = true;
        }
    }

    /**
     * Called at the START of renderFrame (before GameRenderer.render).
     * If a restore is pending, restore the framebuffer here so the new frame
     * renders at the original resolution. This prevents the black screen that
     * occurs when the framebuffer is destroyed between render and blitToScreen.
     */
    public static void onRenderFrameStart() {
        if (pendingRestore) {
            pendingRestore = false;
            MinecraftInterface.refresh();
        }
    }

    private static Path getScreenshotFile(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("screenshots");

        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        String world = null;

        if (mc.getSingleplayerServer() != null) {
            world = mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            world = mc.getCurrentServer().name;
        }

        Path file;
        String prefix = Config.CUSTOM_FILE_NAME
                .replace("%time%", Util.getFilenameFormattedDateTime())
                .replace("%world%", world != null ? world : "no_world");

        // loop though suffixes while the file exists
        int i = 1;

        do {
            file = dir.resolve(prefix + (i++ == 1 ? "" : "_" + i) + Config.CAPTURE_FILE_FORMAT.extension());
        } while (Files.exists(file));

        return file;
    }

    public static boolean isInCapture() {
        return task != null;
    }
}

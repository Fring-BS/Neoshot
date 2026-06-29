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
 * {@code fabric.mod.json}. Keybindings are registered via a Mixin that injects them
 * into Minecraft's Options.keyMappings array (no Fabric API dependency needed).
 * The per-tick hook is driven by a Mixin on Minecraft.runTick (no Fabric API needed).
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

    /** All keybindings this mod registers. The OptionsMixin injects these into Minecraft's key array. */
    public static final KeyMapping[] KEY_BINDINGS = { SCREENSHOT_BINDING, CONFIG_BINDING, GALLERY_BINDING };

    private static CaptureTask task;
    private static boolean pendingRestore = false;

    private static void printFileLink(Path path) {
        MutableComponent text = Component.literal(path.toFile().getName()).withStyle(ChatFormatting.UNDERLINE);
        text.setStyle(text.getStyle().withClickEvent(new ClickEvent.OpenFile(path)));
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("screenshot.success", text)));
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        ScreenshotSaveCallback.register(Prismshot::printFileLink);
        // Keybindings are registered via OptionsMixin (no Fabric API KeyMappingHelper needed).
        // The per-tick hook is driven by MinecraftClientMixin (no Fabric API ClientTickEvents needed).
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
            pendingRestore = true;
        }
    }

    /**
     * Called at the START of runTick (before GameRenderer.render).
     * If a restore is pending, restore the framebuffer here so the new frame
     * renders at the original resolution.
     */
    public static void onRenderFrameStart() {
        if (pendingRestore) {
            pendingRestore = false;
            MinecraftInterface.refresh();
        }
    }

    /**
     * Called every client tick from MinecraftClientMixin.
     * Handles the F9/M/U keybind presses.
     */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen == null) {
            while (SCREENSHOT_BINDING.consumeClick()) {
                startCapture();
            }
            while (CONFIG_BINDING.consumeClick()) {
                mc.setScreen(openConfigScreen(null));
            }
            while (GALLERY_BINDING.consumeClick()) {
                mc.setScreen(new me.fring.prismshot.gallery.GalleryScreen());
            }
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

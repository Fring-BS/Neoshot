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

import me.fring.neoshot.capture.CaptureTask;
import me.fring.neoshot.config.Config;
import me.fring.neoshot.event.ScreenshotSaveCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Mod(value = "neoshot", dist = Dist.CLIENT)
public class Neoshot {

    public static final KeyMapping SCREENSHOT_BINDING = new KeyMapping(
            "key.neoshot.screenshot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC);

    /**
     * Opens the Neoshot config screen when pressed. Default key: M.
     * Rebindable in Options → Controls → Neoshot.
     */
    public static final KeyMapping CONFIG_BINDING = new KeyMapping(
            "key.neoshot.config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KeyMapping.Category.MISC);

    public static final KeyMapping GALLERY_BINDING = new KeyMapping(
            "key.neoshot.gallery",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            KeyMapping.Category.MISC);

    private static CaptureTask task;
    private static boolean pendingRestore = false;

    private static void printFileLink(Path path) {
        MutableComponent text = Component.literal(path.toFile().getName()).withStyle(ChatFormatting.UNDERLINE);
        text.setStyle(text.getStyle().withClickEvent(new ClickEvent.OpenFile(path)));
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.getChat().addClientSystemMessage(Component.translatable("screenshot.success", text)));
    }

    public Neoshot(IEventBus modBus, ModContainer container) {
        Config.load();
        ScreenshotSaveCallback.register(Neoshot::printFileLink);

        modBus.addListener(this::onRegisterKeyMappings);

        // Listen for the M key (config open) every client tick.
        // ClientTickEvent fires on the game event bus, not the mod bus.
        NeoForge.EVENT_BUS.addListener(this::onClientTick);

        // Wire up the "Config" button in the NeoForge Mods menu.
        // If Cloth Config is installed, open the Cloth Config screen; otherwise show a
        // friendly "please install Cloth Config" screen.
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (IConfigScreenFactory) (mc, parent) -> openConfigScreen(parent));
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(SCREENSHOT_BINDING);
        event.register(CONFIG_BINDING);
        event.register(GALLERY_BINDING);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Only trigger when in-game (not already on a screen), so we don't yank
        // the user out of a GUI they're already in.
        if (mc.player != null && mc.screen == null) {
            while (CONFIG_BINDING.consumeClick()) {
                mc.setScreen(openConfigScreen(null));
            }
        }
    }

    /**
     * Shared entry point used by both the Mods-menu Config button and the M keybind.
     * Returns the Cloth Config screen if Cloth Config is installed, otherwise
     * a screen telling the user to install it.
     */
    public static Screen openConfigScreen(Screen parent) {
        if (isClothConfigLoaded()) {
            return new me.fring.neoshot.config.ClothConfigBridge().create(parent);
        } else {
            return new me.fring.neoshot.config.InstallClothConfigScreen(parent);
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
            // Resize the framebuffer BEFORE the first render tick, so the entire
            // frame renders at 4K consistently. Doing the resize mid-frame (inside
            // onRenderTick) causes the "TV signal" corruption.
            if (!((me.fring.neoshot.capture.CaptureTask) task).isDirectCapture()) {
                MinecraftInterface.refresh();
            }
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

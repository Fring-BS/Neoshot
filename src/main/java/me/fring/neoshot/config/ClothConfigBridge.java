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

package me.fring.neoshot.config;

import com.mojang.blaze3d.systems.RenderSystem;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClothConfigBridge {

    public Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setTitle(Component.translatable("neoshot.config.title"))
                .setSavingRunnable(Config::save)
                .setParentScreen(parent);
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("neoshot.config.category"));

        category.addEntry(entryBuilder.startStrField(Component.translatable("neoshot.config.custom_file_name"), Config.CUSTOM_FILE_NAME)
                .setTooltip(Component.translatable("neoshot.config.custom_file_name.tooltip"))
                .setDefaultValue("huge_%time%")
                .setSaveConsumer(b -> Config.CUSTOM_FILE_NAME = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("neoshot.config.override_screenshot_key"), Config.OVERRIDE_SCREENSHOT_KEY)
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.OVERRIDE_SCREENSHOT_KEY = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("neoshot.config.hide_hud"), Config.HIDE_HUD)
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.HIDE_HUD = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("neoshot.config.save_file"), Config.SAVE_FILE)
                .setDefaultValue(true)
                .setSaveConsumer(b -> Config.SAVE_FILE = b)
                .build());

        IntegerListEntry width = entryBuilder.startIntField(Component.translatable("neoshot.config.width"), Config.CAPTURE_WIDTH)
                .setDefaultValue(3840)
                .setMin(1)
                .setMax(Math.min(65535, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)))
                .setSaveConsumer(i -> Config.CAPTURE_WIDTH = i)
                .build();
        category.addEntry(width);

        IntegerListEntry height = entryBuilder.startIntField(Component.translatable("neoshot.config.height"), Config.CAPTURE_HEIGHT)
                .setDefaultValue(2160)
                .setMin(1)
                .setMax(Math.min(65535, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)))
                .setSaveConsumer(i -> Config.CAPTURE_HEIGHT = i)
                .build();
        category.addEntry(height);

        category.addEntry(new ScalingPresetEntry(220, width, height));

        category.addEntry(entryBuilder.startIntField(Component.translatable("neoshot.config.delay"), Config.CAPTURE_DELAY)
                .setTooltip(Component.translatable("neoshot.config.delay.tooltip"))
                .setDefaultValue(1)
                .setMin(1)
                .setSaveConsumer(i -> Config.CAPTURE_DELAY = i)
                .build());

        category.addEntry(entryBuilder.startEnumSelector(Component.translatable("neoshot.config.file_format"), FileFormat.class, Config.CAPTURE_FILE_FORMAT)
                .setDefaultValue(FileFormat.PNG)
                .setSaveConsumer(t -> Config.CAPTURE_FILE_FORMAT = t)
                .build());

        return builder.build();
    }
}

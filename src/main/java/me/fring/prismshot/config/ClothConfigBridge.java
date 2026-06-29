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

package me.fring.prismshot.config;

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
                .setTitle(Component.translatable("prismshot.config.title"))
                .setSavingRunnable(Config::save)
                .setParentScreen(parent);
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable("prismshot.config.category"));

        category.addEntry(entryBuilder.startStrField(Component.translatable("prismshot.config.custom_file_name"), Config.CUSTOM_FILE_NAME)
                .setTooltip(Component.translatable("prismshot.config.custom_file_name.tooltip"))
                .setDefaultValue("huge_%time%")
                .setSaveConsumer(b -> Config.CUSTOM_FILE_NAME = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("prismshot.config.override_screenshot_key"), Config.OVERRIDE_SCREENSHOT_KEY)
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.OVERRIDE_SCREENSHOT_KEY = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("prismshot.config.hide_hud"), Config.HIDE_HUD)
                .setDefaultValue(false)
                .setSaveConsumer(b -> Config.HIDE_HUD = b)
                .build());

        category.addEntry(entryBuilder.startBooleanToggle(Component.translatable("prismshot.config.save_file"), Config.SAVE_FILE)
                .setDefaultValue(true)
                .setSaveConsumer(b -> Config.SAVE_FILE = b)
                .build());

        IntegerListEntry width = entryBuilder.startIntField(Component.translatable("prismshot.config.width"), Config.CAPTURE_WIDTH)
                .setDefaultValue(3840)
                .setMin(1)
                .setMax(Math.min(65535, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)))
                .setSaveConsumer(i -> Config.CAPTURE_WIDTH = i)
                .build();
        category.addEntry(width);

        IntegerListEntry height = entryBuilder.startIntField(Component.translatable("prismshot.config.height"), Config.CAPTURE_HEIGHT)
                .setDefaultValue(2160)
                .setMin(1)
                .setMax(Math.min(65535, RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSizeForFormat(com.mojang.blaze3d.GpuFormat.RGBA8_UNORM)))
                .setSaveConsumer(i -> Config.CAPTURE_HEIGHT = i)
                .build();
        category.addEntry(height);

        category.addEntry(new ScalingPresetEntry(220, width, height));

        category.addEntry(entryBuilder.startIntField(Component.translatable("prismshot.config.delay"), Config.CAPTURE_DELAY)
                .setTooltip(Component.translatable("prismshot.config.delay.tooltip"))
                .setDefaultValue(1)
                .setMin(1)
                .setSaveConsumer(i -> Config.CAPTURE_DELAY = i)
                .build());

        category.addEntry(entryBuilder.startEnumSelector(Component.translatable("prismshot.config.file_format"), FileFormat.class, Config.CAPTURE_FILE_FORMAT)
                .setDefaultValue(FileFormat.PNG)
                .setSaveConsumer(t -> Config.CAPTURE_FILE_FORMAT = t)
                .build());

        ConfigCategory galleryCategory = builder.getOrCreateCategory(Component.literal("Gallery"));

        galleryCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enable Gallery"), Config.GALLERY_ENABLED)
                .setDefaultValue(true)
                .setSaveConsumer(b -> Config.GALLERY_ENABLED = b)
                .build());

        galleryCategory.addEntry(entryBuilder.startIntField(Component.literal("Thumbnail Size"), Config.GALLERY_THUMBNAIL_SIZE)
                .setDefaultValue(96)
                .setMin(32)
                .setMax(256)
                .setSaveConsumer(i -> Config.GALLERY_THUMBNAIL_SIZE = i)
                .build());

        galleryCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Cache Thumbnails"), Config.GALLERY_CACHE_ENABLED)
                .setDefaultValue(true)
                .setSaveConsumer(b -> Config.GALLERY_CACHE_ENABLED = b)
                .build());

        galleryCategory.addEntry(entryBuilder.startIntField(Component.literal("Max Cache (MB)"), Config.GALLERY_MAX_CACHE_MB)
                .setDefaultValue(256)
                .setMin(16)
                .setMax(4096)
                .setSaveConsumer(i -> Config.GALLERY_MAX_CACHE_MB = i)
                .build());

        galleryCategory.addEntry(entryBuilder.startStrField(Component.literal("Default Sort"), Config.GALLERY_DEFAULT_SORT)
                .setDefaultValue("NEWEST_FIRST")
                .setSaveConsumer(s -> Config.GALLERY_DEFAULT_SORT = s)
                .build());

        return builder.build();
    }
}

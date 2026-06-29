/*
 * MIT License
 *
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

package me.fring.prismshot.gallery;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Represents a single screenshot file on disk.
 * Stores basic file metadata and lazily loads image dimensions when needed.
 */
public class ScreenshotEntry {

    private final Path file;
    private final long size;
    private final long modifiedTime;
    private int width = -1;
    private int height = -1;
    private boolean dimensionsLoaded = false;
    private boolean favorite = false;

    public ScreenshotEntry(Path file, long size, long modifiedTime) {
        this.file = file;
        this.size = size;
        this.modifiedTime = modifiedTime;
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return file.getFileName().toString();
    }

    public long getSize() {
        return size;
    }

    public long getModifiedTime() {
        return modifiedTime;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public int getWidth() {
        if (!dimensionsLoaded) {
            loadDimensions();
        }
        return width;
    }

    public int getHeight() {
        if (!dimensionsLoaded) {
            loadDimensions();
        }
        return height;
    }

    public boolean isDimensionsLoaded() {
        return dimensionsLoaded;
    }

    /**
     * Reads PNG header bytes (16-23) to extract width and height without loading the full image.
     * This is fast and uses minimal memory.
     */
    private void loadDimensions() {
        dimensionsLoaded = true;
        try (InputStream is = Files.newInputStream(file)) {
            byte[] header = new byte[24];
            int read = is.read(header);
            if (read >= 24 && header[0] == (byte) 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
            }
        } catch (IOException ignored) {
        }
    }

    public String getResolutionString() {
        int w = getWidth();
        int h = getHeight();
        if (w > 0 && h > 0) {
            return w + "x" + h;
        }
        return "Unknown";
    }

    public String getSizeString() {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    /**
     * Refreshes file metadata (e.g. after rename or modification).
     */
    public void refresh() {
        try {
            FileTime time = Files.getLastModifiedTime(file);
            // modifiedTime is already set, but we could update it here
        } catch (IOException ignored) {
        }
    }
}

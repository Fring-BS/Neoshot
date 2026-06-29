/*
 * MIT License
 *
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

package me.fring.neoshot.gallery;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores favorite screenshot paths in a lightweight JSON file.
 * Does NOT modify the original screenshot files.
 * Thread-safe for concurrent read/write from the render and background threads.
 */
public class FavoritesStore {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("neoshot/favorites.json");
    private static final Set<String> FAVORITES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static volatile boolean loaded = false;

    /**
     * Loads favorites from disk. Called once on startup.
     */
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    Set<String> set = new Gson().fromJson(reader, new TypeToken<Set<String>>(){}.getType());
                    if (set != null) {
                        FAVORITES.addAll(set);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Checks if a file is marked as favorite.
     */
    public static boolean isFavorite(Path file) {
        load();
        return FAVORITES.contains(file.toAbsolutePath().toString());
    }

    /**
     * Toggles the favorite status of a file and persists to disk.
     */
    public static void toggle(Path file) {
        load();
        String key = file.toAbsolutePath().toString();
        if (FAVORITES.contains(key)) {
            FAVORITES.remove(key);
        } else {
            FAVORITES.add(key);
        }
        save();
    }

    /**
     * Persists the current favorites to the JSON file.
     */
    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                new Gson().toJson(new HashSet<>(FAVORITES), writer);
            }
        } catch (IOException ignored) {
        }
    }
}

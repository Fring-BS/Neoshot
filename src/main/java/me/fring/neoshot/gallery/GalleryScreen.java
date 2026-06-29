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

import me.fring.neoshot.config.Config;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * In-game screenshot gallery with thumbnail grid, search, sort, favorites, and file actions.
 * <p>
 * Features:
 * - Virtualized grid with async thumbnail loading
 * - Search by filename (live filtering)
 * - Sort by date, name, or resolution
 * - Favorites with persistent storage
 * - Actions: open, open folder, rename, delete (with confirmation dialog), copy path, toggle favorite
 * - Hover effects with favorite icon overlay
 * - Graceful error handling for corrupted/missing files
 */
public class GalleryScreen extends Screen {

    private static final int TOP_BAR_HEIGHT = 36;
    private static final int BOTTOM_BAR_HEIGHT = 80;
    private static final int PADDING = 10;
    private static final int THUMB_LABEL_HEIGHT = 22;

    private final List<ScreenshotEntry> allScreenshots = new ArrayList<>();
    private final List<ScreenshotEntry> filteredScreenshots = new ArrayList<>();
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private double scrollOffset = 0;
    private SortMode sortMode;
    private String searchText = "";
    private boolean favoritesOnly = false;
    private boolean needsRebuild = false;

    private EditBox searchBox;
    private EditBox renameBox;
    private Button sortButton;
    private Button favoritesButton;
    private Button openButton;
    private Button folderButton;
    private Button renameButton;
    private Button deleteButton;
    private Button copyButton;
    private Button favButton;

    private boolean renaming = false;

    // Confirmation dialog state
    private enum DialogType { NONE, DELETE, REFRESH }
    private DialogType activeDialog = DialogType.NONE;

    private int gridX, gridY, gridWidth, gridHeight;
    private int columns, cellWidth, cellHeight, thumbSize;
    private Path screenshotsDir;

    public GalleryScreen() {
        super(Component.translatable("neoshot.gallery.title"));
    }

    @Override
    protected void init() {
        screenshotsDir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        sortMode = SortMode.fromName(Config.GALLERY_DEFAULT_SORT);
        FavoritesStore.load();
        ThumbnailManager.init();

        thumbSize = Math.max(128, Config.GALLERY_THUMBNAIL_SIZE);
        cellWidth = thumbSize + PADDING;
        cellHeight = thumbSize + THUMB_LABEL_HEIGHT;
        gridX = PADDING;
        gridY = TOP_BAR_HEIGHT + PADDING;
        gridWidth = width - 2 * PADDING;
        gridHeight = height - TOP_BAR_HEIGHT - BOTTOM_BAR_HEIGHT - 2 * PADDING;
        columns = Math.max(1, gridWidth / cellWidth);

        // Search box
        searchBox = new EditBox(font, PADDING, 8, 220, 20, Component.literal("Search"));
        searchBox.setHint(Component.literal("Search screenshots..."));
        searchBox.setValue(searchText);
        addRenderableWidget(searchBox);

        // Sort button
        sortButton = Button.builder(Component.literal("\u2195 " + sortMode.getDisplayName()), b -> cycleSort())
                .pos(PADDING + 230, 8).width(160).build();
        addRenderableWidget(sortButton);

        // Favorites filter button
        favoritesButton = Button.builder(Component.literal(favoritesOnly ? "\u2665 Favorites: ON" : "\u2661 Favorites: OFF"), b -> toggleFavoritesFilter())
                .pos(PADDING + 400, 8).width(140).build();
        addRenderableWidget(favoritesButton);

        // Action buttons (bottom bar)
        int buttonY = height - BOTTOM_BAR_HEIGHT + 45;
        int buttonW = 90;
        int gap = 5;
        int totalW = 6 * buttonW + 5 * gap;
        int bx = (width - totalW) / 2;

        openButton = Button.builder(Component.literal("Open"), b -> openSelected()).pos(bx, buttonY).width(buttonW).build();
        addRenderableWidget(openButton);
        bx += buttonW + gap;

        folderButton = Button.builder(Component.literal("Folder"), b -> openFolder()).pos(bx, buttonY).width(buttonW).build();
        addRenderableWidget(folderButton);
        bx += buttonW + gap;

        renameButton = Button.builder(Component.literal("Rename"), b -> startRename()).pos(bx, buttonY).width(buttonW).build();
        addRenderableWidget(renameButton);
        bx += buttonW + gap;

        deleteButton = Button.builder(Component.literal("Delete"), b -> showDeleteDialog()).pos(bx, buttonY).width(buttonW).build();
        addRenderableWidget(deleteButton);
        bx += buttonW + gap;

        copyButton = Button.builder(Component.literal("Copy Path"), b -> copyPath()).pos(bx, buttonY).width(buttonW).build();
        addRenderableWidget(copyButton);
        bx += buttonW + gap;

        favButton = Button.builder(Component.literal("\u2665 Favorite"), b -> toggleFavorite()).pos(bx, buttonY).width(buttonW).build();
        addRenderableWidget(favButton);

        loadScreenshots();
    }

    private void loadScreenshots() {
        allScreenshots.clear();
        try {
            if (Files.exists(screenshotsDir)) {
                try (var stream = Files.list(screenshotsDir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> isImageFile(p.getFileName().toString()))
                          .forEach(p -> {
                              try {
                                  long size = Files.size(p);
                                  long modTime = Files.getLastModifiedTime(p).toMillis();
                                  ScreenshotEntry entry = new ScreenshotEntry(p, size, modTime);
                                  entry.setFavorite(FavoritesStore.isFavorite(p));
                                  allScreenshots.add(entry);
                              } catch (IOException ignored) {
                              }
                          });
                }
            }
        } catch (IOException ignored) {
        }
        needsRebuild = true;
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".tga");
    }

    private void cycleSort() {
        sortMode = sortMode.next();
        sortButton.setMessage(Component.literal("\u2195 " + sortMode.getDisplayName()));
        needsRebuild = true;
    }

    private void toggleFavoritesFilter() {
        favoritesOnly = !favoritesOnly;
        favoritesButton.setMessage(Component.literal(favoritesOnly ? "\u2665 Favorites: ON" : "\u2661 Favorites: OFF"));
        needsRebuild = true;
    }

    private void rebuildFilteredList() {
        filteredScreenshots.clear();
        String search = searchText.toLowerCase();
        for (ScreenshotEntry entry : allScreenshots) {
            if (favoritesOnly && !entry.isFavorite()) continue;
            if (!search.isEmpty() && !entry.getFileName().toLowerCase().contains(search)) continue;
            filteredScreenshots.add(entry);
        }
        switch (sortMode) {
            case NEWEST_FIRST -> filteredScreenshots.sort((a, b) -> Long.compare(b.getModifiedTime(), a.getModifiedTime()));
            case OLDEST_FIRST -> filteredScreenshots.sort((a, b) -> Long.compare(a.getModifiedTime(), b.getModifiedTime()));
            case NAME_AZ -> filteredScreenshots.sort((a, b) -> a.getFileName().compareToIgnoreCase(b.getFileName()));
            case NAME_ZA -> filteredScreenshots.sort((a, b) -> b.getFileName().compareToIgnoreCase(a.getFileName()));
            case RESOLUTION -> filteredScreenshots.sort((a, b) -> Integer.compare(b.getWidth() * b.getHeight(), a.getWidth() * a.getHeight()));
        }
        if (selectedIndex >= filteredScreenshots.size()) selectedIndex = -1;
        clampScroll();
    }

    private void clampScroll() {
        int totalRows = (int) Math.ceil((double) filteredScreenshots.size() / columns);
        int contentHeight = totalRows * cellHeight;
        double maxScroll = Math.max(0, contentHeight - gridHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        String currentSearch = searchBox.getValue();
        if (!currentSearch.equals(searchText)) {
            searchText = currentSearch;
            needsRebuild = true;
        }
        if (needsRebuild) {
            rebuildFilteredList();
            needsRebuild = false;
        }
        ThumbnailManager.tick();

        // Background gradient
        graphics.fill(0, 0, width, height, 0xDD0F0F0F);
        // Top bar
        graphics.fill(0, 0, width, TOP_BAR_HEIGHT, 0xF0202020);
        graphics.fill(0, TOP_BAR_HEIGHT, width, TOP_BAR_HEIGHT + 1, 0xFF404040);
        // Bottom bar
        graphics.fill(0, height - BOTTOM_BAR_HEIGHT, width, height, 0xF0202020);
        graphics.fill(0, height - BOTTOM_BAR_HEIGHT - 1, width, height - BOTTOM_BAR_HEIGHT, 0xFF404040);
        // Grid area
        graphics.fill(gridX, gridY, gridX + gridWidth, gridY + gridHeight, 0x80101010);

        // Title
        graphics.centeredText(font, Component.literal("\u00a7f\u00a7lScreenshot Gallery"), width / 2, 12, 0xFFFFFF);

        drawGrid(graphics, mouseX, mouseY);
        drawBottomBar(graphics);

        boolean hasSelection = selectedIndex >= 0 && selectedIndex < filteredScreenshots.size();
        openButton.active = hasSelection && !renaming && activeDialog == DialogType.NONE;
        renameButton.active = hasSelection && !renaming && activeDialog == DialogType.NONE;
        deleteButton.active = hasSelection && activeDialog == DialogType.NONE;
        copyButton.active = hasSelection && activeDialog == DialogType.NONE;
        favButton.active = hasSelection && activeDialog == DialogType.NONE;
        sortButton.active = activeDialog == DialogType.NONE;
        favoritesButton.active = activeDialog == DialogType.NONE;
        searchBox.active = activeDialog == DialogType.NONE;

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (renaming && renameBox != null) {
            renameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }

        // Draw confirmation dialog on top
        if (activeDialog != DialogType.NONE) {
            drawDialog(graphics, mouseX, mouseY);
        }
    }

    private void drawGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (filteredScreenshots.isEmpty()) {
            graphics.centeredText(font, Component.literal("\u00a77No screenshots found"), width / 2, gridY + gridHeight / 2, 0xAAAAAA);
            return;
        }

        int firstRow = (int) (scrollOffset / cellHeight);
        int lastRow = (int) Math.ceil((scrollOffset + gridHeight) / cellHeight);
        int gridOffsetX = (gridWidth - columns * cellWidth) / 2;

        graphics.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);

        hoveredIndex = -1;

        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= filteredScreenshots.size()) break;

                ScreenshotEntry entry = filteredScreenshots.get(index);
                int cellX = gridX + gridOffsetX + col * cellWidth;
                int cellY = gridY + row * cellHeight - (int) scrollOffset;
                if (cellY + cellHeight < gridY || cellY > gridY + gridHeight) continue;

                int thumbX = cellX + (cellWidth - thumbSize) / 2;
                int thumbY = cellY;

                boolean isHovered = mouseX >= thumbX && mouseX < thumbX + thumbSize
                        && mouseY >= thumbY && mouseY < thumbY + thumbSize;
                boolean isSelected = index == selectedIndex;
                if (isHovered) hoveredIndex = index;

                // Thumbnail background
                int bgColor = isHovered ? 0xFF303030 : 0xFF1A1A1A;
                graphics.fill(thumbX - 2, thumbY - 2, thumbX + thumbSize + 2, thumbY + thumbSize + 2, bgColor);

                // Request and draw thumbnail
                ThumbnailManager.requestThumbnail(entry.getFile());
                Identifier texId = ThumbnailManager.getTexture(entry.getFile());
                if (texId != null) {
                    graphics.blit(texId, thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0f, 1f, 0f, 1f);
                } else {
                    graphics.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0xFF2A2A2A);
                    graphics.centeredText(font, Component.literal("..."), thumbX + thumbSize / 2, thumbY + thumbSize / 2 - 4, 0x888888);
                }

                // Selection border
                if (isSelected) {
                    int borderCol = 0xFF5599FF;
                    graphics.fill(thumbX - 3, thumbY - 3, thumbX + thumbSize + 3, thumbY - 2, borderCol);
                    graphics.fill(thumbX - 3, thumbY + thumbSize + 2, thumbX + thumbSize + 3, thumbY + thumbSize + 3, borderCol);
                    graphics.fill(thumbX - 3, thumbY - 2, thumbX - 2, thumbY + thumbSize + 2, borderCol);
                    graphics.fill(thumbX + thumbSize + 2, thumbY - 2, thumbX + thumbSize + 3, thumbY + thumbSize + 2, borderCol);
                } else if (isHovered) {
                    int borderCol = 0xFF888888;
                    graphics.fill(thumbX - 3, thumbY - 3, thumbX + thumbSize + 3, thumbY - 2, borderCol);
                    graphics.fill(thumbX - 3, thumbY + thumbSize + 2, thumbX + thumbSize + 3, thumbY + thumbSize + 3, borderCol);
                    graphics.fill(thumbX - 3, thumbY - 2, thumbX - 2, thumbY + thumbSize + 2, borderCol);
                    graphics.fill(thumbX + thumbSize + 2, thumbY - 2, thumbX + thumbSize + 3, thumbY + thumbSize + 2, borderCol);
                }

                // Favorite heart - show on hover at top-right, always show if favorited
                if (entry.isFavorite()) {
                    graphics.text(font, Component.literal("\u2665"), thumbX + thumbSize - 10, thumbY + 2, 0xFFFF4444);
                } else if (isHovered) {
                    graphics.text(font, Component.literal("\u2661"), thumbX + thumbSize - 10, thumbY + 2, 0x88FFFFFF);
                }

                // Filename below thumbnail
                String name = entry.getFileName();
                if (font.width(name) > cellWidth - 2) {
                    while (font.width(name + "...") > cellWidth - 2 && !name.isEmpty()) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name += "...";
                }
                int nameColor = isSelected ? 0xFFFFFF : (isHovered ? 0xDDDDDD : 0xAAAAAA);
                graphics.centeredText(font, Component.literal(name), cellX + cellWidth / 2, thumbY + thumbSize + 4, nameColor);
            }
        }

        graphics.disableScissor();

        // Scroll bar
        int totalRows = (int) Math.ceil((double) filteredScreenshots.size() / columns);
        int contentHeight = totalRows * cellHeight;
        if (contentHeight > gridHeight) {
            int barX = gridX + gridWidth - 4;
            int barH = Math.max(20, (int) ((double) gridHeight / contentHeight * gridHeight));
            int barY = gridY + (int) ((double) scrollOffset / contentHeight * (gridHeight - barH));
            graphics.fill(barX, gridY, barX + 4, gridY + gridHeight, 0x40808080);
            graphics.fill(barX, barY, barX + 4, barY + barH, 0xFF606060);
        }
    }

    private void drawBottomBar(GuiGraphicsExtractor graphics) {
        int y = height - BOTTOM_BAR_HEIGHT + 8;
        if (selectedIndex >= 0 && selectedIndex < filteredScreenshots.size()) {
            ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(entry.getModifiedTime()));
            graphics.text(font, Component.literal("\u00a7f" + entry.getFileName()), PADDING, y, 0xFFFFFF);
            graphics.text(font, Component.literal("\u00a77" + entry.getResolutionString() + "  \u00a7f|  \u00a77" + entry.getSizeString() + "  \u00a7f|  \u00a77" + dateStr), PADDING, y + 12, 0xAAAAAA);
        } else {
            graphics.centeredText(font, Component.literal("\u00a77No screenshot selected"), width / 2, y + 4, 0x888888);
        }
        graphics.centeredText(font, Component.literal("\u00a78" + filteredScreenshots.size() + " screenshots"), width / 2, height - 14, 0x888888);
    }

    private void drawDialog(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Dark overlay
        graphics.fill(0, 0, width, height, 0x80000000);

        int dialogW = 300;
        int dialogH = 110;
        int dialogX = (width - dialogW) / 2;
        int dialogY = (height - dialogH) / 2;

        // Dialog background
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xF0252525);
        graphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 1, 0xFF5599FF);
        graphics.fill(dialogX, dialogY + dialogH - 1, dialogX + dialogW, dialogY + dialogH, 0xFF5599FF);
        graphics.fill(dialogX, dialogY, dialogX + 1, dialogY + dialogH, 0xFF5599FF);
        graphics.fill(dialogX + dialogW - 1, dialogY, dialogX + dialogW, dialogY + dialogH, 0xFF5599FF);

        String message = activeDialog == DialogType.DELETE ? "Delete this screenshot?" : "Refresh gallery?";
        graphics.centeredText(font, Component.literal("\u00a7f\u00a7l" + message), width / 2, dialogY + 18, 0xFFFFFF);

        if (activeDialog == DialogType.DELETE && selectedIndex >= 0 && selectedIndex < filteredScreenshots.size()) {
            ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
            String name = entry.getFileName();
            if (font.width(name) > dialogW - 20) {
                while (font.width(name + "...") > dialogW - 20 && !name.isEmpty()) {
                    name = name.substring(0, name.length() - 1);
                }
                name += "...";
            }
            graphics.centeredText(font, Component.literal("\u00a7c" + name), width / 2, dialogY + 36, 0xFFAAAA);
            graphics.centeredText(font, Component.literal("\u00a77This action cannot be undone"), width / 2, dialogY + 50, 0xAAAAAA);
        }

        // Yes/No buttons
        int btnY = dialogY + 70;
        int btnW = 80;
        graphics.centeredText(font, Component.literal("\u00a7a[ Yes ]   \u00a7c[ No ]"), width / 2, btnY + 4, 0xFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (activeDialog != DialogType.NONE) {
            // Click on dialog
            double mx = event.x();
            double my = event.y();
            int dialogW = 300;
            int dialogH = 110;
            int dialogX = (width - dialogW) / 2;
            int dialogY = (height - dialogH) / 2;
            int btnY = dialogY + 70;
            int btnW = 80;

            // Yes button
            if (mx >= width / 2 - btnW - 5 && mx < width / 2 - 5 && my >= btnY && my < btnY + 20) {
                confirmDialog();
                return true;
            }
            // No button
            if (mx >= width / 2 + 5 && mx < width / 2 + btnW + 5 && my >= btnY && my < btnY + 20) {
                activeDialog = DialogType.NONE;
                return true;
            }
            return true; // consume all clicks when dialog is open
        }

        if (renaming) {
            return super.mouseClicked(event, doubleClick);
        }

        double mx = event.x();
        double my = event.y();

        // Check grid clicks
        if (mx >= gridX && mx < gridX + gridWidth && my >= gridY && my < gridY + gridHeight) {
            int gridOffsetX = (gridWidth - columns * cellWidth) / 2;
            int col = (int) ((mx - gridX - gridOffsetX) / cellWidth);
            int row = (int) ((my - gridY + scrollOffset) / cellHeight);
            if (col >= 0 && col < columns) {
                int index = row * columns + col;
                if (index >= 0 && index < filteredScreenshots.size()) {
                    selectedIndex = index;
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= scrollY * cellHeight;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeDialog != DialogType.NONE) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                activeDialog = DialogType.NONE;
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER) {
                confirmDialog();
                return true;
            }
            return true; // consume all keys when dialog is open
        }

        if (renaming && renameBox != null) {
            if (event.key() == GLFW.GLFW_KEY_ENTER) {
                confirmRename();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            return renameBox.keyPressed(event) || super.keyPressed(event);
        }

        if (super.keyPressed(event)) return true;

        if (selectedIndex >= 0 && selectedIndex < filteredScreenshots.size()) {
            switch (event.key()) {
                case GLFW.GLFW_KEY_ENTER -> { openSelected(); return true; }
                case GLFW.GLFW_KEY_DELETE -> { showDeleteDialog(); return true; }
                case GLFW.GLFW_KEY_F -> { toggleFavorite(); return true; }
                case GLFW.GLFW_KEY_R -> { startRename(); return true; }
                case GLFW.GLFW_KEY_C -> { copyPath(); return true; }
            }
        }
        return false;
    }

    // === Actions ===

    private void showDeleteDialog() {
        if (selectedIndex < 0 || selectedIndex >= filteredScreenshots.size()) return;
        activeDialog = DialogType.DELETE;
    }

    private void confirmDialog() {
        if (activeDialog == DialogType.DELETE) {
            if (selectedIndex >= 0 && selectedIndex < filteredScreenshots.size()) {
                ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
                ThumbnailManager.release(entry.getFile());
                try {
                    Files.delete(entry.getFile());
                } catch (IOException ignored) {
                }
                allScreenshots.remove(entry);
                selectedIndex = Math.min(selectedIndex, filteredScreenshots.size() - 2);
                needsRebuild = true;
            }
        }
        activeDialog = DialogType.NONE;
    }

    private void openSelected() {
        if (selectedIndex < 0 || selectedIndex >= filteredScreenshots.size()) return;
        ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
        Util.getPlatform().openFile(entry.getFile().toFile());
    }

    private void openFolder() {
        Util.getPlatform().openFile(screenshotsDir.toFile());
    }

    private void copyPath() {
        if (selectedIndex < 0 || selectedIndex >= filteredScreenshots.size()) return;
        ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
        String path = entry.getFile().toAbsolutePath().toString();
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(path), null);
            Minecraft.getInstance().gui.getChat().addClientSystemMessage(
                    Component.literal("\u00a7a[Neoshot] \u00a7fPath copied: " + path));
        } catch (Exception ignored) {
        }
    }

    private void toggleFavorite() {
        if (selectedIndex < 0 || selectedIndex >= filteredScreenshots.size()) return;
        ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
        FavoritesStore.toggle(entry.getFile());
        entry.setFavorite(!entry.isFavorite());
    }

    private void startRename() {
        if (selectedIndex < 0 || selectedIndex >= filteredScreenshots.size()) return;
        ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
        String name = entry.getFileName();
        String baseName = name;
        int dot = name.lastIndexOf('.');
        if (dot > 0) baseName = name.substring(0, dot);

        renaming = true;
        renameBox = new EditBox(font, PADDING, height - BOTTOM_BAR_HEIGHT + 8, width - 2 * PADDING, 20, Component.literal("Rename"));
        renameBox.setValue(baseName);
        renameBox.setFocused(true);
    }

    private void confirmRename() {
        if (!renaming || renameBox == null || selectedIndex < 0 || selectedIndex >= filteredScreenshots.size()) {
            cancelRename();
            return;
        }
        ScreenshotEntry entry = filteredScreenshots.get(selectedIndex);
        String newName = renameBox.getValue().trim();
        if (newName.isEmpty()) {
            cancelRename();
            return;
        }
        String oldName = entry.getFileName();
        String extension = "";
        int dot = oldName.lastIndexOf('.');
        if (dot > 0) extension = oldName.substring(dot);
        Path newPath = entry.getFile().resolveSibling(newName + extension);
        try {
            if (!Files.exists(newPath)) {
                Files.move(entry.getFile(), newPath, StandardCopyOption.ATOMIC_MOVE);
                ThumbnailManager.release(entry.getFile());
                loadScreenshots();
            }
        } catch (IOException ignored) {
        }
        cancelRename();
    }

    private void cancelRename() {
        renaming = false;
        renameBox = null;
    }

    @Override
    public void onClose() {
        cancelRename();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.chenweikeng.mcparks.showtimes;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;

/**
 * Snapshot of the "Show Times" chest GUI: time column headers, day row labels,
 * and populated show cells. Built by reading the container menu's slot 0..53
 * once per render.
 *
 * <p>The mod detects the screen by its literal title {@code "Show Times"} so
 * any shovel-item grid with that title is handled the same way — future
 * pages reached via the "Next" arrow get rebuilt on open.
 */
public final class ShowTimesGrid {

    public static final int COLS = 9;
    public static final int ROWS = 6;
    public static final int GRID_SIZE = COLS * ROWS;
    /** Bottom-left slot — populated with an Arrow item ("Prev") on pages 2+. */
    public static final int SLOT_PREV = 45;
    /** Bottom-right slot — populated with an Arrow item ("Next") on all but the last page. */
    public static final int SLOT_NEXT = 53;
    public static final String SCREEN_TITLE = "Show Times";

    private final String[] timeHeaders = new String[COLS];
    private final String[] dayLabels = new String[ROWS];
    private final List<Cell> cells = new ArrayList<>();
    private boolean prevPageButton;
    private boolean nextPageButton;

    private ShowTimesGrid() {}

    public String timeHeader(int col) { return col >= 0 && col < COLS ? timeHeaders[col] : null; }
    public String dayLabel(int row) { return row >= 0 && row < ROWS ? dayLabels[row] : null; }
    public List<Cell> cells() { return cells; }
    public boolean isEmpty() { return cells.isEmpty(); }

    /** True if this snapshot has a "Next" arrow at the bottom-right but no "Prev" — i.e. page 1. */
    public boolean isFirstPage() { return nextPageButton && !prevPageButton; }
    /** True if either pagination button is present (server splits the schedule across pages). */
    public boolean isPaginated() { return nextPageButton || prevPageButton; }
    public boolean hasNextPage() { return nextPageButton; }
    public boolean hasPrevPage() { return prevPageButton; }

    /** True if the given screen's title matches the Show Times board (strip color codes). */
    public static boolean matches(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> cs)) return false;
        String t = cs.getTitle().getString();
        return t != null && stripFormatting(t).trim().equalsIgnoreCase(SCREEN_TITLE);
    }

    /** Builds a grid snapshot from the currently-open container. Returns empty grid if no suitable screen is open. */
    public static ShowTimesGrid capture() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return new ShowTimesGrid();
        if (!matches(cs)) return new ShowTimesGrid();
        AbstractContainerMenu menu = cs.getMenu();
        ShowTimesGrid g = new ShowTimesGrid();
        int max = Math.min(menu.slots.size(), GRID_SIZE);
        for (int i = 0; i < max; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            int row = i / COLS;
            int col = i % COLS;
            String name = stack.getHoverName().getString();
            if (row == 0 && col >= 1 && col <= 7) {
                g.timeHeaders[col] = name;
            } else if (col == 0 && row >= 1 && row <= 4) {
                g.dayLabels[row] = name;
            } else if (row >= 1 && row <= 4 && col >= 1 && col <= 7) {
                if (stack.getItem() instanceof ShovelItem) {
                    g.cells.add(new Cell(row, col, name, stack.getDamageValue(), stack.getItem()));
                }
            } else if (i == SLOT_PREV && stack.getItem() instanceof ArrowItem) {
                g.prevPageButton = true;
            } else if (i == SLOT_NEXT && stack.getItem() instanceof ArrowItem) {
                g.nextPageButton = true;
            }
        }
        return g;
    }

    /** Strips §x color/format codes used by the server's TextComponent strings. */
    public static String stripFormatting(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00A7' && i + 1 < s.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    public record Cell(int row, int col, String showName, int damageId, Item item) {}
}

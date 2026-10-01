package dev.leopaul.colonyledger.client.gui;

import dev.leopaul.colonyledger.model.ColonyResourceSummary;
import dev.leopaul.colonyledger.network.RequestLedgerPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

public final class LedgerScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 300;

    private enum View { BUILDERS, SUPPLY }

    private record DisplayRow(String itemId, String displayName, int missing, int pending,
            Component details, String sources) {}

    private ColonyResourceSummary summary;
    private View view = View.BUILDERS;
    private EditBox search;
    private Button buildersTab;
    private Button supplyTab;
    private Button previousPage;
    private Button nextPage;
    private int scroll;
    private int refreshTicks;

    public LedgerScreen(ColonyResourceSummary summary) {
        super(Component.translatable("screen.colonyresourceledger.title"));
        this.summary = summary;
    }

    public void update(ColonyResourceSummary next) {
        this.summary = next;
        this.refreshTicks = 0;
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();
        int buttonWidth = (panelWidth() - 26) / 2;
        buildersTab = addRenderableWidget(plainButton(left + 10, top + 57, buttonWidth,
                Component.translatable("screen.colonyresourceledger.builders"), b -> switchView(View.BUILDERS)));
        supplyTab = addRenderableWidget(plainButton(left + 16 + buttonWidth, top + 57, buttonWidth,
                Component.translatable("screen.colonyresourceledger.supply"), b -> switchView(View.SUPPLY)));
        supplyTab.setTooltip(Tooltip.create(Component.translatable("screen.colonyresourceledger.supply_help")));
        buildersTab.active = view != View.BUILDERS;
        supplyTab.active = view != View.SUPPLY;
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left + 10, top + 84, panelWidth() - 20, 20,
                Component.translatable("screen.colonyresourceledger.search")));
        search.setHint(Component.translatable("screen.colonyresourceledger.search"));
        search.setTextShadow(false);
        search.setValue(query);
        search.setResponder(value -> scroll = 0);
        int footerY = top + panelHeight() - 26;
        previousPage = addRenderableWidget(plainButton(left + panelWidth() - 54, footerY, 20,
                Component.literal("<"), b -> scrollBy(-rowCapacity())));
        nextPage = addRenderableWidget(plainButton(left + panelWidth() - 30, footerY, 20,
                Component.literal(">"), b -> scrollBy(rowCapacity())));
    }

    private Button plainButton(int x, int y, int buttonWidth, Component label, Button.OnPress onPress) {
        return new Button(Button.builder(label, onPress).bounds(x, y, buttonWidth, 20)) {
            @Override
            public void renderString(GuiGraphics graphics, Font buttonFont, int color) {
                String text = fitText(buttonFont, getMessage().getString(), getWidth() - 6);
                graphics.drawString(buttonFont, text, getX() + (getWidth() - buttonFont.width(text)) / 2,
                        getY() + (getHeight() - buttonFont.lineHeight) / 2,
                        active ? 0xFF202020 : 0xFF777777, false);
            }
        };
    }

    private int panelWidth() { return Math.min(PANEL_WIDTH, width - 16); }
    private int panelHeight() { return Math.min(PANEL_HEIGHT, height - 16); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }
    private int listTop() { return panelTop() + 109; }
    private int listBottom() { return panelTop() + panelHeight() - 30; }
    private int rowHeight() { return view == View.SUPPLY ? 47 : 36; }
    private int rowCapacity() { return Math.max(1, (listBottom() - listTop()) / rowHeight()); }
    private void switchView(View next) {
        view = next;
        scroll = 0;
        buildersTab.active = view != View.BUILDERS;
        supplyTab.active = view != View.SUPPLY;
    }

    private void scrollBy(int amount) {
        int maxScroll = Math.max(0, visibleRows().size() - rowCapacity());
        scroll = Math.clamp(scroll + amount, 0, maxScroll);
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private void drawCenteredText(GuiGraphics graphics, Component text, int y, int color) {
        String fitted = fitText(font, text.getString(), panelWidth() - 20);
        graphics.drawString(font, fitted, (width - font.width(fitted)) / 2, y, color, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 100 && summary.colonyId() >= 0) {
            refreshTicks = 0;
            PacketDistributor.sendToServer(new RequestLedgerPayload(summary.colonyId()));
        }
    }

    private List<DisplayRow> visibleRows() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        List<DisplayRow> rows;
        if (view == View.SUPPLY) {
            rows = summary.supplyRequirements().stream().map(r -> new DisplayRow(r.itemId(), r.displayName(),
                    r.missing(), 0, Component.translatable("screen.colonyresourceledger.supply_row",
                    r.requiredTotal(), r.allocatedStock()), String.join(", ", r.usedBy()))).toList();
        } else {
            rows = summary.resources().stream().map(r -> new DisplayRow(r.itemId(), r.displayName(),
                    r.missing(), r.inTransit(), Component.translatable("screen.colonyresourceledger.row",
                    r.requiredTotal(), r.builderInventory(), r.colonyAvailable(), r.inTransit()),
                    r.requestSources().stream().map(s -> s.buildingName() + " / " + s.builderName()).distinct()
                            .reduce((a, b) -> a + ", " + b).orElse(""))).toList();
        }
        return rows.stream()
                .filter(r -> query.isBlank() || r.displayName().toLowerCase(Locale.ROOT).contains(query)
                        || r.itemId().toLowerCase(Locale.ROOT).contains(query)
                        || r.sources().toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Screen.render calls this before rendering widgets. The backdrop is already
        // rendered at the start of our render method, before any ledger content.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Apply the vanilla world backdrop and blur before drawing the entire UI.
        // Calling it from Screen.render would blur the panel and header only.
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();

        // The white inventory panel sits directly behind the search field and list.
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF4A4A4A);
        graphics.fill(left, top, right, bottom, 0xFFF3F3F3);
        graphics.fill(left + 4, top + 4, right - 4, top + 48, 0xFFE0E0E0);
        graphics.fill(left + 8, top + 105, right - 8, listBottom() + 1, 0xFFFFFFFF);
        graphics.renderOutline(left + 8, top + 105, panelWidth() - 16, listBottom() - top - 104, 0xFF9A9A9A);

        drawCenteredText(graphics, title, top + 12, 0xFF202020);
        drawCenteredText(graphics, Component.literal(summary.colonyName()), top + 27, 0xFF6B4B13);
        Component subtitle = view == View.SUPPLY
                ? Component.translatable(summary.supplyPlanLimited()
                        ? "screen.colonyresourceledger.supply_limited" : "screen.colonyresourceledger.supply_forecast")
                : Component.translatable("screen.colonyresourceledger.stats",
                        summary.activeBuilderCount(), summary.activeConstructionCount());
        drawCenteredText(graphics, subtitle, top + 39,
                view == View.SUPPLY && summary.supplyPlanLimited() ? 0xFF9A6800 : 0xFF555555);

        List<DisplayRow> rows = visibleRows();
        int capacity = rowCapacity();
        int maxScroll = Math.max(0, rows.size() - capacity);
        scroll = Math.clamp(scroll, 0, maxScroll);
        previousPage.active = scroll > 0;
        nextPage.active = scroll < maxScroll;
        super.render(graphics, mouseX, mouseY, partialTick);

        int y = listTop();
        int rowHeight = rowHeight();
        int rowRight = right - 20;
        DisplayRow hovered = null;
        graphics.enableScissor(left + 10, listTop(), rowRight, listBottom());

        for (int i = scroll; i < Math.min(rows.size(), scroll + capacity); i++) {
            DisplayRow row = rows.get(i);
            int color = row.missing() > 0 ? 0xFF9C2020 : row.pending() > 0 ? 0xFF9A6800 : 0xFF207A42;
            graphics.fill(left + 10, y, rowRight, y + rowHeight - 2, 0xFFF0F0F0);
            graphics.renderOutline(left + 10, y, panelWidth() - 30, rowHeight - 2, 0xFFD0D0D0);
            graphics.renderItem(itemPreview(row), left + 15, y + 9);
            Component provide = Component.translatable("screen.colonyresourceledger.provide", row.missing());
            int provideX = rowRight - 6 - font.width(provide);
            graphics.drawString(font, fitText(font, row.displayName(), provideX - left - 44),
                    left + 38, y + 6, 0xFF202020, false);
            graphics.drawString(font, provide, provideX, y + 6, color, false);
            graphics.drawString(font, fitText(font, row.details().getString(), rowRight - left - 44),
                    left + 38, y + 19, 0xFF555555, false);
            if (view == View.SUPPLY && !row.sources().isEmpty()) {
                graphics.drawString(font, fitText(font, row.sources(), rowRight - left - 44),
                        left + 38, y + 32, 0xFF777777, false);
            }
            if (mouseX >= left + 10 && mouseX < rowRight && mouseY >= y
                    && mouseY < Math.min(y + rowHeight - 2, listBottom())) {
                hovered = row;
            }
            y += rowHeight;
        }
        graphics.disableScissor();
        if (rows.isEmpty()) {
            drawCenteredText(graphics, Component.translatable(view == View.SUPPLY
                            ? "screen.colonyresourceledger.supply_empty" : "screen.colonyresourceledger.empty"),
                    listTop() + 10, 0xFF555555);
        }
        if (maxScroll > 0) {
            int trackHeight = listBottom() - listTop();
            int thumbHeight = Math.max(12, trackHeight * capacity / rows.size());
            int thumbY = listTop() + (trackHeight - thumbHeight) * scroll / maxScroll;
            graphics.fill(right - 17, listTop(), right - 11, listBottom(), 0xFFE0E0E0);
            graphics.fill(right - 17, thumbY, right - 11, thumbY + thumbHeight, 0xFF777777);
        }
        String range = rows.isEmpty() ? "0 / 0"
                : (scroll + 1) + " - " + Math.min(rows.size(), scroll + capacity) + " / " + rows.size();
        graphics.drawString(font, range, left + 10, bottom - 20, 0xFF555555, false);
        if (hovered != null) {
            Component tooltip = Component.literal(hovered.displayName()).append("\n").append(hovered.details())
                    .append("\n").append(Component.translatable("screen.colonyresourceledger.provide", hovered.missing()))
                    .append("\n").append(hovered.sources());
            graphics.renderTooltip(font, font.split(tooltip, Math.min(300, width - 24)), mouseX, mouseY);
        }
    }

    private ItemStack itemPreview(DisplayRow row) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(row.itemId())));
        } catch (IllegalArgumentException ignored) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (deltaY != 0 && mouseX >= panelLeft() && mouseX < panelLeft() + panelWidth()
                && mouseY >= listTop() && mouseY < listBottom()) {
            scrollBy(deltaY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override public boolean isPauseScreen() { return false; }
}

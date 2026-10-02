package dev.leopaul.colonyledger.client.gui;

import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.network.RequestJobMonitorPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class JobMonitorScreen extends Screen {
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 330;
    private static final int ROW_HEIGHT = 50;
    private static final int[] COLORS = {0xFF996510, 0xFF287A38, 0xFFAA2525};
    private JobMonitorSummary summary;
    private JobPeriod period;
    private EditBox search;
    private final Map<JobPeriod, Button> periods = new EnumMap<>(JobPeriod.class);
    private Button previous, next;
    private int scroll, refreshTicks;

    public JobMonitorScreen(JobMonitorSummary summary) {
        super(Component.translatable("screen.colonyresourceledger.jobs.title"));
        this.summary = summary;
        this.period = summary.period();
    }

    public void update(JobMonitorSummary nextSummary) {
        // Discard responses from a period that was selected before the current one.
        if (nextSummary.period() != period) return;
        summary = nextSummary;
        refreshTicks = 0;
    }

    private int panelWidth() { return Math.min(MAX_WIDTH, width - 16); }
    private int panelHeight() { return Math.min(MAX_HEIGHT, height - 16); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int listTop() { return top() + 109; }
    private int listBottom() { return top() + panelHeight() - 30; }
    private int capacity() { return Math.max(1, (listBottom() - listTop()) / ROW_HEIGHT); }

    @Override
    protected void init() {
        periods.clear();
        int buttonWidth = (panelWidth() - 36) / JobPeriod.values().length;
        int x = left() + 10;
        for (JobPeriod choice : JobPeriod.values()) {
            Button button = addRenderableWidget(button(x, top() + 57, buttonWidth,
                    Component.translatable(choice.translationKey()), b -> selectPeriod(choice)));
            button.active = choice != period;
            button.setTooltip(Tooltip.create(Component.translatable("screen.colonyresourceledger.jobs.help")));
            periods.put(choice, button);
            x += buttonWidth + 4;
        }
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left() + 10, top() + 84, panelWidth() - 20, 20,
                Component.translatable("screen.colonyresourceledger.search")));
        search.setHint(Component.translatable("screen.colonyresourceledger.search"));
        search.setTextShadow(false);
        search.setValue(query);
        search.setResponder(value -> scroll = 0);
        previous = addRenderableWidget(button(left() + panelWidth() - 54, top() + panelHeight() - 26, 20,
                Component.literal("<"), b -> scrollBy(-capacity())));
        next = addRenderableWidget(button(left() + panelWidth() - 30, top() + panelHeight() - 26, 20,
                Component.literal(">"), b -> scrollBy(capacity())));
    }

    private Button button(int x, int y, int w, Component label, Button.OnPress onPress) {
        return new Button(Button.builder(label, onPress).bounds(x, y, w, 20)) {
            @Override public void renderString(GuiGraphics graphics, Font font, int color) {
                String text = fit(font, getMessage().getString(), getWidth() - 6);
                graphics.drawString(font, text, getX() + (getWidth() - font.width(text)) / 2,
                        getY() + (getHeight() - font.lineHeight) / 2, active ? 0xFF202020 : 0xFF777777, false);
            }
        };
    }

    private void selectPeriod(JobPeriod selected) {
        period = selected;
        scroll = 0;
        periods.forEach((choice, button) -> button.active = choice != selected);
        requestUpdate();
    }
    private void requestUpdate() {
        refreshTicks = 0;
        PacketDistributor.sendToServer(new RequestJobMonitorPayload(summary.colonyId(), period, false));
    }
    @Override public void tick() { if (++refreshTicks >= 100) requestUpdate(); }

    private List<CitizenJobRow> visibleRows() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        return summary.citizens().stream().filter(row -> query.isBlank()
                || row.name().toLowerCase(Locale.ROOT).contains(query)
                || Component.translatable(row.jobTranslationKey()).getString().toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void scrollBy(int delta) { scroll = Math.clamp(scroll + delta, 0, Math.max(0, visibleRows().size() - capacity())); }
    private static String fit(Font font, String text, int w) {
        return font.width(text) <= w ? text : font.plainSubstrByWidth(text, Math.max(0, w - font.width("..."))) + "...";
    }
    private void centered(GuiGraphics graphics, Component text, int y, int color) {
        String fitted = fit(font, text.getString(), panelWidth() - 20);
        graphics.drawString(font, fitted, (width - font.width(fitted)) / 2, y, color, false);
    }
    private static String days(long ticks) { return String.format(Locale.ROOT, "%.3f", ticks / 24_000.0); }
    private static String percent(int tenths) { return String.format(Locale.ROOT, "%.1f%%", tenths / 10.0); }
    private static Component state(ObservedJobStatus status) {
        return Component.translatable("screen.colonyresourceledger.jobs." + status.name().toLowerCase(Locale.ROOT));
    }

    // Screen.render would otherwise blur the panel before drawing its widgets.
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = left(), top = top(), right = left + panelWidth(), bottom = top + panelHeight();
        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xFF4A4A4A);
        graphics.fill(left, top, right, bottom, 0xFFF3F3F3);
        graphics.fill(left + 4, top + 4, right - 4, top + 48, 0xFFE0E0E0);
        graphics.fill(left + 8, top + 105, right - 8, listBottom() + 1, 0xFFFFFFFF);
        centered(graphics, title, top + 12, 0xFF202020);
        centered(graphics, summary.colonyId() < 0 ? Component.translatable("screen.colonyresourceledger.jobs.no_colony")
                : Component.literal(summary.colonyName()), top + 27, 0xFF6B4B13);
        boolean loading = period != summary.period();
        JobDurations aggregate = summary.totalDurations();
        int[] totals = aggregate.percentages();
        Component subtitle = loading ? Component.translatable("screen.colonyresourceledger.jobs.loading")
                : aggregate.total() == 0 ? Component.translatable("screen.colonyresourceledger.jobs.no_data")
                : Component.translatable("screen.colonyresourceledger.jobs.total", percent(totals[0]), percent(totals[1]), percent(totals[2]));
        centered(graphics, subtitle, top + 39, 0xFF555555);
        List<CitizenJobRow> rows = loading ? List.of() : visibleRows();
        int maxScroll = Math.max(0, rows.size() - capacity());
        scroll = Math.clamp(scroll, 0, maxScroll);
        previous.active = scroll > 0;
        next.active = scroll < maxScroll;
        super.render(graphics, mouseX, mouseY, partialTick);
        int rowRight = right - 20;
        int y = listTop();
        CitizenJobRow hovered = null;
        graphics.enableScissor(left + 10, listTop(), rowRight, listBottom());
        for (int i = scroll; i < Math.min(rows.size(), scroll + capacity()); i++) {
            CitizenJobRow row = rows.get(i);
            graphics.fill(left + 10, y, rowRight, y + ROW_HEIGHT - 2, 0xFFF0F0F0);
            Component current = row.observedNow() ? state(row.currentStatus())
                    : Component.translatable("screen.colonyresourceledger.jobs.unobserved");
            int statusX = rowRight - 6 - font.width(current);
            graphics.drawString(font, fit(font, row.name(), statusX - left - 20), left + 14, y + 4, 0xFF202020, false);
            graphics.drawString(font, current, statusX, y + 4,
                    row.observedNow() ? COLORS[row.currentStatus().ordinal()] : 0xFF777777, false);
            Component job = Component.translatable(row.jobTranslationKey());
            Component tracked = Component.translatable("screen.colonyresourceledger.jobs.tracked", job, days(row.durations().total()));
            graphics.drawString(font, fit(font, tracked.getString(), rowRight - left - 20), left + 14, y + 16, 0xFF555555, false);
            int[] percentages = row.durations().percentages();
            int barWidth = rowRight - left - 20;
            for (ObservedJobStatus status : ObservedJobStatus.values()) {
                int n = status.ordinal();
                String value = row.durations().total() == 0 ? "—" : percent(percentages[n]);
                Component label = Component.translatable("screen.colonyresourceledger.jobs.percent", state(status), value);
                graphics.drawString(font, fit(font, label.getString(), barWidth / 3 - 2),
                        left + 14 + n * barWidth / 3, y + 28, COLORS[n], false);
            }
            graphics.fill(left + 14, y + 41, rowRight - 6, y + 45, 0xFFD0D0D0);
            if (row.durations().total() > 0) {
                int cumulative = 0, barX = left + 14;
                for (int n = 0; n < 3; n++) {
                    cumulative += percentages[n];
                    int end = left + 14 + barWidth * cumulative / 1000;
                    graphics.fill(barX, y + 41, end, y + 45, COLORS[n]);
                    barX = end;
                }
            }
            if (mouseX >= left + 10 && mouseX < rowRight && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) hovered = row;
            y += ROW_HEIGHT;
        }
        graphics.disableScissor();
        if (rows.isEmpty()) centered(graphics, Component.translatable(loading ? "screen.colonyresourceledger.jobs.loading"
                : "screen.colonyresourceledger.jobs.empty"), listTop() + 12, 0xFF555555);
        if (maxScroll > 0) {
            int track = listBottom() - listTop();
            int thumb = Math.max(12, track * capacity() / rows.size());
            int thumbY = listTop() + (track - thumb) * scroll / maxScroll;
            graphics.fill(right - 17, listTop(), right - 11, listBottom(), 0xFFE0E0E0);
            graphics.fill(right - 17, thumbY, right - 11, thumbY + thumb, 0xFF777777);
        }
        String range = rows.isEmpty() ? "0 / 0" : (scroll + 1) + " - " + Math.min(rows.size(), scroll + capacity()) + " / " + rows.size();
        graphics.drawString(font, range, left + 10, bottom - 20, 0xFF555555, false);
        if (hovered != null) {
            Component coverage = period == JobPeriod.ALL
                    ? Component.translatable("screen.colonyresourceledger.jobs.coverage_all", days(hovered.durations().total()))
                    : Component.translatable("screen.colonyresourceledger.jobs.coverage", days(hovered.durations().total()), days(period.ticks()));
            Component tooltip = Component.literal(hovered.name()).append("\n").append(Component.translatable(hovered.jobTranslationKey()))
                    .append("\n").append(coverage).append("\n").append(Component.translatable("screen.colonyresourceledger.jobs.help"));
            graphics.renderTooltip(font, font.split(tooltip, Math.min(300, width - 24)), mouseX, mouseY);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy != 0 && x >= left() && x < left() + panelWidth() && y >= listTop() && y < listBottom()) {
            scrollBy(dy < 0 ? 1 : -1); return true;
        }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}

package dev.leopaul.colonyledger.client.gui;

import dev.leopaul.colonyledger.model.*;
import dev.leopaul.colonyledger.network.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** No periodic optimization: snapshots refresh only on opening, explicit refresh, or applying a swap. */
public final class HousingManagerScreen extends Screen {
    private static final String PREFIX = "screen.colonyresourceledger.housing.";
    private enum Tab { CITIZENS, RESIDENCES, SWAPS }
    private HousingDataPayload data;
    private Tab tab = Tab.CITIZENS;
    private EditBox search;
    private Button previous, next, apply, cancel;
    private int scroll, sort = 2, selected = -1, waitTicks;
    private HousingStatus statusFilter;
    private HousingPosition residenceFilter;
    private boolean pending;
    private record Row(Component first, Component second, Component third, Component detail, int color,
                       HousingPosition residence, int suggestionIndex) {}

    public HousingManagerScreen(HousingDataPayload data) { super(text("title")); this.data = data; }
    public void update(HousingDataPayload payload) {
        data = payload; pending = false; selected = -1; waitTicks = 0; rebuildWidgets();
    }
    private HousingSummary summary() { return data.summary(); }
    private int panelWidth() { return Math.min(530, width - 16); }
    private int panelHeight() { return Math.min(350, height - 16); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return (height - panelHeight()) / 2; }
    private int listTop() { return top() + 137; }
    private int listBottom() { return top() + panelHeight() - 31; }
    private int capacity() { return Math.max(0, (listBottom() - listTop()) / 44); }
    private static Component text(String suffix, Object... args) { return Component.translatable(PREFIX + suffix, args); }
    private static String number(double value) { return String.format(Locale.ROOT, "%.1f", value); }
    private static String fit(Font font, String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, Math.max(0, width - font.width("..."))) + "...";
    }
    private Component job(CitizenHousingInfo citizen) { return Component.translatable(citizen.jobName()); }
    private Component residence(CitizenHousingInfo citizen) {
        return citizen.residencePosition() == null ? text("status.no_home") : Component.literal(citizen.residenceName() + " [" + citizen.residencePosition().coordinates() + "]");
    }
    private Component workplace(CitizenHousingInfo citizen) {
        return citizen.workplacePosition() == null ? text("status.no_work") : Component.literal(citizen.workplaceName() + " [" + citizen.workplacePosition().coordinates() + "]");
    }
    @Override protected void init() {
        int x = left() + 10, usable = panelWidth() - 20, third = (usable - 8) / 3;
        for (Tab choice : Tab.values()) {
            Button b = addRenderableWidget(button(x, top() + 59, third, text("tab." + choice.name().toLowerCase(Locale.ROOT)), pressed -> {
                tab = choice; scroll = 0; selected = -1;
                if (choice == Tab.CITIZENS) residenceFilter = null;
                rebuildWidgets();
            }));
            b.active = choice != tab; x += third + 4;
        }
        int control = (usable - 8) / 3;
        addRenderableWidget(button(left() + 10, top() + 84, control, text("sort." + sort), b -> { sort = (sort + 1) % 3; scroll = 0; rebuildWidgets(); }));
        Component filterLabel = residenceFilter != null ? text("filter.residence") : statusFilter == null ? text("filter.all") : Component.translatable(statusFilter.translationKey());
        addRenderableWidget(button(left() + 14 + control, top() + 84, control, filterLabel, b -> {
            if (residenceFilter != null) residenceFilter = null;
            else statusFilter = statusFilter == null ? HousingStatus.GOOD : statusFilter.ordinal() == HousingStatus.values().length - 1 ? null : HousingStatus.values()[statusFilter.ordinal() + 1];
            scroll = 0; rebuildWidgets();
        }));
        Button refresh = addRenderableWidget(button(left() + 18 + 2 * control, top() + 84, control, text("refresh"), b -> {
            pending = true; waitTicks = 0; selected = -1;
            PacketDistributor.sendToServer(new RequestHousingDataPayload(summary().colonyId(), false));
            rebuildWidgets();
        }));
        refresh.active = !pending;
        String query = search == null ? "" : search.getValue();
        search = addRenderableWidget(new EditBox(font, left() + 10, top() + 110, usable, 20, text("search")));
        search.setHint(text("search")); search.setTextShadow(false); search.setValue(query);
        search.setResponder(value -> { scroll = 0; selected = -1; });
        previous = addRenderableWidget(button(left() + panelWidth() - 54, top() + panelHeight() - 26, 20, Component.literal("<"), b -> scrollBy(-Math.max(1, capacity()))));
        next = addRenderableWidget(button(left() + panelWidth() - 30, top() + panelHeight() - 26, 20, Component.literal(">"), b -> scrollBy(Math.max(1, capacity()))));
        apply = addRenderableWidget(button(left() + 10, top() + panelHeight() - 26, Math.min(130, panelWidth() / 3), text("confirm"), b -> applySelected()));
        cancel = addRenderableWidget(button(left() + 16 + Math.min(130, panelWidth() / 3), top() + panelHeight() - 26, 65, text("cancel"), b -> selected = -1));
    }
    private Button button(int x, int y, int width, Component label, Button.OnPress action) {
        return new Button(Button.builder(label, action).bounds(x, y, width, 20)) {
            @Override public void renderString(GuiGraphics g, Font font, int color) {
                String fitted = fit(font, getMessage().getString(), getWidth() - 6);
                g.drawString(font, fitted, getX() + (getWidth() - font.width(fitted)) / 2,
                        getY() + (getHeight() - font.lineHeight) / 2, active ? 0xFF202020 : 0xFF777777, false);
            }
        };
    }
    private void applySelected() {
        if (pending || selected < 0 || selected >= summary().suggestions().size() || !summary().canManage() || !summary().allowManualSwaps()) return;
        pending = true; waitTicks = 0;
        PacketDistributor.sendToServer(new ApplyHousingSwapPayload(data.proposalId(), selected));
        selected = -1; rebuildWidgets();
    }
    @Override public void tick() {
        // A dropped/rate-limited response never leaves the GUI permanently disabled.
        if (pending && ++waitTicks >= 100) { pending = false; rebuildWidgets(); }
    }
    private CitizenHousingInfo citizen(int id) { return summary().citizens().stream().filter(c -> c.citizenId() == id).findFirst().orElse(null); }
    private boolean matches(CitizenHousingInfo c, String query) {
        return (statusFilter == null || c.status() == statusFilter)
                && (residenceFilter == null || residenceFilter.equals(c.residencePosition()))
                && (query.isBlank() || (c.citizenName() + " " + job(c).getString() + " " + residence(c).getString()
                + " " + workplace(c).getString()).toLowerCase(Locale.ROOT).contains(query));
    }
    private List<Row> rows() {
        String query = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        Comparator<CitizenHousingInfo> ordering = sort == 0 ? Comparator.comparing(CitizenHousingInfo::citizenName, String.CASE_INSENSITIVE_ORDER)
                : sort == 1 ? Comparator.comparing(c -> job(c).getString(), String.CASE_INSENSITIVE_ORDER)
                : Comparator.<CitizenHousingInfo>comparingDouble(c -> c.hasCommute() ? c.homeToWorkDistance() : -1).reversed();
        if (tab == Tab.CITIZENS) {
            summary().citizens().stream().filter(c -> matches(c, query)).sorted(ordering).forEach(c -> {
                Component distance = c.hasCommute() ? text("distance", number(c.homeToWorkDistance())) : Component.translatable(c.status().translationKey());
                Component first = Component.literal(c.citizenName()).append(" — ").append(job(c)).append(" · ").append(distance);
                Component second = text("route", residence(c), workplace(c));
                Component third = text("citizen_levels", c.residenceLevel(), c.residenceOccupancy(), c.residenceCapacity(), c.workerLevel(), c.skillCap()).copy()
                        .append(c.skillCapped() ? text("capped") : Component.empty());
                Component detail = first.copy().append("\n").append(text("age", text(c.child() ? "child" : "adult")))
                        .append("\n").append(residence(c)).append("\n").append(workplace(c)).append("\n").append(third)
                        .append("\n").append(Component.translatable(c.status().translationKey()))
                        .append(c.excludedFromOptimization() ? text("excluded") : Component.empty());
                rows.add(new Row(first, second, third, detail, color(c.status()), null, -1));
            });
        } else if (tab == Tab.RESIDENCES) {
            for (ResidenceInfo h : summary().residences()) {
                List<CitizenHousingInfo> occupants = summary().citizens().stream().filter(c -> h.occupants().contains(c.citizenId())).sorted(ordering).toList();
                if (!query.isBlank() && !(h.name() + " " + h.position().coordinates()).toLowerCase(Locale.ROOT).contains(query)
                        && occupants.stream().noneMatch(c -> matches(c, query))) continue;
                if (statusFilter != null && occupants.stream().noneMatch(c -> c.status() == statusFilter)) continue;
                String names = occupants.stream().map(CitizenHousingInfo::citizenName).reduce((a, b) -> a + ", " + b).orElse(text("unoccupied").getString());
                Component first = Component.literal(h.name() + " [" + h.position().coordinates() + "]");
                Component second = text("residence_levels", h.level(), h.occupants().size(), h.capacity(), h.freePlaces(), h.skillCap());
                Component third = Component.literal(names);
                Component detail = first.copy().append("\n").append(second).append("\n").append(text(h.assignable() ? "residence_hint" : "special_home"));
                for (CitizenHousingInfo c : occupants) detail = detail.copy().append("\n").append(Component.literal(c.citizenName() + " — "))
                        .append(job(c)).append(" · ").append(c.hasCommute() ? text("distance", number(c.homeToWorkDistance())) : Component.translatable(c.status().translationKey()));
                rows.add(new Row(first, second, third, detail, h.overloaded() ? 0xFFAA2525 : 0xFF202020, h.position(), -1));
            }
        } else {
            for (int i = 0; i < summary().suggestions().size(); i++) {
                HousingSuggestion s = summary().suggestions().get(i);
                CitizenHousingInfo a = citizen(s.firstCitizenId()), b = citizen(s.secondCitizenId());
                if (a == null || b == null || (!matches(a, query) && !matches(b, query))) continue;
                Component first = Component.literal(a.citizenName() + " ↔ " + b.citizenName());
                Component second = text("swap_gain", number(s.before()), number(s.after()), number(s.gain()));
                Component third = selected == i ? Component.literal(a.citizenName() + ": " + number(s.firstBefore()) + " → " + number(s.firstAfter())
                        + " · " + b.citizenName() + ": " + number(s.secondBefore()) + " → " + number(s.secondAfter())) : text("preview_hint");
                Component detail = first.copy().append("\n").append(second).append("\n")
                        .append(text("swap_route", a.citizenName(), residence(a), residence(b), number(s.firstBefore()), number(s.firstAfter())))
                        .append("\n").append(text("swap_route", b.citizenName(), residence(b), residence(a), number(s.secondBefore()), number(s.secondAfter())))
                        .append("\n").append(text("swap_safety"));
                rows.add(new Row(first, second, third, detail, 0xFF287A38, null, i));
            }
        }
        return rows;
    }
    private static int color(HousingStatus s) {
        return switch (s) { case GOOD -> 0xFF287A38; case ACCEPTABLE -> 0xFF705D16; case FAR -> 0xFFAA5B18; case VERY_FAR, NO_HOME -> 0xFFAA2525; case NO_WORK -> 0xFF555555; };
    }
    private void scrollBy(int amount) { scroll = Math.clamp(scroll + amount, 0, Math.max(0, rows().size() - capacity())); }
    private void centered(GuiGraphics g, Component value, int y, int color) {
        String fitted = fit(font, value.getString(), panelWidth() - 20);
        g.drawString(font, fitted, (width - font.width(fitted)) / 2, y, color, false);
    }
    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float tick) {}
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
        super.renderBackground(g, mouseX, mouseY, tick);
        int x = left(), y = top(), right = x + panelWidth(), bottom = y + panelHeight();
        g.fill(x - 2, y - 2, right + 2, bottom + 2, 0xFF4A4A4A);
        g.fill(x, y, right, bottom, 0xFFF3F3F3);
        g.fill(x + 4, y + 4, right - 4, y + 55, 0xFFE0E0E0);
        centered(g, title.copy().append(" — ").append(summary().colonyName()), y + 10, 0xFF202020);
        Component overview = text("summary", summary().residences().size(), summary().freePlaces(), summary().citizens().stream().filter(c -> c.residencePosition() != null).count(), number(summary().averageDistance()), summary().commuters());
        Component compactOverview = text("summary_short", number(summary().averageDistance()), summary().residences().size(), summary().freePlaces());
        centered(g, font.width(overview) > panelWidth() - 20 ? compactOverview : overview, y + 24, 0xFF555555);
        long good = summary().citizens().stream().filter(c -> c.status() == HousingStatus.GOOD).count();
        long acceptable = summary().citizens().stream().filter(c -> c.status() == HousingStatus.ACCEPTABLE).count();
        long far = summary().citizens().stream().filter(c -> c.status() == HousingStatus.FAR).count();
        long veryFar = summary().citizens().stream().filter(c -> c.status() == HousingStatus.VERY_FAR).count();
        Component subtitle = !summary().messageKey().isEmpty() ? Component.translatable(summary().messageKey())
                : summary().analysisLimited() ? text("limited") : pending ? text("pending")
                : tab == Tab.SWAPS ? text("proposal", number(summary().averageDistance()),
                number(summary().commuters() == 0 ? 0 : (summary().totalDistance() - summary().suggestions().stream().mapToDouble(HousingSuggestion::gain).sum()) / summary().commuters()), summary().suggestions().size())
                : text("counts", good, acceptable, far, veryFar);
        centered(g, subtitle, y + 39, 0xFF555555);
        List<Row> rows = rows();
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - capacity()));
        previous.active = scroll > 0; next.active = scroll + capacity() < rows.size() && capacity() > 0;
        apply.visible = cancel.visible = selected >= 0 && tab == Tab.SWAPS;
        apply.active = summary().canManage() && summary().allowManualSwaps() && !pending;
        super.render(g, mouseX, mouseY, tick);
        Row hovered = null;
        g.enableScissor(x + 10, listTop(), right - 20, Math.max(listTop(), listBottom()));
        for (int i = scroll; i < Math.min(rows.size(), scroll + capacity()); i++) {
            Row row = rows.get(i); int rowY = listTop() + (i - scroll) * 44;
            g.fill(x + 10, rowY, right - 20, rowY + 42, row.suggestionIndex() == selected && selected >= 0 ? 0xFFD9ECD9 : 0xFFFFFFFF);
            g.drawString(font, fit(font, row.first().getString(), panelWidth() - 40), x + 14, rowY + 4, row.color(), false);
            g.drawString(font, fit(font, row.second().getString(), panelWidth() - 40), x + 14, rowY + 17, 0xFF555555, false);
            g.drawString(font, fit(font, row.third().getString(), panelWidth() - 40), x + 14, rowY + 30, 0xFF555555, false);
            if (mouseX >= x + 10 && mouseX < right - 20 && mouseY >= rowY && mouseY < rowY + 42) hovered = row;
        }
        g.disableScissor();
        if (rows.isEmpty()) centered(g, text(tab == Tab.SWAPS ? "no_swaps" : "empty"), listTop() + 12, 0xFF555555);
        if (capacity() == 0) centered(g, text("small_screen"), bottom - 39, 0xFFAA2525);
        if (rows.size() > capacity() && capacity() > 0) {
            int track = listBottom() - listTop(), thumb = Math.max(10, track * capacity() / rows.size());
            int thumbY = listTop() + (track - thumb) * scroll / (rows.size() - capacity());
            g.fill(right - 17, listTop(), right - 11, listBottom(), 0xFFE0E0E0);
            g.fill(right - 17, thumbY, right - 11, thumbY + thumb, 0xFF777777);
        }
        if (!apply.visible) g.drawString(font, rows.isEmpty() ? "0 / 0" : (scroll + 1) + " - " + Math.min(rows.size(), scroll + capacity()) + " / " + rows.size(), x + 10, bottom - 20, 0xFF555555, false);
        if (hovered != null) g.renderTooltip(font, font.split(hovered.detail(), Math.min(360, width - 24)), mouseX, mouseY);
        else if (mouseX >= x + 4 && mouseX < right - 4 && mouseY >= y + 4 && mouseY < y + 55) {
            Component detail = overview.copy().append("\n").append(text("counts", good, acceptable, far, veryFar))
                    .append("\n").append(text("distance_help"));
            if (!summary().messageKey().isEmpty()) detail = detail.copy().append("\n").append(Component.translatable(summary().messageKey()));
            if (!summary().canManage()) detail = detail.copy().append("\n").append(text("read_only"));
            else if (!summary().allowManualSwaps()) detail = detail.copy().append("\n").append(text("suggestions_only"));
            g.renderTooltip(font, font.split(detail, Math.min(360, width - 24)), mouseX, mouseY);
        }
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= left() + 10 && mouseX < left() + panelWidth() - 20 && mouseY >= listTop() && mouseY < listBottom()) {
            List<Row> rows = rows(); int index = scroll + (int) (mouseY - listTop()) / 44;
            if (index < rows.size() && index < scroll + capacity()) {
                Row row = rows.get(index);
                if (row.suggestionIndex() >= 0 && !pending) { selected = row.suggestionIndex(); return true; }
                if (row.residence() != null) { residenceFilter = row.residence(); tab = Tab.CITIZENS; scroll = 0; search.setValue(""); rebuildWidgets(); return true; }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (dy != 0 && x >= left() && x < left() + panelWidth() && y >= listTop() && y < listBottom()) { scrollBy(dy < 0 ? 1 : -1); return true; }
        return super.mouseScrolled(x, y, dx, dy);
    }
    @Override public boolean isPauseScreen() { return false; }
}

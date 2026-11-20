package com.deceasedcraft.doomedtraits.client;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import com.deceasedcraft.doomedtraits.mixin.TraitSelectScreenAccessor;
import com.deceasedcraft.doomedtraits.net.C2SDraftPacket;
import com.deceasedcraft.doomedtraits.net.ModNet;
import java.util.List;
import net.mattlives.doomedmatu.body.Trait;
import net.mattlives.doomedmatu.client.ClientSfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Категория «Doomed traits additions» на экране выбора черт Doomed:
 * перки слева, недостатки справа, по 4 строки со скроллом (колесо мыши).
 * Выбор лежит в нашей маске (draft), а её цена кодируется в маску экрана Doomed,
 * поэтому баланс и кнопку подтверждения считает сам Doomed.
 */
@Mod.EventBusSubscriber(modid = DoomedTraitsAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TraitScreenCategory {
    private static final String TRAIT_SCREEN = "net.mattlives.doomedmatu.client.screen.TraitSelectScreen";

    // Раскладка TraitSelectScreen (Doomed 0.4.7.x)
    private static final int ROW_H = 13;
    private static final int LIST_TOP = 84;
    private static final int DETAIL_H = 58;
    private static final int COL_W = 176;
    private static final int GUTTER = 14;
    private static final int HEADER_H = 16;
    private static final int VISIBLE_ROWS = 4;

    // Цвета Doomed
    private static final int PERK_ON = -6301280;
    private static final int PERK_OFF = -9531536;
    private static final int FLAW_ON = -2068132;
    private static final int FLAW_OFF = -7382448;
    private static final int PERK_ON_BG = 815782304;
    private static final int FLAW_ON_BG = 820015452;
    private static final int ROW_HOVER_BG = 419430399;
    private static final int DETAIL_BG = 1711276032;
    private static final int OVERLAY = -234420986;
    private static final int BLURB = -6643028;
    private static final int EFFECT = -3418925;
    private static final int TRACK = -13487561;

    private static final int TITLE_COLOR = 0xFF8FC8FF;
    private static final int LINE_COLOR = 0x558FC8FF;
    private static final int WARN_COLOR = 0xFFFF6B6B;

    private static Screen current;
    private static int draft;
    private static int scrollPerk;
    private static int scrollFlaw;
    private static long warnUntil;

    private TraitScreenCategory() {
    }

    private record Layout(int perkX, int flawX, int headerY, int rowsTop, int rows) {
    }

    private static boolean isTraitScreen(Screen screen) {
        return TRAIT_SCREEN.equals(screen.getClass().getName());
    }

    private static int detailTop(Screen screen) {
        return Math.max(131, screen.height - DETAIL_H - 32);
    }

    private static int doomedRows(Screen screen) {
        int visible = Math.max(3, (detailTop(screen) - LIST_TOP - 8) / ROW_H);
        int perks = 0;
        int flaws = 0;
        for (Trait t : Trait.values()) {
            if (t.isPerk()) {
                perks++;
            } else {
                flaws++;
            }
        }
        return Math.min(visible, Math.max(perks, flaws));
    }

    private static Layout layout(Screen screen) {
        int perkX = screen.width / 2 - COL_W - GUTTER / 2;
        int flawX = screen.width / 2 + GUTTER / 2;
        int y = LIST_TOP + doomedRows(screen) * ROW_H + 10;
        int rowsTop = y + HEADER_H;
        int rows = Math.min(VISIBLE_ROWS, (detailTop(screen) - 4 - rowsTop) / ROW_H);
        if (rows < 2) {
            return null;
        }
        return new Layout(perkX, flawX, y, rowsTop, rows);
    }

    private static int maxScroll(List<AddTrait> list, Layout l) {
        return Math.max(0, list.size() - l.rows());
    }

    private static AddTrait rowAt(Layout l, double mx, double my) {
        if (my < l.rowsTop() || my >= l.rowsTop() + l.rows() * ROW_H) {
            return null;
        }
        int row = (int) ((my - l.rowsTop()) / ROW_H);
        if (mx >= l.perkX() && mx <= l.perkX() + COL_W) {
            int idx = scrollPerk + row;
            return idx < AddTrait.perks().size() ? AddTrait.perks().get(idx) : null;
        }
        if (mx >= l.flawX() && mx <= l.flawX() + COL_W) {
            int idx = scrollFlaw + row;
            return idx < AddTrait.flaws().size() ? AddTrait.flaws().get(idx) : null;
        }
        return null;
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!isTraitScreen(screen) || current == screen) {
            return;
        }
        current = screen;
        draft = 0;
        scrollPerk = 0;
        scrollFlaw = 0;
        TraitSelectScreenAccessor acc = (TraitSelectScreenAccessor) screen;
        acc.doomedtraitsadd$setMask(acc.doomedtraitsadd$getMask() & ~AddTraits.OUR_BITS);
        ModNet.CHANNEL.sendToServer(new C2SDraftPacket(0));
    }

    // LOWEST: рисуем последними, поверх чужих надписей (например, слоя FancyMenu)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isTraitScreen(screen)) {
            return;
        }
        TraitSelectScreenAccessor acc = (TraitSelectScreenAccessor) screen;
        GuiGraphics gg = event.getGuiGraphics();
        Font font = Minecraft.getInstance().font;
        int cx = screen.width / 2;
        int dy = detailTop(screen);

        Layout l = layout(screen);
        if (l == null) {
            return;
        }
        scrollPerk = Math.min(scrollPerk, maxScroll(AddTrait.perks(), l));
        scrollFlaw = Math.min(scrollFlaw, maxScroll(AddTrait.flaws(), l));
        AddTrait hovered = rowAt(l, event.getMouseX(), event.getMouseY());

        // Подсказка «Наведите курсор...» убирается, когда курсор на какой-либо черте.
        // Нижняя полоса закрывает её вариант под панелью описания, верхняя - обычное место Doomed (только для наших черт,
        // у чужих там рисуется их собственное описание).
        if (hovered != null || acc.doomedtraitsadd$getHovered() != null) {
            int x0 = Math.max(0, cx - 260);
            int x1 = Math.min(screen.width, cx + 260);
            gg.fill(x0, screen.height - 36, x1, screen.height - 16, OVERLAY);
            if (hovered != null) {
                gg.fill(x0, dy + 2, x1, dy + 19, OVERLAY);
            }
        }

        gg.drawString(font, Component.translatable("doomed_traits_additions.category.title"), l.perkX(), l.headerY(), TITLE_COLOR, false);
        gg.fill(l.perkX(), l.headerY() + 11, l.flawX() + COL_W, l.headerY() + 12, LINE_COLOR);

        drawColumn(gg, font, AddTrait.perks(), l.perkX(), scrollPerk, l, hovered);
        drawColumn(gg, font, AddTrait.flaws(), l.flawX(), scrollFlaw, l, hovered);

        if (System.currentTimeMillis() < warnUntil) {
            gg.drawString(font, Component.translatable("doomed_traits_additions.category.conflict"),
                l.perkX(), l.rowsTop() + l.rows() * ROW_H + 2, WARN_COLOR, false);
        }

        if (hovered != null) {
            int dx = screen.width / 2 - COL_W - GUTTER / 2;
            gg.fill(dx - 3, dy - 4, dx + 366 + 3, dy + 58 - 4, DETAIL_BG);
            gg.drawString(font, Component.translatable(hovered.titleKey()), dx, dy, hovered.isPerk() ? PERK_ON : FLAW_ON, false);
            dy += 11;
            gg.drawString(font, Component.translatable(hovered.blurbKey()), dx, dy, BLURB, false);
            dy += 11;
            List<FormattedCharSequence> lines = font.split(Component.translatable(hovered.effectKey()), 366);
            for (int i = 0; i < Math.min(3, lines.size()); i++) {
                gg.drawString(font, lines.get(i), dx, dy, EFFECT, false);
                dy += 10;
            }
        }
    }

    private static void drawColumn(GuiGraphics gg, Font font, List<AddTrait> list, int x, int scroll, Layout l, AddTrait hovered) {
        for (int i = 0; i < l.rows(); i++) {
            int idx = scroll + i;
            if (idx >= list.size()) {
                break;
            }
            AddTrait t = list.get(idx);
            int y = l.rowsTop() + i * ROW_H;
            boolean on = AddTraits.has(draft, t);
            boolean perk = t.isPerk();
            if (on) {
                gg.fill(x - 2, y - 1, x + COL_W, y + ROW_H - 2, perk ? PERK_ON_BG : FLAW_ON_BG);
            } else if (t == hovered) {
                gg.fill(x - 2, y - 1, x + COL_W, y + ROW_H - 2, ROW_HOVER_BG);
            }
            int col = on ? (perk ? PERK_ON : FLAW_ON) : (perk ? PERK_OFF : FLAW_OFF);
            gg.drawString(font, on ? "[x]" : "[ ]", x, y, col, false);
            gg.drawString(font, Component.translatable(t.titleKey()), x + 22, y, col, false);
            String price = perk ? "-" + t.cost() : "+" + (-t.cost());
            gg.drawString(font, price, x + COL_W - font.width(price) - 6, y, col, false);
        }
        if (list.size() > l.rows()) {
            int trackTop = l.rowsTop();
            int trackH = l.rows() * ROW_H - 2;
            int x0 = x + COL_W + 2;
            gg.fill(x0, trackTop, x0 + 1, trackTop + trackH, TRACK);
            int thumbH = Math.max(8, trackH * l.rows() / list.size());
            int max = list.size() - l.rows();
            int thumbY = trackTop + (trackH - thumbH) * scroll / Math.max(1, max);
            gg.fill(x0 - 1, thumbY, x0 + 2, thumbY + thumbH, PERK_OFF);
        }
    }

    @SubscribeEvent
    public static void onScroll(ScreenEvent.MouseScrolled.Pre event) {
        Screen screen = event.getScreen();
        if (!isTraitScreen(screen)) {
            return;
        }
        Layout l = layout(screen);
        if (l == null) {
            return;
        }
        double mx = event.getMouseX();
        double my = event.getMouseY();
        if (my < l.rowsTop() || my >= l.rowsTop() + l.rows() * ROW_H) {
            return;
        }
        int dir = event.getScrollDelta() > 0 ? -1 : 1;
        if (mx >= l.perkX() && mx <= l.perkX() + COL_W + 6) {
            scrollPerk = Math.max(0, Math.min(maxScroll(AddTrait.perks(), l), scrollPerk + dir));
            event.setCanceled(true);
        } else if (mx >= l.flawX() && mx <= l.flawX() + COL_W + 6) {
            scrollFlaw = Math.max(0, Math.min(maxScroll(AddTrait.flaws(), l), scrollFlaw + dir));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (event.getButton() != 0 || !isTraitScreen(screen)) {
            return;
        }
        TraitSelectScreenAccessor acc = (TraitSelectScreenAccessor) screen;
        int mask = acc.doomedtraitsadd$getMask();

        Layout l = layout(screen);
        if (l != null) {
            AddTrait t = rowAt(l, event.getMouseX(), event.getMouseY());
            if (t != null) {
                int next = draft ^ t.bit();
                if (AddTraits.conflicts(next, mask)) {
                    warn();
                } else {
                    draft = next;
                    acc.doomedtraitsadd$setMask(AddTraits.withCost(mask, draft));
                    acc.doomedtraitsadd$refresh();
                    ModNet.CHANNEL.sendToServer(new C2SDraftPacket(draft));
                    ClientSfx.select();
                }
                event.setCanceled(true);
                return;
            }
        }

        // Клик по «Сообразительности» при выбранном Слабоумии: блокируем
        if (AddTraits.has(draft, AddTrait.DEMENTIA)) {
            Trait clicked = acc.doomedtraitsadd$traitAt(event.getMouseX(), event.getMouseY());
            if (clicked == Trait.BRIGHT) {
                warn();
                event.setCanceled(true);
            }
        }
    }

    private static void warn() {
        warnUntil = System.currentTimeMillis() + 2500L;
    }
}

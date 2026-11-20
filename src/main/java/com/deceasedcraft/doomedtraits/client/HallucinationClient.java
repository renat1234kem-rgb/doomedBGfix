package com.deceasedcraft.doomedtraits.client;

import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import com.deceasedcraft.doomedtraits.config.ClientConfig;
import com.deceasedcraft.doomedtraits.net.C2SGunResultPacket;
import com.deceasedcraft.doomedtraits.net.ModNet;
import com.deceasedcraft.doomedtraits.net.S2CFxPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/** Клиентская часть галлюцинаций Хардкорной шизофрении. */
@Mod.EventBusSubscriber(modid = DoomedTraitsAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HallucinationClient {
    private record Img(ResourceLocation loc, int w, int h) {
    }

    private static Img img(String name, int w, int h) {
        return new Img(new ResourceLocation(DoomedTraitsAdditions.MOD_ID, "textures/gui/" + name + ".png"), w, h);
    }

    private static final List<Img> BAD = List.of(
        img("bad_1", 256, 256), img("bad_2", 256, 256), img("bad_3", 256, 256),
        img("bad_4", 256, 256), img("bad_5", 256, 256), img("bad_6", 256, 256));
    private static final List<Img> WARM = List.of(img("warm_1", 256, 256), img("warm_2", 256, 256), img("warm_3", 256, 256));
    private static final Img FOXY = img("foxy", 638, 480);
    private static final SoundEvent FOXY_SCREAM =
        SoundEvent.createVariableRangeEvent(new ResourceLocation(DoomedTraitsAdditions.MOD_ID, "foxy_scream"));

    private static final RandomSource RNG = RandomSource.create();
    private static final String[] VITAL_KEYS = {"heart", "sys", "dia", "spo2", "resp", "temp", "blood", "mood", "rbc", "pain"};

    private record Playing(SoundInstance inst, long stopAt) {
    }

    private static final List<Playing> PLAYING = new ArrayList<>();
    private static final Map<String, Float> FAKE = new HashMap<>();

    private static long now;
    private static long invertUntil;
    private static long imageUntil;
    private static Img currentImage = BAD.get(0);
    private static long redUntil;
    private static long redTotal = 1;
    private static long vignetteUntil;
    private static long vignetteTotal = 1;
    private static long shakeUntil;
    private static long vitalsUntil;
    private static long nextVitalReroll;
    private static long blackoutUntil;
    private static long blackoutTotal = 1;
    private static long gunUntil;
    private static long gunTotal = 1;
    private static boolean jumpWasDown;
    private static long bloodlustUntil;
    private static long bloodlustStart;
    private static long nextBloodBeat;
    private static long depressionUntil;
    private static long depressionTotal = 1;

    private HallucinationClient() {
    }

    public static void handle(int type, int duration, int arg, String str) {
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case S2CFxPacket.INVERT -> invertUntil = now + duration;
            case S2CFxPacket.IMAGE -> showImage(mc, duration, arg);
            case S2CFxPacket.RED_FLASH -> {
                redTotal = Math.max(1, duration);
                redUntil = now + redTotal;
            }
            case S2CFxPacket.VIGNETTE -> {
                vignetteTotal = Math.max(1, duration);
                vignetteUntil = now + vignetteTotal;
            }
            case S2CFxPacket.SHAKE -> shakeUntil = now + duration;
            case S2CFxPacket.HEAD_SNAP -> {
                LocalPlayer p = mc.player;
                if (p != null) {
                    p.setYRot(p.getYRot() + arg);
                    p.yRotO = p.getYRot();
                }
            }
            case S2CFxPacket.FAKE_VITALS -> startFakeVitals(duration);
            case S2CFxPacket.GUN -> {
                gunTotal = Math.max(1, duration);
                gunUntil = now + gunTotal;
                jumpWasDown = true;
                closeScreens(mc);
            }
            case S2CFxPacket.BLACKOUT -> {
                blackoutTotal = Math.max(1, duration);
                blackoutUntil = now + blackoutTotal;
            }
            case S2CFxPacket.SOUND -> playLocal(mc, str, arg / 100.0F, duration);
            case S2CFxPacket.BLOODLUST -> {
                if (arg == 0) {
                    bloodlustUntil = 0;
                } else {
                    bloodlustStart = now;
                    bloodlustUntil = now + duration;
                    nextBloodBeat = now;
                }
            }
            case S2CFxPacket.DEPRESSION -> {
                depressionTotal = Math.max(1, duration);
                depressionUntil = now + depressionTotal;
            }
            default -> ExtraHallucinations.handle(type, duration, arg);
        }
    }

    // ---------- картинки 25-го кадра ----------

    private static void showImage(Minecraft mc, int duration, int category) {
        if (ClientConfig.foxy()) {
            currentImage = FOXY;
            imageUntil = now + Math.max(duration, 10);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(FOXY_SCREAM, 1.0F, 1.0F));
            return;
        }
        List<Img> pool = category == 1 ? WARM : BAD;
        currentImage = pool.get(RNG.nextInt(pool.size()));
        imageUntil = now + Math.max(1, duration);
    }

    // ---------- звук «в голове» ----------

    private static void playLocal(Minecraft mc, String id, float volume, int stopTicks) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return;
        }
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(rl);
        if (ev == null) {
            ev = SoundEvent.createVariableRangeEvent(rl);
        }
        SoundInstance inst = SimpleSoundInstance.forUI(ev, 1.0F, volume);
        mc.getSoundManager().play(inst);
        if (stopTicks > 0) {
            PLAYING.add(new Playing(inst, now + stopTicks));
        }
    }

    // ---------- лже-показатели ----------

    private static void startFakeVitals(int duration) {
        vitalsUntil = now + duration;
        nextVitalReroll = now + 100;
        FAKE.clear();
        List<String> keys = new ArrayList<>(List.of(VITAL_KEYS));
        boolean[] danger = new boolean[keys.size()];
        int count = 0;
        for (int i = 0; i < danger.length; i++) {
            danger[i] = RNG.nextFloat() < 0.45F;
            count += danger[i] ? 1 : 0;
        }
        while (count < 2) {
            int i = RNG.nextInt(danger.length);
            if (!danger[i]) {
                danger[i] = true;
                count++;
            }
        }
        for (int i = 0; i < keys.size(); i++) {
            FAKE.put(keys.get(i), gen(keys.get(i), danger[i]));
        }
    }

    private static float range(float lo, float hi) {
        return lo + RNG.nextFloat() * (hi - lo);
    }

    /** Случайное лживое значение показателя: опасное (критическое) или просто «не то». */
    private static float gen(String key, boolean danger) {
        boolean low = RNG.nextBoolean();
        return switch (key) {
            case "heart" -> danger ? (low ? range(25, 40) : range(170, 230)) : range(55, 115);
            case "sys" -> danger ? (low ? range(50, 75) : range(200, 270)) : range(95, 155);
            case "dia" -> danger ? (low ? range(25, 45) : range(120, 160)) : range(55, 100);
            case "spo2" -> danger ? range(50, 82) : range(90, 100);
            case "resp" -> danger ? (low ? range(3, 7) : range(32, 48)) : range(10, 24);
            case "temp" -> danger ? (low ? range(26, 31) : range(41.5F, 44.5F)) : range(35.8F, 38.4F);
            case "blood" -> danger ? range(0.7F, 1.8F) : range(3.8F, 5.6F);
            case "mood" -> danger ? -100.0F : range(-40, 45);
            case "rbc" -> danger ? range(0.2F, 0.5F) : range(0.8F, 1.15F);
            default -> danger ? range(85, 100) : range(0, 35); // pain
        };
    }

    public static boolean vitalsActive() {
        return now < vitalsUntil;
    }

    /** Подмена показателя панели здоровья, пока идёт галлюцинация. */
    public static float fake(String key, float real) {
        if (now >= vitalsUntil) {
            return real;
        }
        Float f = FAKE.get(key);
        return f == null ? real : f;
    }

    // ---------- закрытие интерфейсов ----------

    private static void closeScreens(Minecraft mc) {
        if (mc.screen != null && mc.player != null) {
            mc.player.closeContainer();
            if (mc.screen != null) {
                mc.setScreen(null);
            }
        }
    }

    // ---------- события ----------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        now++;
        Minecraft mc = Minecraft.getInstance();

        for (Iterator<Playing> it = PLAYING.iterator(); it.hasNext(); ) {
            Playing p = it.next();
            if (now >= p.stopAt()) {
                mc.getSoundManager().stop(p.inst());
                it.remove();
            }
        }

        if (now < vitalsUntil && now >= nextVitalReroll) {
            nextVitalReroll = now + 120 + RNG.nextInt(120);
            String key = VITAL_KEYS[RNG.nextInt(VITAL_KEYS.length)];
            FAKE.put(key, gen(key, RNG.nextFloat() < 0.5F));
        }

        if (now < gunUntil && mc.player != null) {
            if (mc.screen != null) {
                closeScreens(mc);
            }
            boolean down = mc.options.keyJump.isDown();
            if (down && !jumpWasDown) {
                gunUntil = 0;
                ModNet.CHANNEL.sendToServer(new C2SGunResultPacket(true));
            }
            jumpWasDown = down;
        }

        if (now < bloodlustUntil && now >= nextBloodBeat) {
            float ramp = Math.min(1.0F, (now - bloodlustStart) / 1800.0F);
            nextBloodBeat = now + Math.round(34 - 14 * ramp);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                SoundEvent.createVariableRangeEvent(new ResourceLocation("doomedmatu", "heartthump_heavy")),
                0.9F + 0.3F * ramp, 0.5F + 0.5F * ramp));
        }
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (now >= invertUntil) {
            return;
        }
        var in = event.getInput();
        in.forwardImpulse = -in.forwardImpulse;
        in.leftImpulse = -in.leftImpulse;
        boolean up = in.up;
        in.up = in.down;
        in.down = up;
        boolean left = in.left;
        in.left = in.right;
        in.right = left;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float amp = 0.0F;
        if (now < shakeUntil) {
            amp = 2.2F;
        } else if (now < bloodlustUntil) {
            amp = 0.5F * Math.min(1.0F, (now - bloodlustStart) / 1800.0F);
        }
        if (amp <= 0.0F) {
            return;
        }
        event.setYaw(event.getYaw() + (RNG.nextFloat() - 0.5F) * amp);
        event.setPitch(event.getPitch() + (RNG.nextFloat() - 0.5F) * amp);
        event.setRoll(event.getRoll() + (RNG.nextFloat() - 0.5F) * amp * 1.5F);
    }

    private static void edges(GuiGraphics gg, int w, int h, double k, int rgb, int maxAlpha) {
        int a = (int) (maxAlpha * Math.max(0.0, Math.min(1.0, k)));
        int bw = (int) (w * 0.28 * k) + 2;
        int bh = (int) (h * 0.28 * k) + 2;
        int c = (a << 24) | rgb;
        gg.fill(0, 0, w, bh, c);
        gg.fill(0, h - bh, w, h, c);
        gg.fill(0, 0, bw, h, c);
        gg.fill(w - bw, 0, w, h, c);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gg = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        if (now < redUntil) {
            int a = (int) (0x70 * (redUntil - now) / (double) redTotal);
            gg.fill(0, 0, w, h, (a << 24) | 0xFF0000);
        }
        if (now < vignetteUntil) {
            double t = (vignetteUntil - now) / (double) vignetteTotal;
            edges(gg, w, h, Math.sin(Math.PI * (1.0 - t)), 0x000000, 0xC0);
        }
        if (now < depressionUntil) {
            double t = (depressionUntil - now) / (double) depressionTotal;
            double k = Math.min(1.0, Math.min((1.0 - t) * 8.0, t * 4.0));
            gg.fill(0, 0, w, h, ((int) (0x78 * k) << 24) | 0x081028);
        }
        if (now < bloodlustUntil) {
            double ramp = Math.min(1.0, (now - bloodlustStart) / 1800.0);
            double pulse = 0.55 + 0.45 * Math.sin(now * (0.25 + 0.2 * ramp));
            edges(gg, w, h, (0.35 + 0.65 * ramp) * pulse, 0x8A0000, 0xA0);
        }
        if (now < blackoutUntil) {
            double t = (blackoutUntil - now) / (double) blackoutTotal;
            int a = (int) (0xFF * Math.min(1.0, Math.sin(Math.PI * (1.0 - t)) * 1.6));
            gg.fill(0, 0, w, h, a << 24);
        }
        if (now < imageUntil) {
            gg.blit(currentImage.loc(), 0, 0, w, h, 0.0F, 0.0F, currentImage.w(), currentImage.h(), currentImage.w(), currentImage.h());
        }
        if (now < gunUntil) {
            double left = (gunUntil - now) / (double) gunTotal;
            gg.fill(0, 0, w, h, 0x40000000);
            int pulse = (int) (0x80 + 0x7F * Math.abs(Math.sin(now * 0.4)));
            int color = 0xFF000000 | (pulse << 16) | 0x2020;
            gg.drawCenteredString(mc.font, Component.translatable("doomed_traits_additions.fx.gun"), w / 2, h / 2 - 20, color);
            int barW = (int) (120 * left);
            gg.fill(w / 2 - 60, h / 2 - 6, w / 2 - 60 + barW, h / 2 - 2, 0xFFCC3030);
        }
    }
}

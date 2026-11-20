package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import com.deceasedcraft.doomedtraits.net.ModNet;
import com.deceasedcraft.doomedtraits.net.S2CFxPacket;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Временные «надстройки» над состоянием Doomed, которые не ломают настоящие значения:
 *  - временный сдвиг настроения (депрессивный провал): прибавляется к итоговому настроению и потом пропадает,
 *    а настоящее настроение всё это время продолжает меняться как обычно;
 *  - жажда убийства: растёт давление и пульс, слегка болит, пока игрок кого-нибудь не убьёт.
 */
@Mod.EventBusSubscriber(modid = DoomedTraitsAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MoodEffects {
    private record TempMood(float amount, long untilTick) {
    }

    private static final class Bloodlust {
        long startTick;
        long endTick;
        float level; // 0..1
    }

    private static final Map<DoomedData, TempMood> TEMP_MOOD = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<DoomedData, Bloodlust> BLOODLUST = Collections.synchronizedMap(new WeakHashMap<>());

    // --- жажда убийства: настройки ---
    private static final int BLOODLUST_MAX_TICKS = 6 * 60 * 20;
    private static final float BLOODLUST_RAMP_SECONDS = 90.0F;
    private static final float BLOODLUST_BP_BONUS = 45.0F;
    private static final float BLOODLUST_HR_BONUS = 35.0F;
    private static final float BLOODLUST_PAIN_MIN = 4.0F;
    private static final float BLOODLUST_PAIN_MAX = 16.0F;

    private MoodEffects() {
    }

    private static long now() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        return s == null ? 0L : s.getTickCount();
    }

    // ---------- временное настроение ----------

    public static void addTempMood(DoomedData d, float amount, int ticks) {
        TEMP_MOOD.put(d, new TempMood(amount, now() + ticks));
    }

    /** Текущий временный сдвиг настроения (0, если нет или истёк). Вызывается из миксина на setMood. */
    public static float tempMoodOffset(DoomedData d) {
        TempMood t = TEMP_MOOD.get(d);
        if (t == null) {
            return 0.0F;
        }
        if (now() >= t.untilTick()) {
            TEMP_MOOD.remove(d);
            return 0.0F;
        }
        return t.amount();
    }

    // ---------- жажда убийства ----------

    public static boolean bloodlustActive(DoomedData d) {
        return BLOODLUST.containsKey(d);
    }

    public static boolean startBloodlust(ServerPlayer sp, DoomedData d) {
        if (BLOODLUST.containsKey(d)) {
            return false;
        }
        Bloodlust b = new Bloodlust();
        b.startTick = now();
        b.endTick = b.startTick + BLOODLUST_MAX_TICKS;
        BLOODLUST.put(d, b);
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CFxPacket(S2CFxPacket.BLOODLUST, BLOODLUST_MAX_TICKS, 1));
        sp.displayClientMessage(Component.literal("Тебе нужно кого-нибудь убить. Прямо сейчас."), true);
        return true;
    }

    public static void endBloodlust(ServerPlayer sp, DoomedData d, boolean killed) {
        if (BLOODLUST.remove(d) == null) {
            return;
        }
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CFxPacket(S2CFxPacket.BLOODLUST, 0, 0));
        if (killed) {
            d.addHappiness(6.0F);
            sp.displayClientMessage(Component.literal("Стало легче."), true);
        } else {
            sp.displayClientMessage(Component.literal("Желание медленно отступает."), true);
        }
    }

    public static void forget(DoomedData d) {
        BLOODLUST.remove(d);
        TEMP_MOOD.remove(d);
    }

    /** Раз в секунду: растим уровень жажды убийства и подмешиваем лёгкую боль. */
    public static void tickSecond(ServerPlayer sp, DoomedData d) {
        Bloodlust b = BLOODLUST.get(d);
        if (b == null) {
            return;
        }
        long t = now();
        if (t >= b.endTick) {
            endBloodlust(sp, d, false);
            return;
        }
        b.level = Math.min(1.0F, b.level + 1.0F / BLOODLUST_RAMP_SECONDS);
        float pain = BLOODLUST_PAIN_MIN + (BLOODLUST_PAIN_MAX - BLOODLUST_PAIN_MIN) * b.level;
        for (BodyPart part : new BodyPart[]{BodyPart.HEAD, BodyPart.THORAX}) {
            d.getLimb(part).setPain(Math.max(d.getLimb(part).getPain(), pain));
        }
    }

    public static float bloodlustBp(DoomedData d) {
        Bloodlust b = BLOODLUST.get(d);
        return b == null ? 0.0F : BLOODLUST_BP_BONUS * b.level;
    }

    public static float bloodlustHeartRate(DoomedData d) {
        Bloodlust b = BLOODLUST.get(d);
        return b == null ? 0.0F : BLOODLUST_HR_BONUS * b.level;
    }

    /** Любое убийство (игрок, зомби, животное) снимает жажду. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer killer && event.getEntity() != killer) {
            DoomedDataUtil.get(killer).ifPresent(d -> endBloodlust(killer, d, true));
        }
    }
}

package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import com.deceasedcraft.doomedtraits.net.ModNet;
import com.deceasedcraft.doomedtraits.net.S2CFxPacket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DespairHandler;
import net.mattlives.doomedmatu.body.Ragdoll;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.network.DoomedNetwork;
import net.mattlives.doomedmatu.network.S2CDeathScreenPacket;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Хардкорная шизофрения: каждые 2-10 минут случайная галлюцинация из таблицы ниже (сумма шансов = 100).
 * Интервал короче при сильном повреждении мозга (1-6 мин) и длиннее при настроении выше 20 (6-15 мин).
 */
public final class SchizoManager {
    // ---- настройки интервала (минуты) ----
    private static final int BASE_MIN = 2;
    private static final int BASE_MAX = 10;
    private static final int HURT_MIN = 1;
    private static final int HURT_MAX = 6;
    private static final int HAPPY_ADD_MIN = 4;
    private static final int HAPPY_ADD_MAX = 5;
    private static final float HAPPY_MOOD = 20.0F;
    private static final int TICKS_PER_MIN = 1200;

    // ---- настройки событий ----
    private static final int GUN_REACTION_TICKS = 20;       // 1 секунда на реакцию
    private static final int GUN_NETWORK_SLACK_TICKS = 6;
    private static final float PHANTOM_PAIN_LEVEL = 32.0F;  // «заполнить шкалу боли на ~30%»
    private static final float DEPRESSION_MOOD = -50.0F;
    private static final int FAKE_VITALS_TICKS = 60 * 20;   // минута

    private interface Ev {
        boolean run(ServerPlayer sp, DoomedData d);
    }

    private record Entry(String id, int weight, Ev ev) {
    }

    private record Task(long at, Consumer<ServerPlayer> run) {
    }

    private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>();
    private static final Map<UUID, List<Task>> TASKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GUN_DEADLINE = new ConcurrentHashMap<>();
    /** Последние сказанные другом реплики игрока: чтобы не повторяться. */
    private static final Map<UUID, Deque<String>> RECENT = new ConcurrentHashMap<>();

    private static final String[] INSULTS = {
        "Ты правда думаешь, что у тебя получится?",
        "Жалкое зрелище. Даже смотреть тошно.",
        "Они все давно бы тебя бросили. Хорошо, что я рядом... пока.",
        "Ты слабый. Я это вижу, и они тоже.",
        "Ну давай, облажайся ещё раз. Ты это умеешь.",
        "Ты думал, кто-то придёт за тобой? Смешно.",
        "Каждый твой шаг - ошибка. Я считаю.",
        "Даже я начинаю в тебе сомневаться."
    };
    private static final String[] SNAPS = {
        "Отстань от меня.",
        "Заткнись уже, а?",
        "Сам разбирайся, умник.",
        "Не смотри на меня так.",
        "Хватит ныть. Надоел.",
        "Я тебе не нянька.",
        "Ага, конечно. Продолжай в том же духе.",
        "Не трогай меня."
    };
    private static final String[] COMFORTS = {
        "Я рядом. Всё будет хорошо.",
        "Дыши. Я с тобой.",
        "Ты молодец, правда.",
        "Мы справимся. Как всегда.",
        "Ты сильнее, чем думаешь.",
        "Я никуда не уйду. Обещаю.",
        "Посмотри, сколько ты уже прошёл. Горжусь тобой.",
        "Всё не зря. Я вижу."
    };
    private static final String[] JOKES = {
        "Знаешь, почему зомби не ходят в кино? Все места уже заняты такими же.",
        "Если мы умрём, давай хотя бы красиво. Хотя ты, конечно, как получится.",
        "Я тут посчитал: ты споткнулся уже в сто двадцатый раз. Рекорд!",
        "Говорят, у меня нет чувства юмора. Но ты же улыбаешься.",
        "Если бы у меня были руки, я бы тебе поаплодировал. Но вообще-то нет.",
        "Знаешь, что общего между тобой и консервами? Срок годности уже поджимает."
    };
    private static final String[] CALM = {
        "Тише. Я присмотрю. Закрой глаза на секунду.",
        "Вдох... выдох... Вот так. Ты справляешься.",
        "Здесь никого нет. Только я. И я не уйду.",
        "Отпусти плечи. Никуда не надо спешить.",
        "Слышишь? Тихо. Это хорошая тишина."
    };
    private static final String[] MEMORIES = {
        "Ты вдруг вспоминаешь тёплый вечер у огня и запах горячего чая. Становится легче.",
        "Перед глазами всплывает чей-то смех. Знакомый, добрый. Ты невольно улыбаешься.",
        "Ты вспоминаешь дом. Не этот, а настоящий. Внутри что-то теплеет.",
        "Закат над крышами. Ты тогда ещё не знал, что всё изменится. Но было хорошо."
    };
    private static final String[] WHISPERS = {
        "...слышишь?..",
        "...кто-то рядом...",
        "...ты один?..",
        "...они знают, где ты...",
        "...тише..."
    };
    private static final String[] MEMORY_ERRORS = {
        "где я?",
        "кто здесь?",
        "я же ничего не писал...",
        "почему так темно",
        "они уже рядом",
        "помогите",
        "это был не я",
        "я не помню, как сюда попал",
        "тише, они слушают"
    };
    private static final String[] BONE_SOUNDS = {"doomedmatu:bonebreak1", "doomedmatu:bonebreak2", "doomedmatu:bonebreak3"};
    private static final BodyPart[] PHANTOM_LIMBS = {
        BodyPart.UPPER_ARM_LEFT, BodyPart.UPPER_ARM_RIGHT, BodyPart.FOREARM_LEFT, BodyPart.FOREARM_RIGHT,
        BodyPart.THIGH_LEFT, BodyPart.THIGH_RIGHT, BodyPart.ABDOMEN, BodyPart.THORAX
    };

    private static final List<Entry> TABLE = List.of(
        // --- твои исходные 10 (40%) ---
        new Entry("two_steps", 5, SchizoManager::twoSteps),
        new Entry("invert_controls", 4, (sp, d) -> fx(sp, S2CFxPacket.INVERT, 120, 0)),
        new Entry("self_harm_episode", 2, SchizoManager::selfHarm),
        new Entry("frame_25", 4, (sp, d) -> fx(sp, S2CFxPacket.IMAGE, 1, 0)),
        new Entry("phantom_sounds", 5, SchizoManager::phantomSounds),
        new Entry("weapon_at_temple", 1, SchizoManager::weaponAtTemple),
        new Entry("sudden_dyspnea", 4, SchizoManager::dyspnea),
        new Entry("fake_vitals", 5, (sp, d) -> fx(sp, S2CFxPacket.FAKE_VITALS, FAKE_VITALS_TICKS, 0)),
        new Entry("waiter_swap", 5, SchizoManager::waiterSwap),
        new Entry("scream", 5, SchizoManager::scream),
        // --- твои новые (22%) ---
        new Entry("forgetfulness", 3, (sp, d) -> fx(sp, S2CFxPacket.FORGET, 200, 0)),
        new Entry("flicker", 4, (sp, d) -> fx(sp, S2CFxPacket.FLICKER, 100, 0)),
        new Entry("wrong_mob", 3, SchizoManager::wrongMob),
        new Entry("doppelganger", 2, (sp, d) -> fx(sp, S2CFxPacket.DOPPELGANGER, 600, 0)),
        new Entry("distorted_world", 2, (sp, d) -> fx(sp, S2CFxPacket.DISTORT, 200, 0)),
        new Entry("memory_error", 3, SchizoManager::memoryError),
        new Entry("muffled_ears", 3, (sp, d) -> fx(sp, S2CFxPacket.MUFFLE, 200, 0)),
        new Entry("already_dead", 2, SchizoManager::alreadyDead),
        // --- остальные (38%) ---
        new Entry("friend_insults", 2, SchizoManager::friendInsults),
        new Entry("friend_snaps", 2, (sp, d) -> friendLine(sp, d, SNAPS, -3.0F)),
        new Entry("friend_comforts", 2, (sp, d) -> friendLine(sp, d, COMFORTS, 3.0F)),
        new Entry("friend_joke", 2, (sp, d) -> friendLine(sp, d, JOKES, 2.0F)),
        new Entry("calm_voice", 1, (sp, d) -> friendLine(sp, d, CALM, 3.0F)),
        new Entry("warm_memory", 2, SchizoManager::warmMemory),
        new Entry("phantom_hit", 2, SchizoManager::phantomHit),
        new Entry("phantom_pain", 2, SchizoManager::phantomPain),
        new Entry("tunnel_vision", 2, (sp, d) -> fx(sp, S2CFxPacket.VIGNETTE, 300, 0)),
        new Entry("whispers", 2, SchizoManager::whispers),
        new Entry("heartbeat_tinnitus", 3, SchizoManager::heartbeat),
        new Entry("hand_slips", 2, SchizoManager::dropItem),
        new Entry("head_snap", 2, (sp, d) -> fx(sp, S2CFxPacket.HEAD_SNAP, 0, (sp.getRandom().nextBoolean() ? 1 : -1) * (60 + sp.getRandom().nextInt(80)))),
        new Entry("depressive_dip", 2, SchizoManager::depressiveDip),
        new Entry("bloodlust_urge", 2, (sp, d) -> MoodEffects.startBloodlust(sp, d)),
        new Entry("falling_down", 2, SchizoManager::fallingDown),
        new Entry("followed", 2, SchizoManager::followed),
        new Entry("radio_coordinates", 2, SchizoManager::radioCoordinates),
        new Entry("blackout", 2, (sp, d) -> fx(sp, S2CFxPacket.BLACKOUT, 100, 0))
    );

    static {
        int sum = TABLE.stream().mapToInt(Entry::weight).sum();
        if (sum != 100) {
            DoomedTraitsAdditions.LOGGER.error("SchizoManager: сумма шансов {} вместо 100", sum);
        }
    }

    private SchizoManager() {
    }

    // ================= планировщик =================

    public static void tick(ServerPlayer sp) {
        UUID id = sp.getUUID();
        long now = sp.server.getTickCount();

        List<Task> tasks = TASKS.get(id);
        if (tasks != null && !tasks.isEmpty()) {
            List<Task> due = new ArrayList<>();
            synchronized (tasks) {
                tasks.removeIf(t -> {
                    if (t.at() <= now) {
                        due.add(t);
                        return true;
                    }
                    return false;
                });
            }
            due.forEach(t -> t.run().accept(sp));
        }

        Long deadline = GUN_DEADLINE.get(id);
        if (deadline != null && now >= deadline) {
            GUN_DEADLINE.remove(id);
            DoomedDataUtil.get(sp).ifPresent(d -> gunShot(sp, d));
        }

        if (sp.tickCount % 20 != 0) {
            return;
        }
        DoomedDataUtil.get(sp).ifPresent(d -> {
            if (!d.isActive() || !AddTraits.active(d, AddTrait.HARDCORE_SCHIZOPHRENIA)) {
                NEXT.remove(id);
                return;
            }
            FriendBridge.ensureFriend(sp);
            Long next = NEXT.get(id);
            if (next == null) {
                NEXT.put(id, now + interval(sp, d));
                return;
            }
            if (now < next || !sp.isAlive() || sp.isSleeping() || d.isDowned() || d.isNapping()) {
                return;
            }
            fire(sp, d);
            NEXT.put(id, now + interval(sp, d));
        });
    }

    private static int[] bounds(DoomedData d) {
        float damage = 1.0F - Math.max(0.0F, Math.min(100.0F, d.getBrain())) / 100.0F;
        float t = Math.max(0.0F, Math.min(1.0F, (damage - 0.2F) / 0.6F));
        int lo = Math.round(BASE_MIN + (HURT_MIN - BASE_MIN) * t);
        int hi = Math.round(BASE_MAX + (HURT_MAX - BASE_MAX) * t);
        if (d.getMood() > HAPPY_MOOD) {
            lo += HAPPY_ADD_MIN;
            hi += HAPPY_ADD_MAX;
        }
        return new int[]{lo, Math.max(lo, hi)};
    }

    static long interval(ServerPlayer sp, DoomedData d) {
        int[] b = bounds(d);
        long minTicks = (long) b[0] * TICKS_PER_MIN;
        long maxTicks = (long) b[1] * TICKS_PER_MIN;
        return minTicks + (long) (sp.getRandom().nextDouble() * (maxTicks - minTicks));
    }

    private static void fire(ServerPlayer sp, DoomedData d) {
        List<Entry> pool = new ArrayList<>(TABLE);
        for (int attempt = 0; attempt < 6 && !pool.isEmpty(); attempt++) {
            int total = pool.stream().mapToInt(Entry::weight).sum();
            int roll = sp.getRandom().nextInt(total);
            Entry chosen = pool.get(0);
            for (Entry e : pool) {
                roll -= e.weight();
                if (roll < 0) {
                    chosen = e;
                    break;
                }
            }
            if (chosen.ev().run(sp, d)) {
                return;
            }
            pool.remove(chosen);
        }
    }

    public static void forget(UUID id) {
        NEXT.remove(id);
        TASKS.remove(id);
        GUN_DEADLINE.remove(id);
        RECENT.remove(id);
    }

    // ================= утилиты =================

    private static void later(ServerPlayer sp, int ticks, Consumer<ServerPlayer> run) {
        TASKS.computeIfAbsent(sp.getUUID(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(new Task(sp.server.getTickCount() + ticks, run));
    }

    private static boolean fx(ServerPlayer sp, int type, int duration, int arg) {
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CFxPacket(type, duration, arg));
        return true;
    }

    /** Звук «в голове»: играет на клиенте без позиции, поэтому идёт за игроком. stopTicks = обрыв через N тиков (0 = целиком). */
    static boolean localSound(ServerPlayer sp, String id, float volume, int stopTicks) {
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
            new S2CFxPacket(S2CFxPacket.SOUND, stopTicks, Math.round(volume * 100.0F), id));
        return true;
    }

    private static Vec3 around(ServerPlayer sp, double min, double max) {
        double ang = sp.getRandom().nextDouble() * Math.PI * 2.0;
        double dist = min + sp.getRandom().nextDouble() * (max - min);
        return sp.position().add(Math.cos(ang) * dist, 0.0, Math.sin(ang) * dist);
    }

    private static Vec3 behind(ServerPlayer sp, double dist) {
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        return sp.position().subtract(flat.scale(dist));
    }

    /** Звук в точке мира, слышный только этому игроку. Громкость растёт с дистанцией, чтобы было слышно. */
    private static boolean sound(ServerPlayer sp, String id, Vec3 pos, float volume, float pitch) {
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(id));
        if (ev == null) {
            return false;
        }
        Holder<SoundEvent> holder = ForgeRegistries.SOUND_EVENTS.getHolder(ev).map(h -> (Holder<SoundEvent>) h).orElse(Holder.direct(ev));
        sp.connection.send(new ClientboundSoundPacket(
            holder, SoundSource.PLAYERS, pos.x, pos.y, pos.z, volume, pitch, sp.getRandom().nextLong()
        ));
        return true;
    }

    private static boolean hint(ServerPlayer sp, String text) {
        sp.displayClientMessage(Component.literal(text), true);
        return true;
    }

    /** Случайная фраза без повторов: не берёт то, что друг недавно говорил этому игроку. */
    private static String pick(ServerPlayer sp, String[] arr) {
        Deque<String> recent = RECENT.computeIfAbsent(sp.getUUID(), k -> new ArrayDeque<>());
        List<String> pool = new ArrayList<>();
        for (String s : arr) {
            if (!recent.contains(s)) {
                pool.add(s);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(List.of(arr));
        }
        String chosen = pool.get(sp.getRandom().nextInt(pool.size()));
        recent.addLast(chosen);
        while (recent.size() > 12) {
            recent.removeFirst();
        }
        return chosen;
    }

    // ================= события =================

    private static boolean twoSteps(ServerPlayer sp, DoomedData d) {
        double ang = sp.getRandom().nextDouble() * Math.PI * 2.0;
        Vec3 dir = new Vec3(Math.cos(ang), 0.0, Math.sin(ang));
        for (int i = 0; i < 2; i++) {
            later(sp, i * 8, p -> {
                p.setDeltaMovement(dir.x * 0.42, p.getDeltaMovement().y, dir.z * 0.42);
                p.hurtMarked = true;
            });
        }
        return true;
    }

    private static boolean selfHarm(ServerPlayer sp, DoomedData d) {
        if (d.getDespairTier() != 0) {
            return false;
        }
        DespairHandler.forceEpisode(sp, d, false, false);
        return true;
    }

    private static boolean phantomSounds(ServerPlayer sp, DoomedData d) {
        switch (sp.getRandom().nextInt(4)) {
            case 0 -> {
                Vec3 base = behind(sp, 6);
                for (int i = 0; i < 4; i++) {
                    int step = i;
                    later(sp, i * 9, p -> sound(p, "minecraft:block.stone.step", base.add(0, 0, step * 0.3), 1.6F, 0.9F));
                }
            }
            case 1 -> sound(sp, "doomedmatu:colt_shot_far", around(sp, 24, 34), 3.5F, 1.0F);
            case 2 -> sound(sp, "doomedmatu:mine", around(sp, 3, 6), 1.6F, 1.0F);
            default -> sound(sp, "minecraft:entity.zombie.ambient", around(sp, 10, 14), 2.0F, 0.9F);
        }
        return true;
    }

    private static boolean hasFirearm(ServerPlayer sp) {
        for (ItemStack s : sp.getInventory().items) {
            if (s.isEmpty()) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(s.getItem());
            if (key == null) {
                continue;
            }
            String ns = key.getNamespace();
            String path = key.getPath();
            boolean gunName = path.contains("pistol") || path.contains("revolver") || path.contains("rifle")
                || path.contains("shotgun") || path.contains("colt") || path.endsWith("_gun") || path.contains("smg");
            if ((ns.equals("tacz") && path.contains("gun")) || (ns.equals("doomedguns") && gunName)
                || (ns.equals("doomedmatu") && gunName)) {
                return true;
            }
        }
        return false;
    }

    /** Оружие у виска: любое огнестрельное оружие в инвентаре; 2 секунды, чтобы отдёрнуть руку. */
    private static boolean weaponAtTemple(ServerPlayer sp, DoomedData d) {
        if (!hasFirearm(sp) || GUN_DEADLINE.containsKey(sp.getUUID())) {
            return false;
        }
        GUN_DEADLINE.put(sp.getUUID(), sp.server.getTickCount() + GUN_REACTION_TICKS + (long) GUN_NETWORK_SLACK_TICKS);
        localSound(sp, "doomedmatu:pistol_slide_rear", 0.8F, 0);
        return fx(sp, S2CFxPacket.GUN, GUN_REACTION_TICKS, 0);
    }

    public static void gunResolved(ServerPlayer sp, boolean success) {
        if (success && GUN_DEADLINE.remove(sp.getUUID()) != null) {
            sp.displayClientMessage(Component.literal("Ты отдёргиваешь руку. Никакого оружия у виска не было."), true);
        }
    }

    /** Не успел: сильное (но не смертельное) ранение головы. Упрощённо: урон мозгу, травма и шок. */
    private static void gunShot(ServerPlayer sp, DoomedData d) {
        localSound(sp, "doomedmatu:pistolshot", 1.0F, 0);
        d.setBrain(Math.max(35.0F, d.getBrain() - 20.0F));
        d.addTrauma(15.0F);
        d.addHappiness(-10.0F);
        fx(sp, S2CFxPacket.RED_FLASH, 30, 0);
        fx(sp, S2CFxPacket.SHAKE, 60, 0);
        hint(sp, "Выстрел. Звон в ушах. Ты жив.");
    }

    private static boolean dyspnea(ServerPlayer sp, DoomedData d) {
        localSound(sp, "doomedmatu:vocal_gasp_hard", 1.0F, 0);
        for (int i = 0; i < 5; i++) {
            later(sp, i * 20, p -> DoomedDataUtil.get(p).ifPresent(dd -> dd.setSpO2(Math.min(dd.getSpO2(), 72.0F))));
        }
        return hint(sp, "Тебе нечем дышать!");
    }

    private static boolean waiterSwap(ServerPlayer sp, DoomedData d) {
        String previous = FriendBridge.swapToWaiter(sp, 160);
        if (previous == null) {
            return false;
        }
        later(sp, 600, p -> FriendBridge.restore(p, previous));
        return true;
    }

    private static boolean scream(ServerPlayer sp, DoomedData d) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return false;
        }
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("doomedmatu:vocal_agony"));
        if (ev != null) {
            level.playSound(null, sp.blockPosition(), ev, SoundSource.PLAYERS, 1.4F, 1.0F);
        }
        AABB box = sp.getBoundingBox().inflate(32.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            mob.setTarget(sp);
        }
        return hint(sp, "Ты вскрикиваешь.");
    }

    private static boolean friendInsults(ServerPlayer sp, DoomedData d) {
        if (!FriendBridge.say(sp, pick(sp, INSULTS), 100)) {
            return false;
        }
        later(sp, 260, p -> FriendBridge.say(p, pick(p, INSULTS), 100));
        later(sp, 560, p -> FriendBridge.say(p, pick(p, INSULTS), 100));
        d.addHappiness(-2.0F);
        return true;
    }

    private static boolean friendLine(ServerPlayer sp, DoomedData d, String[] lines, float mood) {
        if (!FriendBridge.say(sp, pick(sp, lines), 100)) {
            return false;
        }
        d.addHappiness(mood);
        return true;
    }

    /** Фантомный удар: красная вспышка и хруст кости. */
    private static boolean phantomHit(ServerPlayer sp, DoomedData d) {
        localSound(sp, BONE_SOUNDS[sp.getRandom().nextInt(BONE_SOUNDS.length)], 1.0F, 0);
        return fx(sp, S2CFxPacket.RED_FLASH, 10, 0);
    }

    /** Фантомная боль: реально поднимает боль в случайной конечности до ~30% шкалы (потом спадает как обычно). */
    private static boolean phantomPain(ServerPlayer sp, DoomedData d) {
        BodyPart part = PHANTOM_LIMBS[sp.getRandom().nextInt(PHANTOM_LIMBS.length)];
        d.getLimb(part).setPain(Math.max(d.getLimb(part).getPain(), PHANTOM_PAIN_LEVEL));
        localSound(sp, "doomedmatu:vocal_gasp1", 0.6F, 0);
        fx(sp, S2CFxPacket.SHAKE, 40, 0);
        return hint(sp, "Фантомная боль...");
    }

    private static boolean whispers(ServerPlayer sp, DoomedData d) {
        localSound(sp, "doomedmatu:deep_maw_mimic_whisper", 0.7F, 30);
        return hint(sp, pick(sp, WHISPERS));
    }

    private static boolean heartbeat(ServerPlayer sp, DoomedData d) {
        localSound(sp, "doomedmatu:tinnitus", 0.6F, 100);
        for (int i = 0; i < 8; i++) {
            later(sp, i * 24, p -> localSound(p, "doomedmatu:heartthump_heavy", 0.9F, 0));
        }
        return true;
    }

    private static boolean dropItem(ServerPlayer sp, DoomedData d) {
        if (sp.getMainHandItem().isEmpty()) {
            return false;
        }
        sp.drop(false);
        return hint(sp, "Предмет выскользнул из руки.");
    }

    /** Депрессивный провал: -50 к итоговому настроению на 10-15 секунд, настоящее настроение продолжает меняться. */
    private static boolean depressiveDip(ServerPlayer sp, DoomedData d) {
        int ticks = 200 + sp.getRandom().nextInt(101);
        MoodEffects.addTempMood(d, DEPRESSION_MOOD, ticks);
        fx(sp, S2CFxPacket.DEPRESSION, ticks, 0);
        localSound(sp, "doomedmatu:heartthump", 0.5F, 0);
        return hint(sp, "Всё вдруг кажется бессмысленным...");
    }

    private static boolean fallingDown(ServerPlayer sp, DoomedData d) {
        if (d.isDowned()) {
            return false;
        }
        float shock0 = d.getShock();
        double ang = sp.getRandom().nextDouble() * Math.PI * 2.0;
        Ragdoll.fling(sp, Math.cos(ang) * 0.25, 0.15, Math.sin(ang) * 0.25);
        localSound(sp, "minecraft:entity.player.big_fall", 0.9F, 0);
        fx(sp, S2CFxPacket.SHAKE, 30, 0);
        // Ragdoll.fling поднимает шок; через ~3 секунды возвращаем прежний, чтобы Doomed сам поднял игрока.
        later(sp, 60, p -> DoomedDataUtil.get(p).ifPresent(dd -> {
            if (dd.getShock() <= 25.0F) {
                dd.setShock(Math.min(dd.getShock(), Math.max(shock0, 5.0F)));
            }
        }));
        return hint(sp, "Ноги подкашиваются, и ты падаешь...");
    }

    private static boolean followed(ServerPlayer sp, DoomedData d) {
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        Vec3 start = sp.position();
        for (int i = 0; i < 8; i++) {
            double dist = 9.0 - i;
            later(sp, i * 12, p -> sound(p, "minecraft:block.stone.step", start.subtract(dir.scale(dist)), 1.4F, 0.85F));
        }
        return true;
    }

    /** Рация: только случайные координаты в личном чате игрока (другие ничего не видят). */
    private static boolean radioCoordinates(ServerPlayer sp, DoomedData d) {
        int bx = sp.getBlockX() + (sp.getRandom().nextBoolean() ? 1 : -1) * (120 + sp.getRandom().nextInt(300));
        int bz = sp.getBlockZ() + (sp.getRandom().nextBoolean() ? 1 : -1) * (120 + sp.getRandom().nextInt(300));
        localSound(sp, "doomedmatu:staticdying1", 0.45F, 40);
        sp.displayClientMessage(Component.literal("[Рация] X: " + bx + ", Z: " + bz), false);
        return true;
    }

    /** Тёплое воспоминание: +4 к настроению, сообщение и 25-й кадр с закатом. */
    private static boolean warmMemory(ServerPlayer sp, DoomedData d) {
        d.addHappiness(4.0F);
        fx(sp, S2CFxPacket.IMAGE, 1, 1);
        localSound(sp, "doomedmatu:vocal_gasp_exhale", 0.4F, 0);
        return hint(sp, pick(sp, MEMORIES));
    }

    /** Не тот моб: ближайший моб в 24 блоках визуально превращается в другого (подмену рисует клиент). */
    private static boolean wrongMob(ServerPlayer sp, DoomedData d) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return false;
        }
        AABB box = sp.getBoundingBox().inflate(24.0);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && sp.hasLineOfSight(m));
        if (mobs.isEmpty()) {
            return false;
        }
        Mob nearest = mobs.get(0);
        for (Mob m : mobs) {
            if (m.distanceToSqr(sp) < nearest.distanceToSqr(sp)) {
                nearest = m;
            }
        }
        ModNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new S2CFxPacket(S2CFxPacket.WRONG_MOB, 400, nearest.getId()));
        return true;
    }

    /** Ошибка памяти: в чате (только у этого игрока) появляется «твоя» реплика, которую ты не писал. */
    private static boolean memoryError(ServerPlayer sp, DoomedData d) {
        sp.displayClientMessage(Component.translatable("chat.type.text", sp.getDisplayName(), Component.literal(pick(sp, MEMORY_ERRORS))), false);
        return true;
    }

    /** Ты уже умер: поддельный экран смерти Doomed (со звуками) на несколько секунд. */
    private static boolean alreadyDead(ServerPlayer sp, DoomedData d) {
        int cause = 1 + sp.getRandom().nextInt(3);
        DoomedNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
            new S2CDeathScreenPacket(cause, sp.tickCount, List.of(), false));
        return fx(sp, S2CFxPacket.DEATH_CLOSE, 90, 0);
    }

    // ================= отладка (команды /dta) =================

    public static boolean fireById(ServerPlayer sp, DoomedData d, String id) {
        for (Entry e : TABLE) {
            if (e.id().equals(id)) {
                return e.ev().run(sp, d);
            }
        }
        return false;
    }

    public static List<String> eventIds() {
        List<String> ids = new ArrayList<>();
        for (Entry e : TABLE) {
            ids.add(e.id());
        }
        return ids;
    }

    public static int weightOf(String id) {
        for (Entry e : TABLE) {
            if (e.id().equals(id)) {
                return e.weight();
            }
        }
        return 0;
    }

    /** Через сколько секунд следующее событие, или -1, если таймер не запущен. */
    public static long secondsUntilNext(ServerPlayer sp) {
        Long next = NEXT.get(sp.getUUID());
        return next == null ? -1 : Math.max(0, (next - sp.server.getTickCount()) / 20);
    }

    public static void setNextInSeconds(ServerPlayer sp, int seconds) {
        NEXT.put(sp.getUUID(), sp.server.getTickCount() + seconds * 20L);
    }

    public static String intervalInfo(ServerPlayer sp, DoomedData d) {
        int[] b = bounds(d);
        return b[0] + "-" + b[1] + " мин (мозг " + Math.round(d.getBrain()) + "%, настроение " + Math.round(d.getMood()) + ")";
    }
}

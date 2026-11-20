package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.mattlives.doomedmatu.network.DoomedNetwork;
import net.mattlives.doomedmatu.network.S2CImaginaryFriendLinePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Мост к «пушистому другу» Doomed. Его классы пакетные (package-private), поэтому через рефлексию.
 * Если что-то изменится в новой версии Doomed - просто вернёт false и событие заменится сообщением.
 */
public final class FriendBridge {
    public static final String WAITER_ID = "doomedmatu:the_one_who_waits";
    private static final String BODY = "net.mattlives.doomedmatu.body.";
    private static boolean warned;

    private FriendBridge() {
    }

    private static Class<?> cls(ServerPlayer sp, String name) throws ClassNotFoundException {
        return Class.forName(BODY + name, false, sp.getClass().getClassLoader());
    }

    private static Object call(Object target, String method) throws Exception {
        Method m = target.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Object> states(ServerPlayer sp) throws Exception {
        Field f = cls(sp, "ImaginaryFriend").getDeclaredField("states");
        f.setAccessible(true);
        return (Map<UUID, Object>) f.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, Object> friends(ServerPlayer sp) throws Exception {
        Field f = cls(sp, "ImaginaryFriend").getDeclaredField("friends");
        f.setAccessible(true);
        return (Map<ResourceLocation, Object>) f.get(null);
    }

    private static Field friendField(Object state) throws Exception {
        Field f = state.getClass().getDeclaredField("friend");
        f.setAccessible(true);
        return f;
    }

    /** Реплика текущего друга игрока. */
    public static boolean say(ServerPlayer sp, String text, int durationTicks) {
        try {
            Object state = states(sp).get(sp.getUUID());
            if (state == null) {
                return false;
            }
            ResourceLocation id = ResourceLocation.tryParse((String) friendField(state).get(state));
            Object def = id == null ? null : friends(sp).get(id);
            return def != null && send(sp, def, text, durationTicks);
        } catch (Throwable t) {
            warn(t);
            return false;
        }
    }

    /** Пушистый друг (Fur): постоянный друг Хардкорной шизофрении. */
    public static final String FUR_ID = "doomedmatu:fur";
    private static final java.util.Set<UUID> SWAPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Следит, чтобы у хардкорного шизофреника был именно пушистый друг (а не случайный друг Doomed),
     * пока не идёт временная подмена на «Того, кто ждёт».
     */
    public static void ensureFriend(ServerPlayer sp) {
        if (SWAPPED.contains(sp.getUUID())) {
            return;
        }
        try {
            Object state = states(sp).get(sp.getUUID());
            ResourceLocation fur = ResourceLocation.tryParse(FUR_ID);
            if (state == null || fur == null || !friends(sp).containsKey(fur)) {
                return;
            }
            Field f = friendField(state);
            if (!FUR_ID.equals(f.get(state))) {
                f.set(state, FUR_ID);
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    /**
     * Друг на одну реплику превращается в «Того, кто ждёт»; возвращает id друга, к которому нужно вернуться
     * (или null, если подмена невозможна). Вернуть обратно нужно через restore().
     */
    public static String swapToWaiter(ServerPlayer sp, int durationTicks) {
        try {
            Object state = states(sp).get(sp.getUUID());
            ResourceLocation waiter = ResourceLocation.tryParse(WAITER_ID);
            Object def = waiter == null ? null : friends(sp).get(waiter);
            if (state == null || def == null) {
                return null;
            }
            String text = randomLine(sp, def);
            if (text == null) {
                return null;
            }
            Field f = friendField(state);
            String previous = (String) f.get(state);
            if (previous == null || previous.isEmpty() || WAITER_ID.equals(previous)) {
                previous = FUR_ID;
            }
            f.set(state, WAITER_ID);
            SWAPPED.add(sp.getUUID());
            try {
                send(sp, def, text, durationTicks);
            } catch (Throwable t) {
                warn(t);
            }
            return previous;
        } catch (Throwable t) {
            warn(t);
            return null;
        }
    }

    public static void restore(ServerPlayer sp, String previousId) {
        SWAPPED.remove(sp.getUUID());
        try {
            Object state = states(sp).get(sp.getUUID());
            if (state != null && previousId != null) {
                friendField(state).set(state, previousId);
            }
        } catch (Throwable t) {
            warn(t);
        }
    }

    public static void forget(UUID id) {
        SWAPPED.remove(id);
    }

    @SuppressWarnings("unchecked")
    private static String randomLine(ServerPlayer sp, Object def) throws Exception {
        Map<Object, List<Object>> lines = (Map<Object, List<Object>>) call(def, "lines");
        List<String> texts = new ArrayList<>();
        for (List<Object> list : lines.values()) {
            for (Object line : list) {
                Object t = call(line, "text");
                if (t instanceof String s && !s.isBlank()) {
                    texts.add(s);
                }
            }
        }
        return texts.isEmpty() ? null : texts.get(sp.getRandom().nextInt(texts.size()));
    }

    private static boolean send(ServerPlayer sp, Object def, String text, int durationTicks) throws Exception {
        ResourceLocation id = (ResourceLocation) call(def, "id");
        String name = FUR_ID.equals(id.toString()) ? "Пушистик" : (String) call(def, "name");
        DoomedNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> sp),
            new S2CImaginaryFriendLinePacket(id, name, "dta_" + System.nanoTime(), text, null, durationTicks)
        );
        Object appearance = call(def, "appearance");
        if (appearance == null) {
            return true; // у друга нет тела (например, «Тот, кто ждёт»): только голос и текст
        }
        Method show = cls(sp, "ImaginaryFriendApparitions").getDeclaredMethod(
            "show", ServerPlayer.class, appearance.getClass(), int.class
        );
        show.setAccessible(true);
        show.invoke(null, sp, appearance, durationTicks);
        return true;
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            DoomedTraitsAdditions.LOGGER.warn("FriendBridge: не удалось обратиться к «пушистому другу» Doomed", t);
        }
    }
}

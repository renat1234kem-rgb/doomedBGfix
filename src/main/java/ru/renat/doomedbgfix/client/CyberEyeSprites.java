package ru.renat.doomedbgfix.client;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.client.ClientDoomedData;
import net.mattlives.doomedmatu.client.puppet.PuppetSprites;
import net.mattlives.doomedmatu.client.screen.WitnessPatientScanScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import ru.renat.doomedbgfix.DoomedBGFix;
import ru.renat.doomedbgfix.eye.EyeImplantHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Подбирает спрайт кибернетического глаза для панели здоровья (только клиент).
 * Текстуры лежат в assets/doomedbgfix/textures/gui/[alt/]eye_left_cyber.png,
 * eye_right_cyber.png и запасной eye_open_cyber.png, "alt/" соответствует DollStyle.ALT.
 */
public final class CyberEyeSprites {

    private static final int EYE_W = 26;
    private static final int EYE_H = 12;

    /** Кэш по ключу "стиль + файл"; Optional.empty() означает "текстуры нет", чтобы не искать её каждый кадр. */
    private static final Map<String, Optional<PuppetSprites.BodySpr>> CACHE = new HashMap<>();

    private CyberEyeSprites() {}

    /** @return спрайт импланта или null, если на этом глазу импланта нет (тогда Doomed рисует свой). */
    public static PuppetSprites.BodySpr forEye(BodyPart part, String styleDir) {
        if (part != BodyPart.EYE_LEFT && part != BodyPart.EYE_RIGHT) return null;
        // При осмотре другого игрока (Witness) ClientDoomedData описывает НАС, а не пациента,
        // поэтому свой имплант там показывать нельзя.
        if (Minecraft.getInstance().screen instanceof WitnessPatientScanScreen) return null;
        if (ClientDoomedData.isLimbDismembered(part)) return null;
        if (ClientDoomedData.getLimbProstheticTier(part) != EyeImplantHandler.IMPLANT_TIER) return null;

        String dir = styleDir == null ? "" : styleDir;
        String name = part == BodyPart.EYE_RIGHT ? "eye_right_cyber" : "eye_left_cyber";
        return CACHE.computeIfAbsent(dir + name, k -> load(dir, name)).orElse(null);
    }

    private static Optional<PuppetSprites.BodySpr> load(String dir, String name) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        String[] candidates = { dir + name, name, dir + "eye_open_cyber", "eye_open_cyber" };
        for (String c : candidates) {
            ResourceLocation rl = new ResourceLocation(DoomedBGFix.MODID, "textures/gui/" + c + ".png");
            if (rm.getResource(rl).isPresent()) {
                return Optional.of(new PuppetSprites.BodySpr(rl, rl, EYE_W, EYE_H));
            }
        }
        return Optional.empty();
    }

    /** Вызывается при перезагрузке ресурсов, чтобы не держать устаревшие ответы. */
    public static void clear() {
        CACHE.clear();
    }
}

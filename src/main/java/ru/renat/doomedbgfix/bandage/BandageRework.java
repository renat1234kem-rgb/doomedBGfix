package ru.renat.doomedbgfix.bandage;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundRecord;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.server.CutBridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bandages in the Casualties: Unknown style: every wrap step stops a FIXED
 * amount of bleeding (delta progress * capacity * quality), not a percentage.
 * A dressing has a capacity (base * quality): once it absorbed that much blood
 * it is soaked and further wrapping does nothing until a new dressing is used.
 * The vanilla 100%-wrap percentage effect is untouched and stays as the
 * completion bonus on top (plus optional fullWrapBonusUnits).
 */
public final class BandageRework {

    private static final Map<String, Float> LAST_PROGRESS = new ConcurrentHashMap<>();
    private static final Map<String, Float> SPENT = new ConcurrentHashMap<>();

    private BandageRework() {}

    public static void onBandageProgress(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                         float targetProgress, boolean finished, int itemSlot) {
        if (!BGConfig.bandageProgressive()) return;
        if (patient == null) return;

        DoomedData data = CutBridge.data(patient);
        if (data == null || !data.isActive()) return;
        LimbData limb = data.getLimb(part);
        if (limb == null || limb.isDismembered()) return;

        WoundRecord wound = WoundLedgerService.selectedOrUrgentDressable(data, part);
        if (wound == null) return;

        float quality = BandageProfiles.quality(itemPath(healer, itemSlot));
        String key = patient.getUUID() + "|" + part.name() + "|" + wound.id();

        if (finished) {
            stopBleed(limb, wound, BGConfig.bandageFullWrapBonusUnits() * quality);
            LAST_PROGRESS.remove(key);
            SPENT.remove(key);
            DoomedDataUtil.sync(patient);
            return;
        }

        float last = LAST_PROGRESS.getOrDefault(key, 0f);
        // progress went backwards: a new dressing session started, reset soak
        if (targetProgress < last - 0.001f) {
            last = 0f;
            SPENT.remove(key);
        }
        float delta = targetProgress - last;
        if (delta <= 0.0001f) return;
        LAST_PROGRESS.put(key, targetProgress);

        float capacity = BGConfig.bandageCapacityBase() * quality;
        float spent = SPENT.getOrDefault(key, 0f);
        float room = Math.max(0f, capacity - spent);
        if (room <= 0f) return;

        float stop = Math.min(room, delta * capacity);
        if (stop <= 0f) return;
        SPENT.put(key, spent + stop);

        stopBleed(limb, wound, stop);
        DoomedDataUtil.sync(patient);
    }

    private static void stopBleed(LimbData limb, WoundRecord wound, float amount) {
        if (amount <= 0f) return;
        float before = wound.bleeding();
        if (before <= 0f) return;
        wound.setBleeding(Math.max(0f, before - amount));
        WoundLedgerService.applyProjection(limb);
    }

    private static String itemPath(ServerPlayer healer, int itemSlot) {
        if (healer == null) return "";
        Inventory inv = healer.getInventory();
        if (inv != null && itemSlot >= 0 && itemSlot < inv.getContainerSize()) {
            ItemStack stack = inv.getItem(itemSlot);
            if (stack != null && !stack.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) return id.getPath();
            }
        }
        ItemStack main = healer.getMainHandItem();
        if (main == null || main.isEmpty()) return "";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(main.getItem());
        return id == null ? "" : id.getPath();
    }
}
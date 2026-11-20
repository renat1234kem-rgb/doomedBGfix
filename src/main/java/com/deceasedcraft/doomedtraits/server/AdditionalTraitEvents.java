package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.mattlives.doomedmatu.body.VomitHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.config.DoomedConfig;
import net.mattlives.doomedmatu.entity.DoomedTrader;
import net.mattlives.doomedmatu.network.DoomedNetwork;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Серверные эффекты: лактоза, вегетарианство, социофобия, заядлый курильщик, аура оптимиста, шизофрения. */
@Mod.EventBusSubscriber(modid = DoomedTraitsAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AdditionalTraitEvents {
    // --- вегетарианство ---
    private static final float VEGETARIAN_MOOD_HIT = -10.0F;
    private static final float VEGETARIAN_MOOD_HIT_ANAEMIC = -5.0F;
    private static final float VEGETARIAN_VOMIT_CHANCE_ANAEMIC = 0.20F;
    private static final float VEGETARIAN_RBC_THRESHOLD = 0.75F;

    // --- социофобия ---
    private static final double SOCIAL_RADIUS = 6.0;
    private static final float SOCIAL_DRAIN_PER_SECOND = 0.06F;
    private static final float SOCIAL_MOOD_FLOOR = -45.0F;

    // --- заядлый курильщик ---
    private static final int SMOKE_EVERY_TICKS = 5 * 60 * 20;
    private static final float SMOKE_DEPENDENCE_FLOOR = 30.0F;
    /** Какая доля смолы от обычного всё же оседает в лёгких. */
    private static final float TAR_KEPT = 0.10F;
    /** Раз в сколько секунд лёгкие немного очищаются, и на сколько. */
    private static final int TAR_CLEAN_EVERY_SECONDS = 120;
    private static final float TAR_CLEAN_AMOUNT = 0.02F;
    private static final float CIGARETTE_TAR = 0.3F * TAR_KEPT;

    // --- аура оптимиста ---
    private static final double AURA_RADIUS = 6.0;

    private static final Map<UUID, Long> LAST_SMOKE = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> LAST_TAR = new ConcurrentHashMap<>();

    private AdditionalTraitEvents() {
    }

    // ================= еда =================

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!FoodClassifier.isConsumable(stack)) {
            return;
        }
        DoomedDataUtil.get(sp).ifPresent(d -> {
            if (!d.isActive()) {
                return;
            }
            if (AddTraits.active(d, AddTrait.LACTOSE_INTOLERANCE) && FoodClassifier.isDairy(stack)) {
                VomitHandler.triggerVomit(sp, d);
            }
            if (AddTraits.active(d, AddTrait.VEGETARIAN) && FoodClassifier.isMeat(stack)) {
                applyMeatReaction(sp, d);
            }
        });
    }

    /** Торт ест не через предмет, а правым кликом по блоку. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().isClientSide()) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(sp.level().getBlockState(event.getPos()).getBlock() instanceof CakeBlock) || !sp.canEat(false)) {
            return;
        }
        DoomedDataUtil.get(sp).ifPresent(d -> {
            if (d.isActive() && AddTraits.active(d, AddTrait.LACTOSE_INTOLERANCE)) {
                VomitHandler.triggerVomit(sp, d);
            }
        });
    }

    private static void applyMeatReaction(ServerPlayer sp, DoomedData d) {
        boolean anaemic = d.getRedCellFraction() < VEGETARIAN_RBC_THRESHOLD;
        if (!anaemic) {
            VomitHandler.triggerVomit(sp, d);
            d.addHappiness(VEGETARIAN_MOOD_HIT);
        } else {
            d.addHappiness(VEGETARIAN_MOOD_HIT_ANAEMIC);
            if (sp.getRandom().nextFloat() < VEGETARIAN_VOMIT_CHANCE_ANAEMIC) {
                VomitHandler.triggerVomit(sp, d);
            }
        }
    }

    // ================= тик игрока =================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer sp)) {
            return;
        }
        SchizoManager.tick(sp);
        if (sp.tickCount % 20 != 0 || !sp.isAlive() || sp.isSpectator()) {
            return;
        }
        DoomedDataUtil.get(sp).ifPresent(d -> {
            if (!d.isActive()) {
                return;
            }
            MoodEffects.tickSecond(sp, d);
            socialPhobia(sp, d);
            chainSmoker(sp, d);
            optimistAura(sp, d);
        });
    }

    private static void socialPhobia(ServerPlayer sp, DoomedData d) {
        if (!AddTraits.active(d, AddTrait.SOCIOPHOBIA) || d.getMood() <= SOCIAL_MOOD_FLOOR) {
            return;
        }
        if (isCrowded(sp)) {
            d.addHappinessRaw(-SOCIAL_DRAIN_PER_SECOND * (float) DoomedConfig.paceFactor());
        }
    }

    /** Рядом (~6 блоков) другой игрок или торговец Doomed. */
    private static boolean isCrowded(ServerPlayer sp) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return false;
        }
        AABB box = sp.getBoundingBox().inflate(SOCIAL_RADIUS);
        double maxSq = SOCIAL_RADIUS * SOCIAL_RADIUS;
        List<Player> players = level.getEntitiesOfClass(
            Player.class, box, p -> p != sp && p.isAlive() && !p.isSpectator() && p.distanceToSqr(sp) <= maxSq
        );
        if (!players.isEmpty()) {
            return true;
        }
        List<DoomedTrader> traders = level.getEntitiesOfClass(
            DoomedTrader.class, box, t -> t.isAlive() && t.distanceToSqr(sp) <= maxSq
        );
        return !traders.isEmpty();
    }

    // ================= заядлый курильщик =================

    private static void chainSmoker(ServerPlayer sp, DoomedData d) {
        UUID id = sp.getUUID();
        if (!AddTraits.active(d, AddTrait.CHAIN_SMOKER)) {
            LAST_SMOKE.remove(id);
            LAST_TAR.remove(id);
            return;
        }
        long now = sp.server.getTickCount();
        if (d.getNicotineDependence() < SMOKE_DEPENDENCE_FLOOR) {
            d.setNicotineDependence(SMOKE_DEPENDENCE_FLOOR);
        }

        // Смола: оседает только малая доля, и время от времени лёгкие чистятся.
        float tar = d.getLungTar();
        Float prev = LAST_TAR.get(id);
        if (prev != null && tar > prev) {
            tar = prev + (tar - prev) * TAR_KEPT;
        }
        if (sp.tickCount % (TAR_CLEAN_EVERY_SECONDS * 20) == 0) {
            tar = Math.max(0.0F, tar - TAR_CLEAN_AMOUNT);
        }
        if (tar != d.getLungTar()) {
            d.setLungTar(tar);
        }
        LAST_TAR.put(id, tar);

        Long last = LAST_SMOKE.get(id);
        if (last == null) {
            LAST_SMOKE.put(id, now);
            return;
        }
        if (now - last < SMOKE_EVERY_TICKS) {
            return;
        }
        LAST_SMOKE.put(id, now);
        smokeNow(sp, d);
    }

    /** Выкурить сигарету сейчас (для таймера и для отладки). */
    public static boolean smokeNow(ServerPlayer sp, DoomedData d) {
        UUID id = sp.getUUID();
        ItemStack cig = findItem(sp, DoomedItems.CIGARETTE.get());
        boolean hasLight = !findItem(sp, DoomedItems.LIGHTER.get()).isEmpty() || !findItem(sp, Items.FLINT_AND_STEEL).isEmpty();
        if (cig.isEmpty() || !hasLight) {
            sp.displayClientMessage(Component.literal("Тебя тянет закурить, но курить нечем."), true);
            return false;
        }
        cig.shrink(1);
        d.setSmokingTicks(800);
        DoomedNetwork.broadcastSmoking(sp, 800);
        d.addHappiness(2.0F);
        d.setNicotineDependence(Math.min(100.0F, d.getNicotineDependence() + 2.0F));
        d.setNicotineSated(8.0F * 60.0F);
        d.setLungTar(d.getLungTar() + CIGARETTE_TAR);
        LAST_TAR.put(id, d.getLungTar());
        sp.displayClientMessage(Component.literal("Рука сама тянется к сигарете."), true);
        return true;
    }

    private static ItemStack findItem(ServerPlayer sp, net.minecraft.world.item.Item item) {
        for (ItemStack s : sp.getInventory().items) {
            if (!s.isEmpty() && s.is(item)) {
                return s;
            }
        }
        for (ItemStack s : sp.getInventory().offhand) {
            if (!s.isEmpty() && s.is(item)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    // ================= аура оптимиста =================

    private static void optimistAura(ServerPlayer sp, DoomedData d) {
        if (!(sp.level() instanceof ServerLevel level)) {
            return;
        }
        double maxSq = AURA_RADIUS * AURA_RADIUS;
        AABB box = sp.getBoundingBox().inflate(AURA_RADIUS);
        List<ServerPlayer> near = level.getEntitiesOfClass(
            ServerPlayer.class, box, p -> p != sp && p.isAlive() && !p.isSpectator() && p.distanceToSqr(sp) <= maxSq
        );
        boolean optimistNearby = false;
        for (ServerPlayer p : near) {
            if (DoomedDataUtil.get(p).map(pd -> pd.isActive() && AddTraits.active(pd, AddTrait.OPTIMIST)).orElse(false)) {
                optimistNearby = true;
                break;
            }
        }
        if (optimistNearby) {
            AddTraits.OPTIMIST_AURA.add(d);
        } else {
            AddTraits.OPTIMIST_AURA.remove(d);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_SMOKE.remove(id);
        LAST_TAR.remove(id);
        SchizoManager.forget(id);
        FriendBridge.forget(id);
        Draft.forget(id);
    }
}

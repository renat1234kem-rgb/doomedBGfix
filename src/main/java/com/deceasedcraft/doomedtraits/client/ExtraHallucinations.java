package com.deceasedcraft.doomedtraits.client;

import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import com.deceasedcraft.doomedtraits.net.S2CFxPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.mattlives.doomedmatu.client.DeathScreenGuard;
import net.mattlives.doomedmatu.client.screen.DeathScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Клиентские галлюцинации «второй волны»: забывчивость, мерцание, подмена моба, двойник,
 * искажённый мир, заложенные уши, «ты уже умер».
 */
@Mod.EventBusSubscriber(modid = DoomedTraitsAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ExtraHallucinations {
    private static final RandomSource RNG = RandomSource.create();
    private static final int DOPPEL_ID = -424242;

    private static long now;

    // забывчивость
    private static long forgetUntil;
    private static final Map<Item, Component> FORGET_NAMES = new HashMap<>();
    private static long forgetRefreshAt;
    private static int lastSlot = -1;
    private static long slotChangedAt;
    private static List<Item> allItems;

    // мерцание
    private static long flickerUntil;

    // подмена моба
    private static long wrongUntil;
    private static int wrongTargetId = -1;
    private static Entity wrongDummy;

    // двойник
    private static long doppelUntil;
    private static RemotePlayer doppel;

    // искажённый мир
    private static long distortUntil;
    private static final String[] DISTORT_CHAINS = {"distort_a", "distort_b", "distort_c"};
    private static final int[] DISTORT_ORDER = {0, 1, 2, 1, 0, 2};
    private static PostChain[] distortChains;
    private static boolean distortFailed;
    private static int chainW = -1;
    private static int chainH = -1;

    // заложенные уши
    private static long muffleUntil;

    // «ты уже умер»
    private static long deathCloseAt;

    private ExtraHallucinations() {
    }

    public static void handle(int type, int duration, int arg) {
        Minecraft mc = Minecraft.getInstance();
        switch (type) {
            case S2CFxPacket.FORGET -> {
                forgetUntil = now + duration;
                FORGET_NAMES.clear();
                slotChangedAt = now;
            }
            case S2CFxPacket.FLICKER -> flickerUntil = now + duration;
            case S2CFxPacket.WRONG_MOB -> startWrongMob(mc, duration, arg);
            case S2CFxPacket.DOPPELGANGER -> startDoppelganger(mc, duration);
            case S2CFxPacket.DISTORT -> {
                distortUntil = now + duration;
                distortFailed = false;
            }
            case S2CFxPacket.MUFFLE -> {
                muffleUntil = now + duration;
                mc.getSoundManager().updateSourceVolume(SoundSource.MASTER, 0.0F);
            }
            case S2CFxPacket.DEATH_CLOSE -> deathCloseAt = now + duration;
            default -> {
            }
        }
    }

    // ================= тик =================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        now++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        // забывчивость: меняем случайные имена и отслеживаем смену слота
        if (now < forgetUntil) {
            if (now >= forgetRefreshAt) {
                FORGET_NAMES.clear();
                forgetRefreshAt = now + 60;
            }
            int slot = mc.player.getInventory().selected;
            if (slot != lastSlot) {
                lastSlot = slot;
                slotChangedAt = now;
            }
        }

        // заложенные уши: вернуть звук
        if (muffleUntil != 0 && now >= muffleUntil) {
            muffleUntil = 0;
            restoreSound(mc);
        }

        // подмена моба
        if (wrongDummy != null) {
            Entity real = mc.level.getEntity(wrongTargetId);
            boolean hurtNearby = real != null && mc.player.hurtTime > 8 && mc.player.distanceTo(real) < 6.0F;
            if (now >= wrongUntil || real == null || !real.isAlive() || hurtNearby) {
                wrongDummy = null;
                wrongTargetId = -1;
            } else {
                syncDummy(real);
            }
        }

        // двойник
        if (doppel != null) {
            if (now >= doppelUntil || mc.player.distanceTo(doppel) < 10.0F) {
                removeDoppel(mc);
            } else {
                lookAtPlayer(mc);
            }
        }

        // «ты уже умер»: закрываем экран смерти через несколько секунд
        if (deathCloseAt != 0 && now >= deathCloseAt) {
            deathCloseAt = 0;
            if (mc.screen instanceof DeathScreen && !mc.player.isDeadOrDying()) {
                DeathScreenGuard.disarm();
                mc.setScreen(null);
                for (String s : new String[]{"deathmusic", "death", "flatline"}) {
                    mc.getSoundManager().stop(new ResourceLocation("doomedmatu", s), null);
                }
            }
        }

        // искажённый мир: закончился - освободить цепочку
        if (distortChains != null && now >= distortUntil) {
            closeChain();
        }
    }

    private static void restoreSound(Minecraft mc) {
        mc.getSoundManager().updateSourceVolume(SoundSource.MASTER, mc.options.getSoundSourceVolume(SoundSource.MASTER));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft mc = Minecraft.getInstance();
        if (muffleUntil != 0) {
            muffleUntil = 0;
            restoreSound(mc);
        }
        wrongDummy = null;
        doppel = null;
        closeChain();
        forgetUntil = 0;
        flickerUntil = 0;
        distortUntil = 0;
        deathCloseAt = 0;
    }

    // ================= забывчивость =================

    private static Component randomName() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (Item i : ForgeRegistries.ITEMS) {
                if (i != net.minecraft.world.item.Items.AIR) {
                    allItems.add(i);
                }
            }
        }
        if (allItems.isEmpty()) {
            return Component.literal("???");
        }
        return new ItemStack(allItems.get(RNG.nextInt(allItems.size()))).getHoverName();
    }

    private static Component forgottenName(Item item) {
        return FORGET_NAMES.computeIfAbsent(item, k -> randomName());
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (now < forgetUntil && !event.getToolTip().isEmpty()) {
            event.getToolTip().set(0, forgottenName(event.getItemStack().getItem()));
        }
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (now >= forgetUntil || event.getOverlay() != VanillaGuiOverlay.ITEM_NAME.type()) {
            return;
        }
        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || now - slotChangedAt > 80) {
            return;
        }
        ItemStack held = mc.player.getInventory().getSelected();
        if (held.isEmpty()) {
            return;
        }
        Component name = forgottenName(held.getItem());
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int y = h - 59 + (mc.gameMode != null && !mc.gameMode.canHurtPlayer() ? 14 : 0);
        event.getGuiGraphics().drawString(mc.font, name, (w - mc.font.width(name)) / 2, y, 0xFFFFFF, true);
    }

    // ================= мерцание =================

    @SubscribeEvent
    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (now >= flickerUntil) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        var gg = event.getGuiGraphics();
        if (RNG.nextFloat() < 0.4F) {
            gg.fill(0, 0, w, h, (RNG.nextBoolean() ? 0x16FFFFFF : 0x22000000));
        }
        for (int i = 0; i < 26; i++) {
            if (RNG.nextFloat() < 0.55F) {
                continue;
            }
            int rw = 8 + RNG.nextInt(70);
            int rh = 8 + RNG.nextInt(60);
            int x = RNG.nextInt(Math.max(1, w - rw));
            int y = RNG.nextInt(Math.max(1, h - rh));
            int a = 0x20 + RNG.nextInt(0x50);
            gg.fill(x, y, x + rw, y + rh, (a << 24) | (RNG.nextBoolean() ? 0xFFFFFF : 0x000000));
        }
    }

    // ================= не тот моб =================

    private static void startWrongMob(Minecraft mc, int duration, int targetId) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        Entity real = mc.level.getEntity(targetId);
        if (!(real instanceof LivingEntity)) {
            return;
        }
        Entity dummy = null;
        int pick = RNG.nextInt(9);
        if (pick == 0 || real.getType() == EntityType.ZOMBIE && pick < 4) {
            dummy = new RemotePlayer(mc.level, mc.player.getGameProfile());
        } else {
            EntityType<?>[] types = {EntityType.CREEPER, EntityType.VILLAGER, EntityType.PIG, EntityType.SKELETON,
                EntityType.COW, EntityType.ENDERMAN, EntityType.IRON_GOLEM, EntityType.SPIDER};
            EntityType<?> t = types[RNG.nextInt(types.length)];
            if (t == real.getType()) {
                t = EntityType.VILLAGER;
            }
            dummy = t.create(mc.level);
        }
        if (dummy == null) {
            return;
        }
        wrongDummy = dummy;
        wrongTargetId = targetId;
        wrongUntil = now + duration;
        syncDummy(real);
    }

    private static void syncDummy(Entity real) {
        Entity d = wrongDummy;
        if (d == null) {
            return;
        }
        d.setPos(real.getX(), real.getY(), real.getZ());
        d.xo = real.xo;
        d.yo = real.yo;
        d.zo = real.zo;
        d.xOld = real.xOld;
        d.yOld = real.yOld;
        d.zOld = real.zOld;
        d.setYRot(real.getYRot());
        d.yRotO = real.yRotO;
        d.setXRot(real.getXRot());
        d.xRotO = real.xRotO;
        d.tickCount = real.tickCount;
        if (d instanceof LivingEntity dl && real instanceof LivingEntity rl) {
            dl.yBodyRot = rl.yBodyRot;
            dl.yBodyRotO = rl.yBodyRotO;
            dl.yHeadRot = rl.yHeadRot;
            dl.yHeadRotO = rl.yHeadRotO;
            dl.calculateEntityAnimation(false);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (wrongDummy == null || event.getEntity().getId() != wrongTargetId) {
            return;
        }
        Entity dummy = wrongDummy;
        event.setCanceled(true);
        EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(dummy);
        float pt = event.getPartialTick();
        float yaw = Mth.lerp(pt, event.getEntity().yRotO, event.getEntity().getYRot());
        renderer.render(dummy, yaw, pt, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (event.getEntity() == doppel || event.getEntity() == wrongDummy) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ================= двойник =================

    private static void startDoppelganger(Minecraft mc, int duration) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || doppel != null) {
            return;
        }
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        for (int attempt = 0; attempt < 12; attempt++) {
            double ang = RNG.nextDouble() * Math.PI * 2.0;
            double dist = 18.0 + RNG.nextDouble() * 8.0;
            int x = Mth.floor(px + Math.cos(ang) * dist);
            int z = Mth.floor(pz + Math.sin(ang) * dist);
            for (int y = Mth.floor(py) + 8; y > Mth.floor(py) - 12; y--) {
                BlockPos ground = new BlockPos(x, y, z);
                boolean solid = !level.getBlockState(ground).getCollisionShape(level, ground).isEmpty();
                boolean free = level.getBlockState(ground.above()).getCollisionShape(level, ground.above()).isEmpty()
                    && level.getBlockState(ground.above(2)).getCollisionShape(level, ground.above(2)).isEmpty();
                if (solid && free) {
                    RemotePlayer p = new RemotePlayer(level, mc.player.getGameProfile());
                    p.moveTo(x + 0.5, y + 1.0, z + 0.5, 0.0F, 0.0F);
                    level.putNonPlayerEntity(DOPPEL_ID, p);
                    doppel = p;
                    doppelUntil = now + duration;
                    lookAtPlayer(mc);
                    return;
                }
            }
        }
    }

    private static void lookAtPlayer(Minecraft mc) {
        RemotePlayer d = doppel;
        if (d == null || mc.player == null) {
            return;
        }
        double dx = mc.player.getX() - d.getX();
        double dz = mc.player.getZ() - d.getZ();
        double dy = mc.player.getEyeY() - d.getEyeY();
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));
        d.setYRot(yaw);
        d.yRotO = yaw;
        d.setXRot(pitch);
        d.xRotO = pitch;
        d.yBodyRot = yaw;
        d.yBodyRotO = yaw;
        d.yHeadRot = yaw;
        d.yHeadRotO = yaw;
    }

    private static void removeDoppel(Minecraft mc) {
        if (mc.level != null && doppel != null) {
            mc.level.removeEntity(DOPPEL_ID, Entity.RemovalReason.DISCARDED);
        }
        doppel = null;
    }

    // ================= искажённый мир =================

    private static void closeChain() {
        if (distortChains != null) {
            for (PostChain c : distortChains) {
                try {
                    if (c != null) {
                        c.close();
                    }
                } catch (Throwable ignored) {
                    // ничего
                }
            }
            distortChains = null;
            chainW = -1;
            chainH = -1;
        }
    }

    /** Цвета и контраст «плывут»: частичная инверсия ванильным пост-шейдером + цветная вуаль. */
    @SubscribeEvent
    public static void onRenderGuiPre(RenderGuiEvent.Pre event) {
        if (now >= distortUntil) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float phase = (now + event.getPartialTick()) / 20.0F;
        if (!distortFailed) {
            try {
                if (distortChains == null) {
                    PostChain[] made = new PostChain[DISTORT_CHAINS.length];
                    for (int i = 0; i < made.length; i++) {
                        made[i] = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(),
                            new ResourceLocation(DoomedTraitsAdditions.MOD_ID, "shaders/post/" + DISTORT_CHAINS[i] + ".json"));
                    }
                    distortChains = made;
                    chainW = -1;
                }
                int w = mc.getWindow().getWidth();
                int h = mc.getWindow().getHeight();
                if (w != chainW || h != chainH) {
                    for (PostChain c : distortChains) {
                        c.resize(w, h);
                    }
                    chainW = w;
                    chainH = h;
                }
                // сила инверсии "плывёт": по кругу переключаем три готовые цепочки
                PostChain active = distortChains[DISTORT_ORDER[(int) ((now / 14) % DISTORT_ORDER.length)]];
                RenderSystem.disableBlend();
                RenderSystem.disableDepthTest();
                RenderSystem.resetTextureMatrix();
                active.process(event.getPartialTick());
                mc.getMainRenderTarget().bindWrite(true);
            } catch (Throwable t) {
                distortFailed = true;
                closeChain();
                DoomedTraitsAdditions.LOGGER.warn("Не удалось включить пост-шейдер искажённого мира, остаётся цветная вуаль", t);
            }
        }
        // вуаль меняющегося цвета поверх (работает и без шейдера)
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int r = (int) (127 + 127 * Mth.sin(phase * 1.3F));
        int g = (int) (127 + 127 * Mth.sin(phase * 1.7F + 2.0F));
        int b = (int) (127 + 127 * Mth.sin(phase * 2.1F + 4.0F));
        event.getGuiGraphics().fill(0, 0, w, h, (0x38 << 24) | (r << 16) | (g << 8) | b);
    }
}

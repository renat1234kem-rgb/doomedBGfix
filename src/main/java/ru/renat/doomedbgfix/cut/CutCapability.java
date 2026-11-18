package ru.renat.doomedbgfix.cut;

import net.mattlives.doomedmatu.body.BodyPart;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import ru.renat.doomedbgfix.DoomedBGFix;

import javax.annotation.Nullable;
import java.util.Optional;

public final class CutCapability {
    public static Capability<CutProgress> CAP = null;
    private static final ResourceLocation KEY = new ResourceLocation(DoomedBGFix.MODID, "cut_progress");

    private CutCapability() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CutCapability::onRegister);
        MinecraftForge.EVENT_BUS.addGenericListener(Entity.class, CutCapability::onAttach);
        MinecraftForge.EVENT_BUS.addListener(CutCapability::onClone);
    }

    private static void onRegister(RegisterCapabilitiesEvent event) {
        event.register(CutProgress.class);
        CAP = CapabilityManager.get(new CapabilityToken<CutProgress>() {});
    }

    private static void onAttach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new Provider());
        }
    }

    private static void onClone(PlayerEvent.Clone event) {
        Optional<CutProgress> old = get(event.getOriginal());
        Optional<CutProgress> now = get(event.getEntity());
        if (old.isPresent() && now.isPresent()) now.get().copyFrom(old.get());
    }

    public static Optional<CutProgress> get(Player player) {
        if (player == null || CAP == null) return Optional.empty();
        return player.getCapability(CAP).resolve();
    }

    public static LimbCut cut(Player player, BodyPart part) {
        return get(player).map(p -> p.get(part)).orElseGet(LimbCut::new);
    }

    private static final class Provider implements ICapabilitySerializable<CompoundTag> {
        private final CutProgress instance = new CutProgress();
        private final LazyOptional<CutProgress> optional = LazyOptional.of(() -> instance);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
            return cap == CAP ? optional.cast() : LazyOptional.empty();
        }

        @Override public CompoundTag serializeNBT() { return instance.save(); }
        @Override public void deserializeNBT(CompoundTag nbt) { instance.load(nbt); }
    }
}

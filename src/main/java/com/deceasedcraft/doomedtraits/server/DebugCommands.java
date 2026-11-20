package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.DoomedDataExt;
import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Отладочные команды /dta ... Работают только у игрока в творческом режиме.
 *
 *   /dta trait list | give <черта|all> | take <черта> | clear
 *   /dta schizo list | status | fire <событие> | next <секунды>
 *   /dta smoke
 */
@Mod.EventBusSubscriber(modid = DoomedTraitsAdditions.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DebugCommands {
    private static final SuggestionProvider<CommandSourceStack> TRAITS = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (AddTrait t : AddTrait.values()) {
            ids.add(t.id());
        }
        ids.add("all");
        return SharedSuggestionProvider.suggest(ids, b);
    };
    private static final SuggestionProvider<CommandSourceStack> EVENTS =
        (ctx, b) -> SharedSuggestionProvider.suggest(SchizoManager.eventIds(), b);

    private DebugCommands() {
    }

    private static boolean creative(CommandSourceStack src) {
        return src.getEntity() instanceof ServerPlayer p && p.isCreative();
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("dta").requires(DebugCommands::creative)
            .then(Commands.literal("trait")
                .then(Commands.literal("list").executes(DebugCommands::traitList))
                .then(Commands.literal("clear").executes(DebugCommands::traitClear))
                .then(Commands.literal("give").then(Commands.argument("trait", StringArgumentType.word())
                    .suggests(TRAITS).executes(ctx -> traitChange(ctx, true))))
                .then(Commands.literal("take").then(Commands.argument("trait", StringArgumentType.word())
                    .suggests(TRAITS).executes(ctx -> traitChange(ctx, false)))))
            .then(Commands.literal("schizo")
                .then(Commands.literal("list").executes(DebugCommands::schizoList))
                .then(Commands.literal("status").executes(DebugCommands::schizoStatus))
                .then(Commands.literal("fire").then(Commands.argument("event", StringArgumentType.word())
                    .suggests(EVENTS).executes(DebugCommands::schizoFire)))
                .then(Commands.literal("next").then(Commands.argument("seconds", IntegerArgumentType.integer(0, 7200))
                    .executes(DebugCommands::schizoNext))))
            .then(Commands.literal("smoke").executes(DebugCommands::smoke)));
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static DoomedData data(ServerPlayer sp) {
        return DoomedDataUtil.get(sp).orElse(null);
    }

    // ---------- черты ----------

    private static int traitList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        DoomedData d = data(sp);
        if (d == null) {
            ctx.getSource().sendFailure(Component.literal("Нет данных Doomed."));
            return 0;
        }
        StringBuilder sb = new StringBuilder("Выбранные: ");
        int n = 0;
        for (AddTrait t : AddTrait.values()) {
            if (AddTraits.has(AddTraits.mask2(d), t)) {
                sb.append(t.id()).append(AddTraits.active(d, t) ? "" : " (не действует)").append(", ");
                n++;
            }
        }
        say(ctx, n == 0 ? "Выбранные: нет. Все: " + String.join(", ", allIds()) : sb.toString());
        return n;
    }

    private static List<String> allIds() {
        List<String> ids = new ArrayList<>();
        for (AddTrait t : AddTrait.values()) {
            ids.add(t.id());
        }
        return ids;
    }

    private static int traitChange(CommandContext<CommandSourceStack> ctx, boolean give) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        DoomedData d = data(sp);
        if (d == null) {
            ctx.getSource().sendFailure(Component.literal("Нет данных Doomed."));
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "trait");
        int bits = 0;
        if (name.equals("all")) {
            bits = AddTraits.ALL_MASK;
        } else {
            for (AddTrait t : AddTrait.values()) {
                if (t.id().equals(name)) {
                    bits = t.bit();
                }
            }
        }
        if (bits == 0) {
            ctx.getSource().sendFailure(Component.literal("Нет такой черты: " + name));
            return 0;
        }
        int mask2 = AddTraits.mask2(d);
        mask2 = give ? (mask2 | bits) : (mask2 & ~bits);
        applyMask2(sp, d, mask2);
        say(ctx, (give ? "Добавлено: " : "Убрано: ") + name + ". Баланс очков не проверяется (отладка).");
        return 1;
    }

    private static int traitClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        DoomedData d = data(sp);
        if (d == null) {
            return 0;
        }
        applyMask2(sp, d, 0);
        say(ctx, "Наши черты сброшены.");
        return 1;
    }

    /** Ставим наши черты напрямую; цену в маску Doomed не кодируем, чтобы баланс не мешал проверке. */
    private static void applyMask2(ServerPlayer sp, DoomedData d, int mask2) {
        int base = d.getTraits() & ~AddTraits.OUR_BITS;
        int flag = AddTraits.has(mask2, AddTrait.FOCUSED) ? AddTraits.FOCUSED_FLAG : 0;
        d.setTraits(mask2 == 0 ? base : (base | AddTraits.COST_MARKER | flag));
        ((DoomedDataExt) (Object) d).doomedtraitsadd$setMask2(mask2);
        d.setTraitsChosen(true);
        DoomedDataUtil.sync(sp);
    }

    // ---------- шизофрения ----------

    private static int schizoList(CommandContext<CommandSourceStack> ctx) {
        StringBuilder sb = new StringBuilder("События (id: шанс %):\n");
        for (String id : SchizoManager.eventIds()) {
            sb.append(id).append(": ").append(SchizoManager.weightOf(id)).append("\n");
        }
        say(ctx, sb.toString());
        return 1;
    }

    private static int schizoStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        DoomedData d = data(sp);
        if (d == null) {
            return 0;
        }
        boolean on = AddTraits.active(d, AddTrait.HARDCORE_SCHIZOPHRENIA);
        long left = SchizoManager.secondsUntilNext(sp);
        say(ctx, "Хардкорная шизофрения: " + (on ? "действует" : "не действует")
            + "; до события: " + (left < 0 ? "таймер не запущен" : left + " с")
            + "; интервал: " + SchizoManager.intervalInfo(sp, d));
        return 1;
    }

    private static int schizoFire(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        DoomedData d = data(sp);
        if (d == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "event");
        boolean ok = SchizoManager.fireById(sp, d, id);
        if (ok) {
            say(ctx, "Событие запущено: " + id);
        } else {
            ctx.getSource().sendFailure(Component.literal("Событие не сработало (нет такого id или не выполнены условия, например нет оружия/друга): " + id));
        }
        return ok ? 1 : 0;
    }

    private static int schizoNext(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        SchizoManager.setNextInSeconds(sp, seconds);
        say(ctx, "Следующее событие через " + seconds + " с.");
        return 1;
    }

    // ---------- курение ----------

    private static int smoke(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        DoomedData d = data(sp);
        if (d == null) {
            return 0;
        }
        boolean ok = AdditionalTraitEvents.smokeNow(sp, d);
        say(ctx, ok ? "Сигарета выкурена." : "Не вышло: нужны сигарета и огонь в инвентаре.");
        return ok ? 1 : 0;
    }
}

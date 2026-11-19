# Doomed BG Fix 0.2.0

Полный проект. Можно просто заменить всё содержимое репозитория (кроме libs/ - там твои jar).
Если не хочешь качать целиком - возьми doomedBGfix-0.2.0-PATCH.zip, там только изменённые файлы.

## Новое в 0.2.0 (фикс пакетов)

* `bag/BagVolume.java` - остаток пакета в NBT (`BGFixBagMl`, 100 = полный), стек-safe списание.
* `bag/BagInfusion.java` - замена ванильной ветки переливания: списывается только влитый объём,
  остальное остаётся в пакете. Когда пакет пуст - превращается в пустой пакет для переливания.
* `bag/BarrelFill.java` - порт KubeJS-скрипта: ПКМ пустым пакетом по бочке с физраствором/Рингером.
  Умеет **доливать** уже начатый пакет пропорционально (500 mB = полный пакет).
* `bag/BagTooltip.java` - тултип "320 / 750 ml".
* `mixin/DrugServiceMixin.java` - перехват `DrugService.inject` для пакетов.
* `mixin/CoopMedicalMixin.java` - `injectionVolume` для пакетов возвращает остаток, а не всегда 100.

## Уже было в 0.1.x, но вошло в этот архив (чтобы не собирать по кускам)

* Фикс `boneThrough`: кость, пропиленную насквозь, больше не «отращивает» глубину назад.
  Именно из-за этого пила стопорилась на 66%. (`cut/LimbCut.java`, `server/CutBone.java`)
* Кровь откалибрована по ванильным ориентирам: пик 14 единиц вместо 65. (`cut/CutMath.java`)
* Боль - точная ванильная формула, пересчитанная на приложенную работу. (`cut/CutMath.java`)
* `server/PhantomNerf.java` - потолки фантомной боли (12 вместо 36..70), приступ <= 8 с.
* Фикс исчезновения жгута при потере конечности. (`server/TourniquetFix.java`)

## Что заменено относительно 0.1.0

    gradle.properties                                   (версия 0.2.0)
    src/main/resources/doomedbgfix.mixins.json          (3 миксина вместо 1)
    src/main/java/ru/renat/doomedbgfix/DoomedBGFix.java (регистрация BarrelFill + BagTooltip)
    .../config/BGConfig.java                            (секции phantom + fixes расширены)
    .../cut/CutMath.java                                (кровь + боль)
    .../cut/LimbCut.java                                (boneThrough)
    .../server/CutBone.java                             (boneThrough)
    .../server/CutTicker.java                           (вызов PhantomNerf)

## Новые файлы

    .../server/PhantomNerf.java
    .../bag/BagVolume.java
    .../bag/BagInfusion.java
    .../bag/BarrelFill.java
    .../bag/BagTooltip.java
    .../mixin/DrugServiceMixin.java
    .../mixin/CoopMedicalMixin.java
    CHANGELOG.md

## Сборка

    ./gradlew build      -> build/libs/doomedbgfix-0.2.0.jar

Старый doomedbgfix-0.1.0.jar из mods/ убери. Конфиг Forge допишет новые ключи сам;
если хочешь дефолты начисто - удали config/doomedbgfix-common.toml.

## Что ещё НЕ сделано

Мгновенное действие шприцов. Причина найдена: `SyringeScreen.doMouseReleased` -> `inject()`
отправляет дозу один раз при отпускании. Нужен стриминг дельт + кумулятивные пороги
(например `EPINEPHRINE: if (ml >= 40) tryStartFibrillation`), иначе передоз перестанет работать.
Это следующий патч.

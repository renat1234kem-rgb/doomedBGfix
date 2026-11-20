# Doomed Traits Additions

Замени папку `src/` в своём проекте содержимым этого архива (build.gradle не трогай).
Старые персонажи с чертами из прошлой версии лучше пересоздать: формат хранения изменился.

Где крутить числа:
- `AddTrait.java` - цены черт
- `AddTraits.java` - сдвиги уровней, множители опыта
- `server/AdditionalTraitEvents.java` - вегетарианство, социофобия, курильщик, оптимист
- `server/SchizoManager.java` - интервалы, таблица событий шизофрении, тексты друга
- `server/FoodClassifier.java` - что считается мясом/молочкой
- `assets/doomed_traits_additions/lang/` - тексты черт
- `assets/doomed_traits_additions/textures/gui/hallucination.png` - картинка «25-го кадра»

## Отладка (только в творческом режиме)
- `/dta trait list | give <черта|all> | take <черта> | clear`
- `/dta schizo list | status | fire <событие> | next <секунды>`
- `/dta smoke` - выкурить сигарету сейчас (нужны сигарета и огонь)

## Конфиги
- `config/doomed_traits_additions-client.toml`: `foxy_jumpscare = true` заменяет ВСЕ «25-е кадры» на скример Фокси со звуком.
  Звук `assets/doomed_traits_additions/sounds/foxy_scream.ogg` - синтезированная заглушка; положи на её место настоящий звук скримера (ogg, моно).
- `config/doomed_traits_additions-common.toml`: `friend_voice = false` отключает озвучку реплик друга Doomed.

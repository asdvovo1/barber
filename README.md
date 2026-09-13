# Barber Shop Rush — Android (Kotlin native)

إعادة بناء اللعبة من HTML/Canvas إلى تطبيق أندرويد أصلي بالكامل بـ Kotlin.
مفيش WebView ومفيش تغليف — كل حاجة بترسم على `Canvas` جوه `View` واحد.

## البناء في GitHub Codespaces

```bash
bash scripts/setup-android-sdk.sh      # أول مرة بس (بينزّل الـ SDK)
./gradlew assembleDebug                # أو: gradle assembleDebug
```

الـ APK هيطلع في:

```
app/build/outputs/apk/debug/app-debug.apk
```

نزّله من فايلات الـ Codespace (كليك يمين → Download) وثبّته على الموبايل.
لو عايز بناء أوتوماتيك كل push، فيه workflow جاهز في `.github/workflows/build.yml`
والـ APK بيتحط كـ artifact.

## البنية

| ملف | الدور |
|---|---|
| `Data.kt` | تحميل الجداول والصور من `assets/` + الترجمة (عربي/إنجليزي) |
| `Save.kt` | الحفظ في SharedPreferences بدل localStorage (نفس مفتاح `bsr_v3`) |
| `Levels.kt` | مولّد المراحل، RNG نفسه بالظبط، أقنعة قصّات الشعر، النجوم |
| `Masks.kt` | طبقات الشعر والفرق بين صورة قبل/بعد + المسح بالفرشة |
| `Game.kt` | محرك اللعب: الصبر، الكومبو، الجزاءات، الجزيئات، الربح/الخسارة |
| `GameSurface.kt` | كل الشاشات: القائمة، الخريطة، المتجر، اللعب، النتيجة |
| `Sfx.kt` | SoundPool للأصوات + MediaPlayer للموسيقى |

## الأصول

`app/src/main/assets/`:
- `assets/` — 904 صورة وصوت اتفكّوا من الـ data URIs الأصلية (~12 MB)
- `data/` — الجداول المستخرجة: `assets.json`، `zones.json`، `tables.json`، `txt_ar.json`، `txt_en.json`

## إعدادات

- `minSdk 24` · `targetSdk 34` · Kotlin 1.9.24 · AGP 8.5.2
- بدون أي مكتبة خارجية (لا Compose ولا AndroidX) عشان البناء يعدّي من أول مرة
- بورتريه ثابت، شاشة كاملة، `largeHeap`

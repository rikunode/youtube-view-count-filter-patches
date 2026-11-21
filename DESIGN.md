# Rikunode View Count Filter — architecture and verification

Updated 2026-11-21. Target: **YouTube 21.30.216** (versionCode 1561187872), **official Morphe Patches v2.3.0** and an independently built `.mpp` patch in **one** patcher invocation. This target is the official STABLE version; 21.46.522 was excluded from official compatibility.

## 1. Independent APK-side filtering hook

Implementation: `patches/src/main/kotlin/app/rikunode/morphe/patches/youtube/ViewCountM1ProbePatch.kt`.

Only public `app.morphe.patcher.*` APIs are imported. The patch neither imports `app.morphe.patches.*` nor copies Morphe extension code. We use original APK-side class/method characteristics, which can be independently fingerprinted:

- Component converter fingerprint: original strings `Element missing correct type extension` and `Element missing type`. In **21.30.216** it resolves to `Lvvz;->a(Liev;Lxcv;Lwls;Ljava/util/List;Lxds;Z)Lieq;`.
- Official patch v2.3.0 modifies this APK method to run `LithoFilterPatch.isFiltered(context, protobuf, accessibility ID, accessibility text)` after conversion. Source evidence: [Morphe shared Litho hook](https://github.com/MorpheApp/morphe-patches/blob/v2.3.0/patches/src/main/kotlin/app/morphe/patches/shared/misc/litho/filter/LithoFilterPatch.kt).
- The independent hook targets the **final original return-object** after conversion/cleanup and after the official hook. It calls `ViewCountFilter.shouldDrop(Object)`; when true, it replaces the pending return value with the APK-side empty component via an independently fingerprinted `EmptyComponent` builder, otherwise returns the original object. The app-side method and builder references are obtained at patch time from fingerprints, with free-register allocation through the public patcher library.
- Decompiled final `vvz.a` shows both: `LithoFilterPatch.isFiltered(...) || ViewCountFilter.shouldDrop(wlsVar)`. This is **structural coexistence evidence**, not proof that all actual feeds contain parseable counts or that list layouts never show a blank slot.

## 2. Settings independent of the official registry

A second APK-side fingerprint finds a synthetic `run()` method containing `:android:show_fragment_args` and the native `PreferenceScreen` accessor. On this exact APK it resolves to `Lptw;->run()V`. Before the settings builder returns, the independent extension `ViewCountSettingsEntry.install(Object)` inserts a uniquely keyed native `Preference` via reflection and opens `ViewCountSettingsActivity`. It does **not** use Morphe's private settings API or call its extension hooks.

The patch registers its own settings Activity and `ViewCountInitProvider` in the patched app manifest via the public `resourceXml` API. The provider initializes the application Context before feed rendering. Runtime settings persist in private `SharedPreferences` (`rikunode_viewcount` file), with unique keys:

| Key | Default |
|---|---:|
| `rikunode_viewcount_home` | false |
| `rikunode_viewcount_search` | false |
| `rikunode_viewcount_subscriptions` | false |
| `rikunode_viewcount_less_than` | 1000 |
| `rikunode_viewcount_greater_than` | 1000000000000 |
| `rikunode_viewcount_m1_test` | false |

M1 test mode attempts to hide components containing `video` in the APK-side metadata identifier. It is explicitly opt-in and can hide broadly. Owner device testing of the actual settings entry, persistence after restart, and visible feed removal remains mandatory.

## 3. Localized parsing and fail-open behavior

`ViewCountParser.java` is an independent, self-owned Java implementation informed by the multiplier/threshold approach of [Anddea's FeedVideoViewsFilter](https://github.com/anddea/revanced-patches/blob/main/extensions/shared/src/main/java/app/morphe/extension/youtube/patches/components/FeedVideoViewsFilter.java). It uses neither Anddea's private extension classes nor Morphe's private registry.

The parser normalizes Unicode fullwidth numbers, spaces and NBSP, and recognizes Japanese `万`, `億`, `回視聴`, `回再生` and English K/M/B and `views`. It scans only patterns with explicit view-count suffixes, rejecting ambiguous matches, overflow and missing counts. Thresholds are strict lower-than/greater-than. Buffer access and obfuscated component method reflection are guarded: any failure returns false (leave visible).

**Known limitation:** The current screen classifier recognizes only metadata identifiers containing `home`/`feed`, `search`, or `subscription`. The raw protobuf may contain multiple view-count-like strings; differing numbers fail open. Some actual feeds may not carry a recognizable screen identifier or count in the exposed bytes, in which case the patch deliberately does nothing. Actual feed data must be validated on a device before calling the filter reliable.

Parser fixtures passed: `1.2万回視聴`=12000, `3億回視聴`=300000000, `1,234 回視聴`=1234, `1.2K views`=1200, `2.5M views`=2500000, `1B views`=1000000000, `１２．５万回再生`=125000, no-count fail-open, conflicting-count fail-open.

## 4. Build and packaging

Gradle plugin [morphe-patches-gradle-plugin v2.1.1](https://github.com/MorpheApp/morphe-patches-gradle-plugin) at `444bb41a`, patcher [v2.4.0](https://github.com/MorpheApp/morphe-patcher) at `0aed87c4`, pinned as Git submodules and wired with `includeBuild`. JDK 23 + Android SDK 36. The official v2.3.0 patch bundle is **not** copied into the independent `.mpp`.

For Windows integration, the MUSE-supplied base APK (SHA-256 `6422639d1cd679f99a6e26288940b243c0cde30253a085058bd09ab4aeedfe0c`) and 30 resource splits (ZIP SHA-256 `43016c5234748e89e9cd44bcbd5d7e42af7fb42b5dec47c8eebc212b17cc55d5`) were merged using **AntiSplit-G2** in an Android emulator. The result was 75,398,767 bytes; `aapt` found `yt_fill_bell_black_24` with zero ARSC consistency warnings. The bare base APK is unsuitable for the official Hide layout components patch because its density resources are absent.

## 5. M1 evidence and owner acceptance

Windows static gate:
- Official `Hide layout components` (including `KeywordContentFilter`) plus `View count filter (Rikunode)` both applied on YouTube 21.30.216 with **no patch failures**, and the APK rebuilt.
- Decompiled `vvz.a` contains the independent optional empty-component branch and official `LithoFilterPatch.isFiltered`, with no class collision.
- The patched manifest registers the self-owned settings Activity and init Provider.
- Nine parser fixtures passed.
- Morphe Desktop's local Bouncy Castle signer failed at keystore MAC verification; static verification used `--unsigned`. This is a signing-environment failure rather than a patch failure; it does **not** establish device installability.

The owner must independently verify actual hiding, settings entry visibility and persistence, separate HOME/SEARCH/SUBSCRIPTIONS switches, Japanese/English and boundary cases, fail-open behavior, and official filter stability on a device. **Do not claim runtime verification from APK inspection alone.**

## 6. License and release

GPL-3.0-or-later, with `NOTICE` retaining Morphe GPLv3 Section 7 notices and Anddea attribution. Official patch internals are not redistributed. Source README in English/Japanese and GitHub Actions build a uniquely named `.mpp` with `SHA256SUMS` for an experimental `v0.1.0` Release. No YouTube APK, keys, tokens or personal data may be committed or attached.

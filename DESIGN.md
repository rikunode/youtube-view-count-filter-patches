# YouTube view-count filter: self-owned hook design (2026-11-21)

**Target:** clean YouTube 21.30.216 APK, official Morphe Patches v2.3.0 plus one independent third-party `.mpp`, patched in the same CLI run. **Architecture decision:** independent APK hooks only, via public `app.morphe.patcher.*` API and self-owned extension; no `app.morphe.patches.*` imports or copied Morphe extension classes. This supersedes the original cross-bundle Litho/settings registration proposal.

## 1. Where Morphe hooks, and independent hook boundary

Source examined: [official shared LithoFilterPatch.kt](https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/shared/misc/litho/filter/LithoFilterPatch.kt#L171-L280) and [Fingerprints.kt](https://github.com/MorpheApp/morphe-patches/blob/main/patches/src/main/kotlin/app/morphe/patches/shared/misc/litho/filter/Fingerprints.kt#L26-L79). The official patch finds an APK-side component-creation method via a characteristic `IF_EQZ` / `CHECK_CAST` / `RETURN_OBJECT` sequence and diagnostic literals `Element missing correct type extension` and `Element missing type`. At the matched return, it calls its own extension with `p2` (context), encoded protobuf bytes from `p3`, accessibility ID/text; if filtered, it calls an APK-side empty-component builder and returns that component. Separately, it modifies the **Morphe extension constructor** to populate a filter array. **Do not touch that constructor or the official helper.**

**Independent candidate:** find the same APK-side component-construction method by original literals/structural fingerprint, but inspect the **original successful return** rather than reuse the official inserted `isFiltered` call or its helper. At the final successful `return-object` boundary, insert a call to our own extension `app.rikunode.morphe.extension.youtube.filters.ViewCountFilter` with the relevant per-item metadata/context (captured only from verified method registers). If decision is true, replace the successful result with the APK's existing empty-component result; otherwise preserve it. This requires verifying the exact register lifetime, original return branches, empty-component builder, and whether official patch insertion changes the match location on **21.30.216**, before writing the hook. This is an independent APK method interception, not an upstream filter registration. Both hooks must leave the original parser's mandatory cleanup/control flow intact; if no distinct safe success boundary exists, fail M1 rather than override the official hook. Reuse only factual fingerprint characteristics, never source or classes from the official patch.

**Metadata contract:** do not parse arbitrary protobuf bytes as if UTF-8 text. Identify and decode the actual view-count display text from feed component metadata (or a verified accessibility text field) while preserving screen context; reject ambiguity. The M1 probe must demonstrate one fixed test rule that **actually removes one observed feed item**, not only a patched class or logged call. Returning a blank component is insufficient if it leaves a visible empty card; verify feed-list removal. If the upstream list-removal mechanism cannot be shared, independently identify a safe list-element exclusion point without copying its implementation. If neither can be proven, M1 FAIL.

## 2. Independent runtime settings

No Morphe settings API imports. Own Java Android Activity under `app.rikunode.morphe.extension.youtube.settings`, own manifest registration and resources (via public patcher resource integration). Add an entry to the YouTube **APK-side** settings UI by fingerprinting the app's settings preference screen/menu creation path, inserting a uniquely keyed preference `rikunode_viewcount_settings` which opens the own activity. Do not alter Morphe's settings classes. Prefer a distinct preference insertion after the original app-built list is assembled, not at Morphe's `PreferenceScreen` registry. Verify 21.30.216 class/method and resource ID before implementation. An unverified menu entry must not be reported as working.

Persist using Android `SharedPreferences` with namespace `rikunode_viewcount_*`. Controls: home/search/subscriptions ON/OFF (all false), `less_than=1000L`, `greater_than=1000000000000L`. Each control editable at runtime, survives app restart. Default filtering OFF, unknown feed context or unparseable count leaves item visible. The probe must demonstrate settings entry displayed **and** saved value read back, not merely static resource addition.

## 3. Locale and filter semantics

Recognize localized count strings only at the verified per-video view-count field. Normalize Unicode fullwidth digits and punctuation, decimal point, ordinary/NBSP spacing, thousands separators; suffixes `回視聴` / `回再生`, `view` / `views`; multipliers 万=10000, 億=100000000, K=1000, M=1000000, B=1000000000. Fixtures: `1.2万回視聴` = 12000; `3億回視聴` = 300000000; `1,234 回視聴` = 1234; `1.2K views` = 1200; `2.5M views` = 2500000; `1B views` = 1000000000. Use strict less/greater boundaries, safe long arithmetic, fail-open. These examples are design test cases, **not proof** of actual 21.30.216 serialized metadata.

## 4. Independent package and build

Continue official [patches template](https://github.com/MorpheApp/morphe-patches-template) layout, Gradle plugin submodule `v2.1.1` (commit `444bb41a`), patcher submodule `v2.4.0` (commit `0aed87c4`), local `includeBuild` in `settings.gradle.kts`. No credentials committed. Proposed:
- `patches/src/main/kotlin/app/rikunode/morphe/patches/youtube/ViewCountPatch.kt`: app-only fingerprints, injection and class/resource integration
- `extensions/extension/src/main/java/app/rikunode/morphe/extension/youtube/filters/ViewCountFilter.java`
- `extensions/extension/src/main/java/app/rikunode/morphe/extension/youtube/settings/ViewCountSettingsActivity.java`
- `extensions/extension/src/main/AndroidManifest.xml`, `extensions/extension/src/main/res/values/strings.xml`

## 5. M1 **hard gate** / proof obligations

1. Build a tiny `.mpp` using public patcher API only. Any unresolved app registers, fingerprint ambiguity or inability to add own extension => FAIL.
2. Use MUSE-supplied, SHA-256-verified APKMirror 21.30.216 base.apk (bundle SHA-256 verified by MUSE); retain original SHA256, check signature/package/version and scan for malware **before use**. Obtain official `patches-2.3.0.mpp` and official patcher CLI from MorpheApp releases. Use exactly one CLI patch run with both bundles, selecting official Hide layout components (includes the KeywordContentFilter) and custom probe.
3. Verify CLI report marks both official patches and custom patch as applied, extension present without duplicate class descriptors, safe independent hook in patched DEX (specific opcode and method evidence), **structural evidence of a filter branch** by the fixed M1 rule, and the custom settings entry and persistence code in patched DEX/resources. Actual visible feed removal and live settings persistence are owner-device acceptance checks, not assertions from static APK inspection.
4. If any requirement fails, **stop** and report exact evidence. Do not silently weaken the test or invent a new hook.

**M2 only after fully successful M1:** port Anddea view-count comparison (preserve source copyright headers), add localized parser and six focused fixtures, GPLv3 LICENSE and NOTICE retaining Morphe GPLv3 Section 7 notices, bilingual README with explicit unofficial third-party status, tagged `v0.1.0` GitHub Actions Release with `.mpp` and `SHA256SUMS`. Do not check in APKs, credentials, machine paths or personal data.

## 6. Owner device verification (after M2)

Check settings shown/persisted; independent HOME/SEARCH/SUBSCRIPTIONS toggles; Japanese `1.2万回視聴` both sides of threshold; GREATER_THAN behavior; English K/M/B; fail-open on unknown counts; original Morphe Hide feed components and Keyword filter still working.

## MUSE hold points

- The APK-side **safe success-return hook**, extracted view-count metadata, and app settings insertion site must be demonstrated from actual 21.30.216 code, not inferred only from upstream Kotlin.
- Windows-side M1 verifies patch report and post-patch structural coexistence; the owner separately verifies actual feed removal and settings UI readback on a device.
- A conflict with official injected code means FAIL (no replacement method or internal-library copy).

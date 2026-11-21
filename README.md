# Rikunode View Count Filter for YouTube

An **independent, unofficial** third-party patch for YouTube feeds, applied alongside official Morphe Patches. This project is **not affiliated with Morphe, Google, YouTube, Anddea, or ReVanced**.

[日本語 README](README.ja.md) · [Design and verification](DESIGN.md) · [License](LICENSE) · [Notices](NOTICE)

## Compatibility

- YouTube **21.30.216** (versionCode `1561187872`), as supplied by APKMirror
- Official Morphe Patches **v2.3.0**, including **Hide layout components** (which includes its KeywordContentFilter)
- Independent patch source: **View count filter (Rikunode)**, applied **in the same Morphe Desktop / Manager session** as the official patches
- Unsupported on other YouTube versions, including 21.46.522

The patch uses the public `app.morphe.patcher.*` API. It does **not** import the official patch implementation's private `app.morphe.patches.*` API or copy its extension classes.

## Behavior

An independent APK-side hook adds a final optional rejection of a feed component after YouTube's own converter and official Litho filter. Its runtime settings are separate:

| Key | Default |
|---|---|
| `rikunode_viewcount_home` | OFF |
| `rikunode_viewcount_search` | OFF |
| `rikunode_viewcount_subscriptions` | OFF |
| `rikunode_viewcount_less_than` | 1000 |
| `rikunode_viewcount_greater_than` | 1,000,000,000,000 |

The new **View count filter / 再生回数フィルタ** entry is injected into YouTube's native settings screen and opens this project's own settings Activity. The keys persist in the application's private SharedPreferences. The filter recognizes Japanese `万`, `億`, `回視聴` and `回再生`, and English `K/M/B` and `view(s)`, including normalized fullwidth digits. Unknown/ambiguous counts, unknown screen contexts, and exceptions fail open (no hiding). Threshold comparisons are strict (`<` and `>`).

**Important limitation:** Screen classification currently depends on component metadata identifiers containing `home`/`feed`, `search`, or `subscription`. Not every feed component provides these identifiers. The Windows integration proves structural injection, **not** successful hiding on every screen; on-device validation remains required. The optional **M1 test** toggle attempts to hide components with `video` in their metadata identifier; it is OFF by default and can broadly hide cards.

## Build

Prerequisites: JDK 23, Android SDK API 36, Android build-tools 36, Gradle wrapper, and the two checked-in Git submodule pointers.

```sh
git clone --recurse-submodules https://github.com/rikunode/youtube-view-count-filter-patches.git
cd youtube-view-count-filter-patches
./gradlew build
```

The artifact is `patches/build/libs/patches-0.1.3.mpp`. To verify the GitHub Release artifact, use `sha256sum -c SHA256SUMS`.

## Add to Morphe Manager

In Morphe Manager, open **Patch sources → Add source** and enter the **GitHub repository URL**, not a direct `.mpp` download URL:

`https://github.com/rikunode/youtube-view-count-filter-patches`

Alternatively, open the [one-tap add-source link](https://morphe.software/add-source?github=rikunode/youtube-view-count-filter-patches) on your Android device. The Manager reads `patches-list.json` and `patches-bundle.json` from the repository root, and downloads the **v0.1.3** bundle. Keep the official Morphe source enabled and select both patches in the same patching session.
## Patching

Use Morphe Desktop 2.2.0 or compatible Manager and **both bundles in the same patching operation**. Example on a clean compatible single APK:

```sh
java -jar morphe-desktop-2.2.0-all.jar patch \
  --patches=patches-2.3.0.mpp --enable="Hide layout components" \
  --patches=patches-0.1.3.mpp --enable="View count filter (Rikunode)" \
  --exclusive --out=youtube-patched.apk youtube-21.30.216.apk
```

The original APKMirror distribution is split. A bare `base.apk` does **not** include required density resources; it is not a valid input for the official filter patch. Windows integration used **AntiSplit-G2** to merge `base.apk` with 30 resource splits. **No YouTube APK, keys, or other proprietary app files are distributed by this repository.** Use the original bundle in a Manager that supports it for device installation.

## Verification status

- ✅ JDK/Gradle build of independent patch bundle
- ✅ Official v2.3.0 Hide layout components + custom patch applied together to a merged 21.30.216 APK without patch failures
- ✅ Post-patch decompilation shows both official `LithoFilterPatch.isFiltered` and independent `ViewCountFilter.shouldDrop` inside APK class `vvz`, method `a`
- ✅ Self-owned Activity and init Provider registered in patched AndroidManifest
- ✅ 9 focused parser fixtures (Japanese/English, fullwidth, fail-open) passed
- ⚠️ Morphe Desktop's local Bouncy Castle keystore signer failed; **structural test APK was emitted unsigned**. Use a working signer/Manager for installation
- ⏳ Owner must validate feed removal and settings display/persistence on a device. Until that happens, this release remains **experimental**.

### Owner device checks

1. Verify the settings entry appears, opens, and saves values after restarting YouTube.
2. Check independent HOME/SEARCH/SUBSCRIPTIONS toggles.
3. Test both threshold sides using Japanese `1.2万回視聴` and `3億回視聴`.
4. Test English `1.2K views`, `2.5M views`, `1B views` and fullwidth normalization.
5. Confirm unknown counts remain visible (fail-open).
6. Confirm official Hide layout components and its KeywordContentFilter are unaffected.

## Licensing

GPL-3.0-or-later. See [LICENSE](LICENSE) and [NOTICE](NOTICE). All names and branding belong to their respective owners.

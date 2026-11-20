# YouTube view-count filter — DESIGN (2026-11-21)
Scope: design only. Target YouTube 21.46.522 + official Morphe Patches 2.x; no patch implementation until MUSE GO.

## 1. Integration
Produce one standalone .mpp applied in the same patcher session as the official bundle to one clean APK. Official Morphe must own the shared Litho hook; custom patch should register one uniquely named filter through that existing hook, without duplicate hook instrumentation or duplicate extension class descriptors. Proposed Java namespace: app.rikunode.morphe.extension.youtube.filters. **Unverified blocker:** patcher 2.x may not resolve dependencies/extension classes across independent bundles. Confirm cross-bundle registration ABI in official code before implementation; if unsupported, ask MUSE to authorize an upstream-integrated extension approach rather than shipping a conflicting hook. Hide feed components and Keyword filter must continue to work. Sources: [Anddea filter](https://github.com/anddea/revanced-patches/blob/main/extensions/shared/src/main/java/app/morphe/extension/youtube/patches/components/FeedVideoViewsFilter.java); official Litho registration, FeedViewCountPatch and VideoDetailsRequest (confirmed by MUSE). kveld/hxreborn interoperability is NOT yet source-verified.

## 2. Runtime settings
HOME/SEARCH/SUBSCRIPTIONS toggles and LESS_THAN/GREATER_THAN long values must be changeable without repatching. Anddea's Settings.java uses BooleanSetting/LongSetting and preferences. Build-time patch options do not satisfy runtime editing. Preferred design: add a dedicated settings group using official extension settings registration (if supported), unique rikunode_viewcount_* preference keys, and a Java filter reading them at runtime. Proposed paths:
- patches/src/main/kotlin/.../ViewCountPatch.kt
- extensions/youtube/src/main/java/app/rikunode/.../ViewCountFilter.java
- extensions/youtube/src/main/java/app/rikunode/.../ViewCountSettings.java
- extensions/youtube/src/main/res/values/strings.xml
If official settings registration cannot be reused from a separate bundle, MUSE must choose between coordinated extension integration and a separate settings activity; do not substitute static options silently.

## 3. Japanese and English parsing
Extract the view-count display field from the correct feed metadata context, not arbitrary numbers elsewhere in protobuf. Normalize full-width digits, whitespace/NBSP, commas and decimal dots. Recognize Japanese suffixes 回視聴 / 回再生 and English view/views; multiply 万=10000, 億=100000000, K=1000, M=1000000, B=1000000000. Fixtures: 1.2万回視聴=12000; 3億回視聴=300000000; 1,234 回視聴=1234; 1.2K views=1200; 2.5M views=2500000; 1B views=1000000000. Reject ambiguous/unrecognized fields and leave video visible (fail open). These are **proposed strings**, not captured Japanese 21.46.522 protobuf evidence; verify real feed samples before implementation approval.

## 4. Defaults
Anddea [Settings.java](https://github.com/anddea/revanced-patches/blob/main/extensions/shared/src/main/java/app/morphe/extension/youtube/settings/Settings.java) defines all three screen toggles FALSE, LESS_THAN=1000L, GREATER_THAN=1_000_000_000_000L; slider 0..1e12, step 1000. Preserve these opt-in defaults. Strict comparison: hide below 1000 or above 1e12 only on enabled screens; missing count remains visible.

## 5. Version and fingerprints
Pin first release to 21.46.522, not Anddea's 20.05.46–21.13.164. Reuse official Litho registration already compatible with 21.46.522; never copy old Anddea fingerprints unchanged. On official updates verify patcher ABI, Litho registration, class/resource injection and settings registration. Missing fingerprint or registration must fail the patch application, not produce an apparently successful but inert patch.

## 6. Repo, licenses, release
Plan: README.md (English), README.ja.md, DESIGN.md, LICENSE, NOTICE, Gradle Kotlin DSL build/settings/properties and wrapper, patches/, extensions/youtube/, .github/workflows/release.yml. Use verified official Morphe patcher 2.x artifact coordinates, not guessed versions. Owner's [hatena patch repo](https://github.com/rikunode/hatena-bookmark-hide-pr-patches) provides Gradle, patches/, extensions/, workflows, SHA256SUMS and release precedent (gradle.properties version observed 0.4.2). GitHub Actions on version tag: build .mpp, compute SHA256SUMS, verify checksum, attach both to GitHub Release. Never commit APKs, secrets, keystores, personal data or local paths. Include GPLv3 LICENSE, NOTICE naming Anddea/Morphe and preserve original copyright headers; confirm upstream exact license variants and notices before distributing derived source.

## 7. Focused verification after GO
Windows: Gradle build .mpp; inspect unique classes/no shared Litho duplicates; apply custom .mpp plus official latest patches to clean 21.46.522 APK; check patch report; test Japanese 万/億, English K/M/B, threshold boundaries and unrecognized-count fail-open.
Owner device: verify settings visibility/persistence; independent home/search/subscriptions toggles; 1.2万 count on both sides of threshold; greater-than behavior; English locale; missing counts visible; existing Hide feed components and Keyword filter unaffected.

## MUSE decision gates
1. Cross-bundle Litho filter registration and extension class visibility: actual 2.x code evidence required, including kveld/hxreborn comparison.
2. Third-party runtime settings integration API: prove or choose extension-integration alternative.
3. Capture actual Japanese feed protobuf/view-count text on 21.46.522.
4. Confirm exact upstream GPL terms and notices.

No patch code or APK built in this phase.
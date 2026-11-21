package app.rikunode.morphe.patches.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.util.getFreeRegisterProvider
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val FILTER = "Lapp/rikunode/morphe/extension/youtube/filters/ViewCountFilter;"
private const val SETTINGS_ENTRY =
    "Lapp/rikunode/morphe/extension/youtube/settings/ViewCountSettingsEntry;"
private const val ACTIVITY =
    "app.rikunode.morphe.extension.youtube.settings.ViewCountSettingsActivity"
private const val PROVIDER =
    "app.rikunode.morphe.extension.youtube.settings.ViewCountInitProvider"

private val youtube2130 = Compatibility(
    name = "YouTube",
    packageName = "com.google.android.youtube",
    apkFileType = ApkFileType.APK,
    appIconColor = 0xFF0000,
    targets = listOf(AppTarget(version = "21.30.216")),
)

/**
 * These fingerprints locate original APK-side classes. No Morphe patch-internal
 * symbols, extension registries, or settings APIs are used.
 */
private object ComponentCreatorFingerprint : Fingerprint(
    returnType = "L",
    filters = listOf(
        string("Element missing correct type extension"),
        string("Element missing type"),
    ),
)

private object EmptyComponentParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.CONSTRUCTOR),
    parameters = listOf(),
    filters = listOf(string("EmptyComponent")),
)

private object EmptyComponentBuilderFingerprint : Fingerprint(
    classFingerprint = EmptyComponentParentFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    returnType = "L",
    parameters = listOf("L"),
)

private object SettingsScreenFingerprint : Fingerprint(
    definingClass = "Lgek;",
    name = "u",
    parameters = listOf("Landroidx/preference/PreferenceScreen;"),
    returnType = "V",
)

@Suppress("unused")
val viewCountFilterPatch = patch(
    name = "View count filter (Rikunode)",
    description = "Independent feed component filter and runtime settings for YouTube 21.30.216.",
    default = false,
) {
    compatibleWith(youtube2130)
    extendWith("extensions/extension.mpe")

    execute {
        val builder = EmptyComponentBuilderFingerprint.method
        val emptyComponentField = classDefBy(builder.returnType).fields.single()

        val converter = ComponentCreatorFingerprint.method
        val converterInstructions = converter.implementation!!.instructions.toList()
        val end = converterInstructions.indexOfLast { it.opcode == Opcode.RETURN_OBJECT }
        require(end >= 0) { "APK-side component converter has no object return" }
        val resultRegister = (converterInstructions[end] as OneRegisterInstruction).registerA
        val p0 = converter.p0Register
        require(converter.parameterTypes.size == 6) { "Unexpected component converter signature" }
        // p1 and p3 are required after allocating the temporary.
        val temp = converter.getFreeRegisterProvider(
            end, 1, resultRegister, p0 + 1, p0 + 3
        ).getFreeRegister4Bit()
        converter.addInstructionsWithLabels(
            end,
            """
                # Original app conversion and the official filter have already run.
                move-object/from16 v$temp, p3
                invoke-static { v$temp }, $FILTER->shouldDrop(Ljava/lang/Object;)Z
                move-result v$temp
                if-eqz v$temp, :viewcount_keep

                # APK-side empty component builder, not a Morphe extension class.
                move-object/from16 v$temp, p1
                invoke-static { v$temp }, $builder
                move-result-object v$temp
                iget-object v$temp, v$temp, $emptyComponentField
                move-object/from16 v$resultRegister, v$temp

                :viewcount_keep
                nop
            """.trimIndent(),
        )
        println("Rikunode view-count hook: ${converter.definingClass}->${converter.name}")

        // Lgek.u(PreferenceScreen) is the single APK-side write point for the
        // active settings screen. Restrict installation to the main Lpty fragment.
        val run = SettingsScreenFingerprint.method
        require(run.definingClass == "Lgek;") {
            "Settings screen setter resolved to unexpected class: ${run.definingClass}"
        }
        // YouTube 21.30.216: registers=4, p0=v2, p1=v3. The original
        // method overwrites v3 with integer 1 after assigning the screen,
        // so injection at the return would pass an integer to install().
        require(run.implementation!!.registerCount == 4 && run.p0Register == 2) {
            "Unexpected Lgek.u register layout"
        }
        run.addInstructionsWithLabels(
            0,
            """
                instance-of v0, v2, Lpty;
                if-eqz v0, :viewcount_no_settings
                invoke-static { v3 }, $SETTINGS_ENTRY->install(Ljava/lang/Object;)V
                :viewcount_no_settings
                nop
            """.trimIndent(),
        )
        println("Rikunode settings hook: ${run.definingClass}->${run.name} gated by Lpty")

        // Official Morphe's settings patch merges this declaration into its own
        // settings screen when both bundles are applied in one session.
        // The native settings hook remains the standalone fallback.
        resourceFile("morphe_addon_prefs.xml").writeText(
            """<morphe-add-on-preferences xmlns:android="http://schemas.android.com/apk/res/android">
                <screen>
                    <Preference android:key="rikunode_viewcount_settings"
                        android:title="View count filter / 再生回数フィルタ"
                        android:summary="独立した再生回数フィルタの設定">
                        <intent android:action="android.intent.action.VIEW"
                            android:targetPackage="com.google.android.youtube"
                            android:targetClass="$ACTIVITY" />
                    </Preference>
                </screen>
            </morphe-add-on-preferences>""".trimIndent()
        )

    }

    // Re-assert component declarations after all patch execute phases. Earlier
    // manifest edits are not sufficient evidence that the final APK retains them.
    finalize {
        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.root.children("application").single()
            if (application.children("activity").none { it.attribute("android:name") == ACTIVITY }) {
                application.appendChild("activity") {
                    setAttribute("android:name", ACTIVITY)
                    setAttribute("android:exported", "false")
                }
            }
            if (application.children("provider").none { it.attribute("android:name") == PROVIDER }) {
                application.appendChild("provider") {
                    setAttribute("android:name", PROVIDER)
                    setAttribute("android:authorities", "com.google.android.youtube.rikunode.viewcount.init")
                    setAttribute("android:exported", "false")
                    setAttribute("android:initOrder", "100")
                }
            }
            require(application.children("activity").any { it.attribute("android:name") == ACTIVITY }) {
                "Missing ViewCountSettingsActivity declaration in final manifest"
            }
        }
    }
}

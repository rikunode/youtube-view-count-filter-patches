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

private object SettingsScreenBuilderFingerprint : Fingerprint(
    name = "run",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    returnType = "V",
    filters = listOf(
        string(":android:show_fragment_args"),
        methodCall(
            opcode = Opcode.INVOKE_VIRTUAL,
            parameters = listOf(),
            returnType = "Landroidx/preference/PreferenceScreen;",
        ),
        opcode(Opcode.RETURN_VOID),
    ),
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

        val settings = SettingsScreenBuilderFingerprint
        val screenGetter = settings.instructionMatches[1].methodReference
        val fragmentField = settings.classDef.fields.single {
            it.type == screenGetter.definingClass
        }
        val run = settings.method
        val runInstructions = run.implementation!!.instructions.toList()
        val returnIndex = runInstructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
        require(returnIndex >= 0) { "Could not locate native YouTube settings return" }
        val settingRegister = run.getFreeRegisterProvider(
            returnIndex, 1, run.p0Register
        ).getFreeRegister4Bit()
        run.addInstructionsWithLabels(
            returnIndex,
            """
                move-object/from16 v$settingRegister, p0
                iget-object v$settingRegister, v$settingRegister, $fragmentField
                if-eqz v$settingRegister, :viewcount_no_settings
                invoke-virtual { v$settingRegister }, $screenGetter
                move-result-object v$settingRegister
                if-eqz v$settingRegister, :viewcount_no_settings
                invoke-static { v$settingRegister }, $SETTINGS_ENTRY->install(Ljava/lang/Object;)V
                :viewcount_no_settings
                nop
            """.trimIndent(),
        )
        println("Rikunode settings hook: ${run.definingClass}->${run.name}")

        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.root.children("application").single()
            application.appendChild("activity") {
                setAttribute("android:name", ACTIVITY)
                setAttribute("android:exported", "false")
            }
            application.appendChild("provider") {
                setAttribute("android:name", PROVIDER)
                setAttribute("android:authorities", "com.google.android.youtube.rikunode.viewcount.init")
                setAttribute("android:exported", "false")
                setAttribute("android:initOrder", "100")
            }
        }
    }
}

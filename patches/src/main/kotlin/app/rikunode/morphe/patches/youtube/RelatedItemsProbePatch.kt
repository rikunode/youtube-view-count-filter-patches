package app.rikunode.morphe.patches.youtube

import app.morphe.patcher.patch.*
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.BuilderSwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val PROBE = "Lapp/rikunode/morphe/extension/youtube/filters/RelatedItemsProbe;"
private const val ENTRY = "Lapp/rikunode/morphe/extension/youtube/settings/ViewCountSettingsEntry;"
private const val ACT = "app.rikunode.morphe.extension.youtube.settings.ViewCountSettingsActivity"
private const val INIT = "app.rikunode.morphe.extension.youtube.settings.RelatedItemsProbeProvider"
private const val EXTRA = 24

// Negative registers refer to explicitly saved method-local values, not an app-global context.
private data class Site(
    val offset: Int, val opcode: Opcode, val reference: String? = null,
    val operands: List<Int>? = null, val after: Boolean = false,
    val event: String? = null, val args: List<Int> = emptyList(),
    val number: Int? = null, val saves: Map<Int, Int> = emptyMap()
)
private data class Target(val dex: String, val descriptor: String, val registers: Int,
                          val sites: List<Site>, val returns: Boolean = false)
private fun s(off: Int, op: Opcode, ref: String?, regs: List<Int>, event: String?,
              vararg args: Int, after: Boolean = false, num: Int? = null,
              saves: Map<Int, Int> = emptyMap()) =
    Site(off, op, ref, regs, after, event, args.toList(), num, saves)

private val targets = listOf(
    Target("classes.dex", "Lrzm;->nu()Ljava/lang/Object;", 60, listOf(
        s(0x0912, Opcode.IPUT_OBJECT, "Lrqa;->am:Llpu;", listOf(2,0), "controller", 0,2, after=true)
    )),
    Target("classes4.dex", "Lrqa;->t(Landroid/os/Bundle;Lakmv;Lamqu;)V", 12, listOf(
        s(0x008f, Opcode.INVOKE_STATIC, "Lrqa;->v(Lakmq;Ltip;)Lakmq;", listOf(0,1), "watch_t_pre",4,0),
        s(0x0095, Opcode.INVOKE_VIRTUAL, "Latnz;->ao(Lakmq;Lamqu;Landroid/os/Bundle;Z)V", listOf(4,0,11,9,1), "watch_t_post",4,0)
    )),
    Target("classes4.dex", "Lrqa;->s(Lakmv;)V", 6, listOf(
        s(0x003f, Opcode.INVOKE_STATIC, "Lrqa;->v(Lakmq;Ltip;)Lakmq;", listOf(5,0), "watch_s_pre",1,5),
        s(0x0043, Opcode.INVOKE_VIRTUAL, "Latnz;->ay(Lakmq;)V", listOf(1,5), "watch_s_post",1,5)
    )),
    Target("classes4.dex", "Lrsh;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", 21, listOf(
        s(0x005f, Opcode.INVOKE_VIRTUAL, "Latqv;->j(Lakmn;)V", listOf(2,1), "section_bind",2,1,20)
    )),
    Target("classes4.dex", "Lltq;->a(Lasdi;Lasdg;)V", 9, listOf(
        s(0x0000, Opcode.IGET, "Lltq;->b:I", listOf(8,6), null, saves=mapOf(-1 to 6,-2 to 8)),
        s(0x0127, Opcode.IGET_OBJECT, "Lbggp;->e:Lbadh;", listOf(3,8), "update_input",2,8,-1,-2),
        s(0x014f, Opcode.INVOKE_VIRTUAL, "Lqca;->mO(Lakmn;)V", listOf(2,4), "update_model",2,4,8,-1,-2),
        s(0x01cc, Opcode.INVOKE_VIRTUAL, "Lrrn;->B(Ljava/util/Collection;)V", listOf(0,6), "update_rows",0,6,-1,-2)
    )),
    Target("classes2.dex", "Latqv;->mO(Lakmn;)V", 3, listOf(
        s(0x0000, Opcode.IF_NEZ, null, listOf(2), null, saves=mapOf(-1 to 2)),
        s(0x0010, Opcode.INVOKE_VIRTUAL, "Laurc;->g(Ljava/util/List;)Ljava/util/List;", listOf(0,2), "children_input",1,-1,2),
        s(0x0014, Opcode.INVOKE_VIRTUAL, "Latpj;->K(Ljava/util/Collection;)V", listOf(1,2), "children_rows",1,-1,2)
    )),
    Target("classes2.dex", "Latqv;->j(Lakmn;)V", 7, listOf(
        s(0x0000, Opcode.INVOKE_VIRTUAL, "Latpj;->p()V", listOf(5), "children_bind_enter",5,6),
        s(0x00a6, Opcode.INVOKE_STATIC, "Lanti;->cP(Lbggn;)Lcom/google/protobuf/MessageLite;", listOf(0), null, saves=mapOf(-1 to 0)),
        s(0x00a9, Opcode.MOVE_RESULT_OBJECT, null, listOf(0), "children_elements",5,6,-1,0, after=true),
        s(0x00cc, Opcode.INVOKE_VIRTUAL, "Latpj;->K(Ljava/util/Collection;)V", listOf(5,0), "children_element_rows",5,6,0)
    )),
    Target("classes2.dex", "Latqv;->hX(Lakmn;Lasdh;)V", 3, listOf(
        s(0x000a, Opcode.INVOKE_VIRTUAL, "Latqv;->z(Lakmn;Lasdg;)V", listOf(0,1,2), "children_mode",0,1,2)
    )),
    Target("classes.dex", "Lrrn;->B(Ljava/util/Collection;)V", 4, listOf(
        s(0x0013, Opcode.INVOKE_VIRTUAL, "Latpj;->K(Ljava/util/Collection;)V", listOf(2,3), "children_append",2,3)
    )),
    Target("classes2.dex", "Latqs;->b(Ljava/lang/Object;Latsk;)V", 4, listOf(
        s(0x0038, Opcode.INVOKE_STATIC, "Lajec;->aQ(Lbggs;)Lcom/google/protobuf/MessageLite;", listOf(2), null, saves=mapOf(-1 to 2)),
        s(0x003b, Opcode.MOVE_RESULT_OBJECT, null, listOf(2), "child_atqs", -1,2, after=true)
    )),
    Target("classes4.dex", "Lrpi;->b(Ljava/lang/Object;Latsk;)V", 4, listOf(
        s(0x000f, Opcode.INVOKE_STATIC, "Lajec;->aQ(Lbggs;)Lcom/google/protobuf/MessageLite;", listOf(2), null),
        s(0x0012, Opcode.MOVE_RESULT_OBJECT, null, listOf(1), "child_rpi",2,1, after=true)
    )),
    Target("classes2.dex", "Lastj;-><init>(Lbdsp;)V", 4, listOf(
        s(0x002f, Opcode.RETURN_VOID, null, emptyList(), "root_construct",2)
    )),
    Target("classes2.dex", "Lvwn;->b(Liev;Lxcv;Lastj;Lxds;Lbsot;)Lieq;", 6, listOf(
        s(0x0000, Opcode.IGET_OBJECT, "Lastj;->c:[B", listOf(3,3), "root_vwn_in",1,2,3, saves=mapOf(-1 to 3)),
        s(0x0004, Opcode.INVOKE_INTERFACE_RANGE, "Lxed;->b(Liev;Lxcv;[BLxds;Lbsot;)Lieq;", (0..5).toList(), "root_vwn_bytes",1,2,-1,3)
    )),
    Target("classes.dex", "Lvpo;->b(Liev;Lxcv;[BLxds;Lbsot;)Lieq;", 13, listOf(
        s(0x0000, Opcode.IGET, "Lvpo;->k:I", listOf(0,7), null, saves=mapOf(-1 to 8,-2 to 9,-3 to 10)),
        s(0x003b, Opcode.INVOKE_VIRTUAL_RANGE, "Lvpo;->a(Liev;Lxcv;Lwls;Lxds;Lbsot;)Lieq;", (1..6).toList(), "root_resolved",-1,-2,-3,2,3,4)
    )),
    Target("classes.dex", "Lvpo;->d(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;ILyay;Z)Lieq;", 37, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(1,28), "element_enter",29,30,31, saves=mapOf(-1 to 29,-2 to 30,-3 to 31)),
        s(0x007c, Opcode.INVOKE_DIRECT, "Lvpo;->e(Lwpx;)I", listOf(1,3), null),
        s(0x007f, Opcode.MOVE_RESULT, null, listOf(6), "element_type",-2,-3, after=true, num=6),
        s(0x00c4, Opcode.INVOKE_INTERFACE_RANGE, "Lwls;->k()Ljava/lang/String;", listOf(31), null),
        s(0x00c7, Opcode.MOVE_RESULT_OBJECT, null, listOf(13), "element_key",-2,-3,13, after=true),
        s(0x0162, Opcode.IPUT_OBJECT, "Lxcu;->g:Ljava/lang/String;", listOf(6,11), "element_path",-2,-3,6),
        s(0x04dc, Opcode.INVOKE_DIRECT_RANGE, "Lvpo;->f(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;Z)Ljava/util/List;", (1..7).toList(), "edge_d_1",-2,-3,2,3,4),
        s(0x0562, Opcode.INVOKE_DIRECT_RANGE, "Lvpo;->f(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;Z)Ljava/util/List;", (1..7).toList(), "edge_d_2",-2,-3,2,3,4)
    ), returns=true),
    Target("classes.dex", "Lvpo;->f(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;Z)Ljava/util/List;", 22, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(2,17), "edge_f_enter",16,17,18),
        s(0x0045, Opcode.INVOKE_INTERFACE, "Lwls;->h(I)Lwls;", listOf(13,6), null),
        s(0x0048, Opcode.MOVE_RESULT_OBJECT, null, listOf(3), "edge_child",13,2,3,16, after=true, num=6),
        s(0x005f, Opcode.INVOKE_DIRECT_RANGE, "Lvpn;-><init>(Lvpo;Lwls;Lxds;ILyay;)V", (3..8).toList(), "edge_lazy",13,2,5,3,16, after=true, num=7),
        s(0x0085, Opcode.INVOKE_VIRTUAL_RANGE, "Lvpo;->d(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;ILyay;Z)Lieq;", (0..8).toList(), "edge_immediate",13,2,3,1, num=6)
    )),
    Target("classes5.dex", "Lvpn;->a(Liev;Lxcv;)Lieq;", 12, listOf(
        s(0x0012, Opcode.INVOKE_VIRTUAL_RANGE, "Lvpo;->d(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;ILyay;Z)Lieq;", (0..8).toList(), "edge_lazy_call",9,1,2,3, num=6)
    )),
    Target("classes2.dex", "Latjd;->E(Latix;I)V", 6, listOf(
        s(0x0005, Opcode.MOVE_RESULT_OBJECT, null, listOf(0), "row_view",0, after=true, saves=mapOf(-1 to 0)),
        s(0x0041, Opcode.MOVE_RESULT_OBJECT, null, listOf(5), "row_prepare",-1,5,4,1, after=true),
        s(0x0054, Opcode.IGET_OBJECT, "Latjd;->a:Ljava/util/HashSet;", listOf(3,3), "row_bind",-1,5,4,1)
    )),
    Target("classes2.dex", "Lastr;->k(Lastj;Liev;Lxcv;Latiq;Lbsot;)Lieq;", 13, listOf(
        s(0x0000, Opcode.IGET_OBJECT, "Lampz;->a:Lampe;", listOf(0,11), null, saves=mapOf(-1 to 11)),
        s(0x000f, Opcode.INVOKE_VIRTUAL_RANGE, "Lvwn;->b(Liev;Lxcv;Lastj;Lxds;Lbsot;)Lieq;", (1..6).toList(), "root_row",-1,4,2,3)
    ))
)

private fun offsetMap(method: MutableMethod): Map<Int, Int> {
    var offset = 0
    return method.implementation!!.instructions.mapIndexed { index, instruction ->
        (offset to index).also { offset += instruction.codeUnits }
    }.toMap()
}

private fun descriptor(m: MutableMethod) = "${m.definingClass}->${m.name}(${m.parameterTypes.joinToString("")})${m.returnType}"

private data class Checked(val target: Target, val method: MutableMethod, val indices: Map<Int, Int>)

private fun verifyTarget(t: Target): Checked {
    val owner = t.descriptor.substringBefore("->")
    val m = mutableClassDefBy(owner).methods.single { descriptor(it) == t.descriptor }
    require(m.implementation!!.registerCount == t.registers) { "Register layout changed: ${t.descriptor}" }
    val indices = offsetMap(m)
    for (site in t.sites) {
        val i = m.implementation!!.instructions[indices[site.offset] ?: error("Missing offset ${site.offset}: ${t.descriptor}")]
        require(i.opcode == site.opcode &&
            (site.reference == null || (i as? ReferenceInstruction)?.reference.toString() == site.reference) &&
            (site.operands == null || i.registersUsed == site.operands)) {
            "Probe target mismatch ${t.descriptor}:${site.offset.toString(16)}: ${i.opcode} ${(i as? ReferenceInstruction)?.reference} ${i.registersUsed}"
        }
    }
    return Checked(t,m,indices)
}

// Expand only our instrumented methods. Keep every original v-register number and
// every original instruction/try handler; copy the shifted incoming bank at entry.
private fun instrument(c: Checked) {
    val old = c.method
    val original = old.implementation!!
    val count = original.registerCount
    val expanded = ImmutableMethodImplementation(count + EXTRA, original.instructions, original.tryBlocks, original.debugItems)
    val m = ImmutableMethod(old.definingClass, old.name, old.parameters, old.returnType,
        old.accessFlags, old.annotations, old.hiddenApiRestrictions, expanded).toMutable()
    val impl = m.implementation!!
    val originalInstructions = impl.instructions.toList()
    require(offsetMap(m) == c.indices) { "Instruction layout changed when copying ${c.target.descriptor}" }
    val branches = originalInstructions.flatMap {
        when(it) {
            is BuilderOffsetInstruction -> listOf(it.target)
            is BuilderSwitchPayload -> it.switchElements.map { e -> e.target }
            else -> emptyList()
        }
    }.toSet()
    fun reg(n: Int) = if (n >= 0) n else count + (-n - 1)
    val base = count + 5
    fun code(site: Site): String = buildString {
        site.saves.forEach { (dest, src) -> appendLine("move-object/from16 v${reg(dest)}, v$src") }
        if (site.event != null) {
            appendLine("const-string v$base, \"${site.event}\"")
            repeat(6) { i ->
                val dst = base + i + 1
                val src = site.args.getOrNull(i)
                if (src == null) appendLine("const/16 v$dst, 0")
                else appendLine("move-object/from16 v$dst, v${reg(src)}")
            }
            if (site.number == null) appendLine("const/16 v${base+7}, -1")
            else appendLine("move/from16 v${base+7}, v${site.number}")
            appendLine("invoke-static/range {v$base .. v${base+7}}, $PROBE->record(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)V")
        }
    }
    val injections = c.target.sites.map { c.indices.getValue(it.offset) to it }.toMutableList()
    if (c.target.returns) {
        originalInstructions.forEachIndexed { index, insn ->
            if (insn.opcode == Opcode.RETURN_OBJECT)
                injections += index to Site(-1, insn.opcode, event="element_exit",
                    args=listOf(-2,-3,(insn as OneRegisterInstruction).registerA))
        }
    }
    // Use original instruction identities so branch widening cannot shift a later target.
    injections.sortedWith(compareByDescending<Pair<Int,Site>> { it.first }.thenByDescending { it.second.after }).forEach { (index, site) ->
        val anchor = originalInstructions[index]
        val at = impl.instructions.indexOfFirst { it === anchor }
        require(at >= 0) { "Lost original instruction ${c.target.descriptor}" }
        val smali = code(site)
        if (smali.isBlank()) return@forEach
        val insertion = at + if (site.after) 1 else 0
        val incoming = if (site.after) emptyList() else anchor.location.labels.filter { it in branches }
        m.addInstructions(insertion, smali)
        val first = impl.instructions[insertion]
        incoming.forEach { label ->
            anchor.location.labels.remove(label)
            first.location.labels.add(label)
        }
    }
    val types = listOf(old.definingClass) + old.parameterTypes.map { it.toString() }
    require(types.none { it == "J" || it == "D" })
    val firstParam = count - types.size
    require(base + 7 < firstParam + EXTRA)
    m.addInstructions(0, types.mapIndexed { i, type ->
        val op = if (type.startsWith("L") || type.startsWith("[")) "move-object/from16" else "move/from16"
        "$op v${firstParam+i}, v${firstParam+i+EXTRA}"
    }.joinToString("\n"))
    val owner = mutableClassDefBy(old.definingClass)
    require(owner.methods.remove(old) && owner.methods.add(m))
    println("Related probe verified and instrumented: ${c.target.dex} ${c.target.descriptor} (${c.target.sites.size} sites)")
}

@Suppress("unused")
val relatedItemsProbePatch = patch(
    name = "Related items diagnostic probe (Rikunode)",
    description = "Observation-only related-items probe. Select instead of View count filter. YouTube 21.30.216 only.",
    default = false,
) {
    compatibleWith(Compatibility(name="YouTube", packageName="com.google.android.youtube",
        apkFileType=ApkFileType.APK, appIconColor=0xFF0000,
        targets=listOf(AppTarget(version="21.30.216"))))
    extendWith("extensions/extension.mpe")
    execute {
        val checked = targets.map(::verifyTarget)
        val settings = mutableClassDefBy("Lgek;").methods.single {
            descriptor(it) == "Lgek;->u(Landroidx/preference/PreferenceScreen;)V"
        }
        require(settings.implementation!!.registerCount == 4)
        checked.forEach(::instrument)
        // Native settings fallback, identical gate to the existing patch.
        app.morphe.patcher.extensions.InstructionExtensions.run {
            settings.addInstructionsWithLabels(0, """
                instance-of v0, v2, Lpty;
                if-eqz v0, :probe_no_settings
                invoke-static {v3}, $ENTRY->install(Ljava/lang/Object;)V
                :probe_no_settings
                nop
            """.trimIndent())
        }
        val pkg = resourceXml("AndroidManifest.xml").use { it.root.attribute("package") ?: error("Missing package") }
        resourceFile("morphe_addon_prefs.xml").writeText("""
            <morphe-add-on-preferences xmlns:android="http://schemas.android.com/apk/res/android">
                <screen>
                    <Preference android:key="rikunode_viewcount_settings"
                        android:title="Related items diagnostic probe"
                        android:summary="Observation-only JSON Lines"
                        ><intent android:action="android.intent.action.VIEW"
                            android:targetPackage="$pkg" android:targetClass="$ACT" /></Preference>
                </screen>
            </morphe-add-on-preferences>
        """.trimIndent())
    }
    finalize {
        // A diagnostic build must not contain the previous filter's rejection path.
        require(mutableClassDefBy("Lvvz;").methods.none { method ->
            method.implementation?.instructions?.any { i ->
                (i as? ReferenceInstruction)?.reference.toString().contains("ViewCountFilter;->shouldDrop")
            } == true
        }) { "Select only Related items diagnostic probe, not View count filter" }
        val pkg = resourceXml("AndroidManifest.xml").use { it.root.attribute("package") ?: error("Missing package") }
        listOf("morphe_addon_prefs.xml", "res/xml/morphe_prefs.xml", "res/xml/morphe_prefs_icons.xml", "res/xml/morphe_prefs_icons_bold.xml").forEach { file ->
            resourceXmlOrNull(file)?.use { xml ->
                xml.elements("intent").filter { it.attribute("android:targetClass") == ACT }
                    .forEach { it.setAttribute("android:targetPackage", pkg) }
            }
        }
        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.root.children("application").single()
            if (application.children("activity").none { it.attribute("android:name") == ACT })
                application.appendChild("activity") {
                    setAttribute("android:name", ACT)
                    setAttribute("android:exported", "false")
                }
            application.appendChild("provider") {
                setAttribute("android:name", INIT)
                setAttribute("android:authorities", "$pkg.rikunode.relatedprobe.init")
                setAttribute("android:exported", "false")
                setAttribute("android:initOrder", "100")
            }
        }
    }
}

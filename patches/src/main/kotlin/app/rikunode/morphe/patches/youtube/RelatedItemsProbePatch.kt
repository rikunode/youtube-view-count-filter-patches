package app.rikunode.morphe.patches.youtube

import app.morphe.patcher.patch.*
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderInstruction
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.BuilderSwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

private const val PROBE = "Lapp/rikunode/morphe/extension/youtube/diagnostic/filters/RelatedItemsProbe;"
private const val ENTRY = "Lapp/rikunode/morphe/extension/youtube/diagnostic/settings/ViewCountSettingsEntry;"
private const val ACT = "app.rikunode.morphe.extension.youtube.diagnostic.settings.RelatedItemsProbeActivity"
private const val INIT = "app.rikunode.morphe.extension.youtube.diagnostic.settings.RelatedItemsProbeProvider"
private const val EXTRA = 24

// Raw APK offsets are provenance/log labels only.
private data class Anchor(val opcode: Opcode, val reference: String?, val operands: List<Int>) {
    // Numbers in the specification are symbolic roles, not live register indices.
    fun bind(i: Instruction, roles: MutableMap<Int, Int>): Boolean {
        if (i.opcode != opcode ||
            (reference != null && (i as? ReferenceInstruction)?.reference.toString() != reference)) return false
        val actual = i.registersUsed
        if (actual.size != operands.size) return false
        return operands.zip(actual).all { (role, live) -> bindRole(roles, role, live) }
    }
}
private fun a(op: Opcode, ref: String?, vararg regs: Int) = Anchor(op, ref, regs.toList())

private fun bindRole(roles: MutableMap<Int, Int>, role: Int, live: Int): Boolean {
    val existing = roles[role]
    if (existing != null) return existing == live
    if (live in roles.values) return false
    roles[role] = live
    return true
}

private fun parameterTypes(m: MutableMethod) =
    (if (AccessFlags.STATIC.isSet(m.accessFlags)) emptyList() else listOf(m.definingClass)) +
        m.parameterTypes.map { it.toString() }

// Recognize a complete cloneParameters prologue, not arbitrary instructions.
// The current incoming bank can be higher than the working bank used by the body.
private fun workingParameters(instructions: List<Instruction>, count: Int, types: List<String>, rawCount: Int): Pair<Int, List<Int>> {
    require(types.none { it == "J" || it == "D" })
    val incoming = (count - types.size until count).toList()
    require(incoming.first() >= 0)
    // An ordinary first move (especially in a one-parameter method) is not cloning.
    if (count < rawCount + types.size) return 0 to incoming
    val copies = instructions.take(types.size)
    if (copies.size != types.size) return 0 to incoming
    val destinations = mutableListOf<Int>()
    for ((index, i) in copies.withIndex()) {
        val objectType = types[index].startsWith("L") || types[index].startsWith("[")
        val allowed = if (objectType) setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)
                      else setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16)
        val regs = i.registersUsed
        if (i.opcode !in allowed || regs.size != 2 || regs[1] != incoming[index] ||
            regs[0] >= incoming.first() || regs[0] in destinations) return 0 to incoming
        destinations += regs[0]
    }
    if (destinations != (destinations.first() until destinations.first() + types.size).toList()) return 0 to incoming
    return copies.size to destinations
}

// Negative registers refer to explicitly saved method-local values, not an app-global context.
private data class Site(
    val offset: Int, val opcode: Opcode, val reference: String? = null,
    val operands: List<Int>? = null, val after: Boolean = false,
    val event: String? = null, val args: List<Int> = emptyList(),
    val number: Int? = null, val saves: Map<Int, Int> = emptyMap(),
    val before: Anchor? = null, val following: Anchor? = null, val following2: Anchor? = null
)
private data class Target(val dex: String, val descriptor: String, val registers: Int,
                          val sites: List<Site>, val returns: Boolean = false,
                          val entryEvent: String? = null, val predicateReturns: Boolean = false)
private fun s(off: Int, op: Opcode, ref: String?, regs: List<Int>, event: String?,
              vararg args: Int, after: Boolean = false, num: Int? = null,
              saves: Map<Int, Int> = emptyMap(), before: Anchor? = null, following: Anchor? = null, following2: Anchor? = null) =
    Site(off, op, ref, regs, after, event, args.toList(), num, saves, before, following, following2)

private val targets = listOf(
    Target("classes.dex", "Lrzm;->nu()Ljava/lang/Object;", 60, listOf(
        s(0x0912, Opcode.IPUT_OBJECT, "Lrqa;->am:Llpu;", listOf(2,0), "controller", 0,2, after=true, before=a(Opcode.MOVE_RESULT_OBJECT, null, 2))
    ), entryEvent="reach_controller"),
    Target("classes4.dex", "Lrqa;->t(Landroid/os/Bundle;Lakmv;Lamqu;)V", 12, listOf(
        s(0x008f, Opcode.INVOKE_STATIC, "Lrqa;->v(Lakmq;Ltip;)Lakmq;", listOf(0,1), "watch_t_pre",4,0),
        s(0x0095, Opcode.INVOKE_VIRTUAL, "Latnz;->ao(Lakmq;Lamqu;Landroid/os/Bundle;Z)V", listOf(4,0,11,9,1), "watch_t_post",4,0)
    ), entryEvent="reach_watch_t"),
    Target("classes4.dex", "Lrqa;->s(Lakmv;)V", 6, listOf(
        s(0x003f, Opcode.INVOKE_STATIC, "Lrqa;->v(Lakmq;Ltip;)Lakmq;", listOf(5,0), "watch_s_pre",1,5),
        s(0x0043, Opcode.INVOKE_VIRTUAL, "Latnz;->ay(Lakmq;)V", listOf(1,5), "watch_s_post",1,5)
    ), entryEvent="reach_watch_s"),
    Target("classes4.dex", "Lrsh;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", 21, listOf(
        s(0x0023, Opcode.MOVE_RESULT, null, listOf(2), "bind_predicate",1,20, after=true, num=2,
            before=a(Opcode.INVOKE_STATIC, "Lrrn;->C(Lakmn;)Z", 1), following=a(Opcode.IF_EQZ, null, 2)),
        s(0x005f, Opcode.INVOKE_VIRTUAL, "Latqv;->j(Lakmn;)V", listOf(2,1), "section_bind",2,1,20)
    ), entryEvent="reach_section_bind"),
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
        s(0x00a9, Opcode.MOVE_RESULT_OBJECT, null, listOf(0), "children_elements",5,6,-1,0, after=true, before=a(Opcode.INVOKE_STATIC, "Lanti;->cP(Lbggn;)Lcom/google/protobuf/MessageLite;", 0)),
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
        s(0x003b, Opcode.MOVE_RESULT_OBJECT, null, listOf(2), "child_atqs", -1,2, after=true, before=a(Opcode.INVOKE_STATIC, "Lajec;->aQ(Lbggs;)Lcom/google/protobuf/MessageLite;", 2))
    )),
    Target("classes4.dex", "Lrpi;->b(Ljava/lang/Object;Latsk;)V", 4, listOf(
        s(0x000f, Opcode.INVOKE_STATIC, "Lajec;->aQ(Lbggs;)Lcom/google/protobuf/MessageLite;", listOf(2), null),
        s(0x0012, Opcode.MOVE_RESULT_OBJECT, null, listOf(1), "child_rpi",2,1, after=true, before=a(Opcode.INVOKE_STATIC, "Lajec;->aQ(Lbggs;)Lcom/google/protobuf/MessageLite;", 2))
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
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(1,28), "element_enter",29,30,31, saves=mapOf(-1 to 29,-2 to 30,-3 to 31), following=a(Opcode.MOVE_OBJECT_FROM16, null, 2,29), following2=a(Opcode.MOVE_OBJECT_FROM16, null, 5,30)),
        s(0x007c, Opcode.INVOKE_DIRECT, "Lvpo;->e(Lwpx;)I", listOf(1,3), null, following=a(Opcode.MOVE_RESULT, null, 6), following2=a(Opcode.GOTO, null)),
        s(0x007f, Opcode.MOVE_RESULT, null, listOf(6), "element_type",-2,-3, after=true, num=6, before=a(Opcode.INVOKE_DIRECT, "Lvpo;->e(Lwpx;)I", 1,3), following=a(Opcode.GOTO, null)),
        s(0x00c4, Opcode.INVOKE_INTERFACE_RANGE, "Lwls;->k()Ljava/lang/String;", listOf(31), null, following=a(Opcode.MOVE_RESULT_OBJECT, null, 13), following2=a(Opcode.INVOKE_STATIC, "Lavqw;->an(Ljava/lang/String;)Z", 13)),
        s(0x00c7, Opcode.MOVE_RESULT_OBJECT, null, listOf(13), "element_key",-2,-3,13, after=true, before=a(Opcode.INVOKE_INTERFACE_RANGE, "Lwls;->k()Ljava/lang/String;", 31), following=a(Opcode.INVOKE_STATIC, "Lavqw;->an(Ljava/lang/String;)Z", 13)),
        s(0x0162, Opcode.IPUT_OBJECT, "Lxcu;->g:Ljava/lang/String;", listOf(6,11), "element_path",-2,-3,6),
        s(0x04dc, Opcode.INVOKE_DIRECT_RANGE, "Lvpo;->f(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;Z)Ljava/util/List;", (1..7).toList(), "edge_d_1",-2,-3,2,3,4, before=a(Opcode.MOVE_OBJECT_FROM16, null, 4,31), following=a(Opcode.MOVE_RESULT_OBJECT, null, 3)),
        s(0x0562, Opcode.INVOKE_DIRECT_RANGE, "Lvpo;->f(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;Z)Ljava/util/List;", (1..7).toList(), "edge_d_2",-2,-3,2,3,4, before=a(Opcode.MOVE_OBJECT, null, 3,8), following=a(Opcode.MOVE_RESULT_OBJECT, null, 7))
    ), returns=true),
    Target("classes.dex", "Lvpo;->f(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;Z)Ljava/util/List;", 22, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(2,17), "edge_f_enter",16,17,18, following=a(Opcode.INVOKE_INTERFACE_RANGE, "Lwls;->g()I", 18)),
        s(0x0045, Opcode.INVOKE_INTERFACE, "Lwls;->h(I)Lwls;", listOf(13,6), null),
        s(0x0048, Opcode.MOVE_RESULT_OBJECT, null, listOf(3), "edge_child",13,2,3,16, after=true, num=6, before=a(Opcode.INVOKE_INTERFACE, "Lwls;->h(I)Lwls;", 13,6)),
        s(0x005f, Opcode.INVOKE_DIRECT_RANGE, "Lvpn;-><init>(Lvpo;Lwls;Lxds;ILyay;)V", (3..8).toList(), "edge_lazy",13,2,5,3,16, after=true, num=7),
        s(0x0085, Opcode.INVOKE_VIRTUAL_RANGE, "Lvpo;->d(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;ILyay;Z)Lieq;", (0..8).toList(), "edge_immediate",13,2,3,1, num=6)
    )),
    Target("classes5.dex", "Lvpn;->a(Liev;Lxcv;)Lieq;", 12, listOf(
        s(0x0012, Opcode.INVOKE_VIRTUAL_RANGE, "Lvpo;->d(Liev;Lxcv;Lwls;Lxds;Ljava/util/List;ILyay;Z)Lieq;", (0..8).toList(), "edge_lazy_call",9,1,2,3, num=6)
    )),
    Target("classes2.dex", "Latjd;->E(Latix;I)V", 6, listOf(
        s(0x0005, Opcode.MOVE_RESULT_OBJECT, null, listOf(0), "row_view",0, after=true, saves=mapOf(-1 to 0), before=a(Opcode.INVOKE_INTERFACE, "Latis;->lN()Landroid/view/View;", 4)),
        s(0x0041, Opcode.MOVE_RESULT_OBJECT, null, listOf(5), "row_prepare",-1,5,4,1, after=true, before=a(Opcode.INVOKE_VIRTUAL, "Latjd;->getItem(I)Ljava/lang/Object;", 3,5)),
        s(0x0054, Opcode.IGET_OBJECT, "Latjd;->a:Ljava/util/HashSet;", listOf(3,3), "row_bind",-1,5,4,1, before=a(Opcode.INVOKE_INTERFACE, "Latis;->pd(Latiq;Ljava/lang/Object;)V", 4,1,5))
    )),
    Target("classes2.dex", "Lastr;->k(Lastj;Liev;Lxcv;Latiq;Lbsot;)Lieq;", 13, listOf(
        s(0x0000, Opcode.IGET_OBJECT, "Lampz;->a:Lampe;", listOf(0,11), null, saves=mapOf(-1 to 11)),
        s(0x000f, Opcode.INVOKE_VIRTUAL_RANGE, "Lvwn;->b(Liev;Lxcv;Lastj;Lxds;Lbsot;)Lieq;", (1..6).toList(), "root_row",-1,4,2,3)
    )),
    // Actual app predicate, preserve its input before the parameter is reused.
    Target("classes.dex", "Lrrn;->C(Lakmn;)Z", 3, listOf(
        s(0x0000, Opcode.CONST_4, null, listOf(0), null, saves=mapOf(-1 to 2),
            following=a(Opcode.IF_EQZ,null,2),following2=a(Opcode.IGET_OBJECT,"Lakmn;->a:Lbggp;",2,2)),
        s(0x0005, Opcode.IGET, "Lbggp;->c:I",listOf(1,2),null,
            following=a(Opcode.AND_INT_LIT8,null,1,1)),
        s(0x000b, Opcode.IGET_OBJECT, "Lbggp;->i:Ljava/lang/String;",listOf(2,2),null,
            following=a(Opcode.CONST_STRING,"related-items",1))
    ),predicateReturns=true),
    Target("classes4.dex", "Lrrx;->d()V", 3, listOf(
        s(0x004e, Opcode.INVOKE_VIRTUAL, "Lrrx;->e(Ljava/lang/String;)V",listOf(2,0),"lookup_request",2,0,
            before=a(Opcode.CONST_STRING,"related-items",0))
    )),
    // Observe the existing Optional results and actual get() results, never call get() ourselves.
    Target("classes.dex", "Lptf;->a(Ljava/lang/Object;)V",11,listOf(
        s(0x0093,Opcode.MOVE_RESULT_OBJECT,null,listOf(1),"lookup_optional_first",10,1,10,-1,
            after=true,saves=mapOf(-1 to 0),before=a(Opcode.INVOKE_VIRTUAL,"Llpu;->lJ(Ljava/lang/String;)Lj$/util/Optional;",10,0),
            following=a(Opcode.IGET_OBJECT,"Lptf;->b:Ljava/lang/Object;",9,9)),
        s(0x0096,Opcode.CHECK_CAST,"Lrrx;",listOf(9),null,after=true,saves=mapOf(-2 to 9),
            following=a(Opcode.IGET_OBJECT,"Lrrx;->c:Ljava/util/function/Supplier;",3,9)),
        s(0x009d,Opcode.MOVE_RESULT,null,listOf(4),"lookup_empty_first",-2,1,10,-1,after=true,num=4,
            before=a(Opcode.INVOKE_VIRTUAL,"Lj$/util/Optional;->isEmpty()Z",1),
            following=a(Opcode.IF_NEZ,null,4)),
        s(0x00a3,Opcode.MOVE_RESULT_OBJECT,null,listOf(4),"lookup_result_first",-2,4,10,-1,after=true,
            before=a(Opcode.INVOKE_VIRTUAL,"Lj$/util/Optional;->get()Ljava/lang/Object;",1),
            following=a(Opcode.INSTANCE_OF,"Lrrn;",4,4)),
        s(0x00b9,Opcode.MOVE_RESULT_OBJECT,null,listOf(1),"lookup_result_confirmed",-2,1,10,-1,after=true,
            before=a(Opcode.INVOKE_VIRTUAL,"Lj$/util/Optional;->get()Ljava/lang/Object;",1),
            following=a(Opcode.CHECK_CAST,"Lrrn;",1)),
        s(0x00d1,Opcode.MOVE_RESULT_OBJECT,null,listOf(9),"lookup_optional_second",-2,9,10,-1,after=true,
            before=a(Opcode.INVOKE_VIRTUAL,"Llpu;->lJ(Ljava/lang/String;)Lj$/util/Optional;",10,0),
            following=a(Opcode.INVOKE_VIRTUAL,"Lj$/util/Optional;->isPresent()Z",9)),
        s(0x00d5,Opcode.MOVE_RESULT,null,listOf(0),"lookup_present_second",-2,9,10,-1,after=true,num=0,
            before=a(Opcode.INVOKE_VIRTUAL,"Lj$/util/Optional;->isPresent()Z",9),
            following=a(Opcode.IF_EQZ,null,0)),
        s(0x00db,Opcode.MOVE_RESULT_OBJECT,null,listOf(9),"lookup_result_second",-2,9,10,-1,after=true,
            before=a(Opcode.INVOKE_VIRTUAL,"Lj$/util/Optional;->get()Ljava/lang/Object;",9),
            following=a(Opcode.INVOKE_INTERFACE,"Latqo;->a()Lathm;",9))
    ))
)

private data class Resolution(val indices: Map<Int, Int>, val roles: Map<Int, Int>, val entry: Int)

private fun resolveSites(t: Target, instructions: List<Instruction>, count: Int, types: List<String>): Resolution {
    val (entry, params) = workingParameters(instructions, count, types, t.registers)
    var roles = (t.registers - types.size until t.registers).zip(params).toMap()
    val resolved = linkedMapOf<Int, Int>()
    for (site in t.sites) {
        val anchor = Anchor(site.opcode, site.reference, requireNotNull(site.operands))
        val candidates = if (site.offset == 0) listOf(entry) else instructions.indices.toList()
        val matches = candidates.mapNotNull { index ->
            val bound = roles.toMutableMap()
            fun match(relative: Int, pattern: Anchor?) = pattern == null ||
                instructions.getOrNull(index + relative)?.let { pattern.bind(it, bound) } == true
            if (match(0, anchor) && match(-1, site.before) && match(1, site.following) && match(2, site.following2))
                index to bound else null
        }
        require(matches.size == 1) {
            "Probe anchor not unique ${t.descriptor}:${site.offset.toString(16)} ${site.opcode} ${site.reference}: ${matches.size} matches"
        }
        val (index, bound) = matches.single()
        require(index > (resolved.values.lastOrNull() ?: -1)) { "Probe anchor order changed: ${t.descriptor}" }
        roles = bound
        resolved[site.offset] = index
    }
    // Never silently fall back to a raw APK register number for an unbound operand.
    for (site in t.sites) {
        val required = site.args + site.saves.values + listOfNotNull(site.number)
        require(required.filter { it >= 0 }.all { roles[it]?.let { live -> live in 0 until count } == true }) {
            "Unbound probe operand: ${t.descriptor}:${site.offset.toString(16)}"
        }
    }
    return Resolution(resolved, roles, entry)
}

private fun descriptor(m: MutableMethod) = "${m.definingClass}->${m.name}(${m.parameterTypes.joinToString("")})${m.returnType}"

private data class Checked(val target: Target, val method: MutableMethod, val resolution: Resolution)

private fun verifyTarget(t: Target): Checked {
    // Exact target version: use the standard fingerprint to locate the signature
    // and at least one semantic reference, then prove every local insertion site.
    val owner=t.descriptor.substringBefore("->")
    val name=t.descriptor.substringAfter("->").substringBefore("(")
    val raw=classDefBy(owner).methods.singleOrNull {
        "${it.definingClass}->${it.name}(${it.parameterTypes.joinToString("")})${it.returnType}"==t.descriptor
    } ?: throw IllegalArgumentException("Missing target signature ${t.descriptor}")
    val references=t.sites.mapNotNull { it.reference ?: it.before?.reference }.toSet() +
        if(owner=="Lastj;")setOf("Lastj;->c:[B") else emptySet()
    val fingerprint=Fingerprint(definingClass=owner,name=name,
        parameters=raw.parameterTypes.map { it.toString() },returnType=raw.returnType,
        filters=when {
            t.predicateReturns||name=="d"&&owner=="Lrrx;" -> listOf(string("related-items"))
            owner=="Lvpo;"&&name=="b" -> listOf(string("Failed to parse Element",StringComparisonType.STARTS_WITH))
            else -> null
        },
        custom={ method,_ -> method.implementation?.instructions?.any { ins ->
            (ins as? ReferenceInstruction)?.reference?.toString() in references
        }==true })
    fingerprint.clearMatch()
    val m=fingerprint.matchAll(1..1).single().method
    require(descriptor(m)==t.descriptor) { "Fingerprint signature changed: ${t.descriptor}" }
    val count = m.implementation!!.registerCount
    val instructions = m.implementation!!.instructions.toList()
    val resolution = resolveSites(t, instructions, count, parameterTypes(m))
    require(count + EXTRA <= 256) { "Probe scratch register limit: ${t.descriptor}" }
    println("Related probe layout: ${t.descriptor} raw=${t.registers} live=$count entry=${resolution.entry} roles=${resolution.roles}")
    val offsets = mutableListOf<Int>()
    var offset = 0
    instructions.forEach { offsets += offset; offset += it.codeUnits }
    for ((raw, index) in resolution.indices)
        println("Related probe anchor: ${t.descriptor} raw=${raw.toString(16)} live=${offsets[index].toString(16)} index=$index")
    return Checked(t,m,resolution)
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
    val copied = resolveSites(c.target, originalInstructions, count, parameterTypes(old))
    require(copied == c.resolution) { "Probe anchors changed when copying ${c.target.descriptor}" }
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
    fun live(role: Int) = if (role < 0) role else copied.roles.getValue(role)
    val injections = c.target.sites.map { site ->
        copied.indices.getValue(site.offset) to site.copy(
            args=site.args.map(::live), number=site.number?.let(::live),
            saves=site.saves.mapValues { live(it.value) })
    }.toMutableList()
    if(c.target.entryEvent!=null){
        // Parameter bank is live here, including verified official cloneParameters copies.
        val params=workingParameters(originalInstructions,count,parameterTypes(old),c.target.registers).second
        val args=when(c.target.entryEvent){
            "reach_watch_t" -> listOf(params[0],params[2])
            "reach_watch_s","reach_section_bind" -> listOf(params[0],params[1])
            else -> listOf(params[0])
        }
        injections += copied.entry to Site(-1,originalInstructions[copied.entry].opcode,event=c.target.entryEvent,args=args)
    }
    if(c.target.predicateReturns){
        originalInstructions.forEachIndexed { index,insn ->
            if(insn.opcode==Opcode.RETURN)
                injections += index to Site(-1,insn.opcode,event="predicate",args=listOf(-1),
                    number=(insn as OneRegisterInstruction).registerA)
        }
    }
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
    val types = parameterTypes(old)
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
    name = "Related items diagnostic probe",
    description = "Observation-only related-items probe. Select instead of View count filter. YouTube 21.30.216 only.",
    default = false,
) {
    compatibleWith(Compatibility(name="YouTube", packageName="com.google.android.youtube",
        apkFileType=ApkFileType.APK, appIconColor=0xFF0000,
        targets=listOf(AppTarget(version="21.30.216"))))
    extendWith("extensions/extension.mpe")
    execute {
        // Resolve everything before mutation. A missing/ambiguous anchor skips its
        // dependency group; errors during actual mutation still abort the build.
        fun group(t:Target)=when {
            t.predicateReturns -> "predicate"
            t.descriptor.startsWith("Lrrx;")||t.descriptor.startsWith("Lptf;") -> "lookup"
            t.descriptor.startsWith("Latjd;")||t.descriptor.startsWith("Lastr;") -> "rows"
            t.descriptor.startsWith("Lastj;")||t.descriptor.startsWith("Lvwn;")||
                t.descriptor.startsWith("Lvpo;")||t.descriptor.startsWith("Lvpn;") -> "elements"
            else -> "upstream"
        }
        val checked=mutableListOf<Checked>()
        val coverage=mutableListOf<Map<String,Any>>()
        for((key,members) in targets.groupBy(::group)){
            val resolved=mutableListOf<Checked>()
            var reason:String?=null
            for(t in members){
                try{resolved+=verifyTarget(t)}
                catch(e:IllegalArgumentException){reason=e.message?:"anchor_validation"}
                catch(e:PatchException){reason=e.message?:"fingerprint_validation"}
                if(reason!=null)break
            }
            if(reason==null)checked+=resolved
            for(t in members) coverage+=mapOf("group" to key,"method" to t.descriptor,
                "sites" to t.sites.map { it.offset.toString(16)+":"+(it.event?:if(it.saves.isEmpty())"check" else "save") }+
                    listOfNotNull(t.entryEvent,if(t.returns)"element_exit" else null,if(t.predicateReturns)"predicate_returns" else null),
                "status" to if(reason==null)"installed" else "skipped",
                "reason" to (reason?:"verified"))
            println("Related probe coverage: $key ${if(reason==null)"installed" else "skipped: $reason"}")
        }
        require(checked.isNotEmpty()) { "No safe diagnostic groups" }
        val settings = mutableClassDefBy("Lgek;").methods.single {
            descriptor(it) == "Lgek;->u(Landroidx/preference/PreferenceScreen;)V"
        }
        val settingsCode = settings.implementation!!
        val settingsInstructions = settingsCode.instructions.toList()
        val (settingsEntry, settingsParams) = workingParameters(settingsInstructions, settingsCode.registerCount, parameterTypes(settings), 4)
        val (receiver, screen) = settingsParams
        // Original entry checks the screen and then overwrites the scratch local.
        val first = settingsInstructions.getOrNull(settingsEntry)
        val second = settingsInstructions.getOrNull(settingsEntry + 1)
        require(first?.opcode == Opcode.IF_EQZ && first.registersUsed == listOf(screen) &&
            second?.opcode == Opcode.IGET_OBJECT &&
            (second as? ReferenceInstruction)?.reference.toString() == "Lgek;->a:Lges;" &&
            second.registersUsed.size == 2 && second.registersUsed[1] == receiver)
        val scratch = second.registersUsed[0]
        require(scratch !in settingsParams && scratch < 16 && receiver < 16)
        checked.forEach(::instrument)
        val coverageMethod=mutableClassDefBy(PROBE).methods.single {
            it.name=="coverageManifest" && it.parameterTypes.isEmpty() && it.returnType=="Ljava/lang/String;"
        }
        // No external serialization dependency in the Manager patch classloader.
        fun quote(s:String)="\""+s.replace("\\","\\\\").replace("\"","\\\"")
            .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\""
        val json=coverage.joinToString(",","[","]"){ row ->
            row.entries.joinToString(",","{","}"){(k,v)->
                quote(k)+":"+if(v is List<*>)v.joinToString(",","[","]"){quote(it.toString())}
                    else quote(v.toString())
            }
        }
        coverageMethod.addInstructions(0,"const-string v0, "+quote(json)+"\nreturn-object v0")
        // Native settings fallback, identical gate to the existing patch.
        app.morphe.patcher.extensions.InstructionExtensions.run {
            settings.addInstructionsWithLabels(settingsEntry, """
                instance-of v$scratch, v$receiver, Lpty;
                if-eqz v$scratch, :probe_no_settings
                invoke-static/range {v$screen .. v$screen}, $ENTRY->install(Ljava/lang/Object;)V
                :probe_no_settings
                nop
            """.trimIndent())
        }
        val pkg = resourceXml("AndroidManifest.xml").use { it.root.attribute("package") ?: error("Missing package") }
        resourceFile("morphe_addon_prefs.xml").writeText("""
            <morphe-add-on-preferences xmlns:android="http://schemas.android.com/apk/res/android">
                <screen>
                    <Preference android:key="rikunode_relatedprobe_settings"
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

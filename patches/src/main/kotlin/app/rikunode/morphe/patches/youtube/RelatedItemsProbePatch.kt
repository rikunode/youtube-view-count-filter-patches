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
import com.android.tools.smali.dexlib2.iface.MethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
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
                          val entryEvent: String? = null, val predicateReturns: Boolean = false, val exactHash: String? = null)
private fun s(off: Int, op: Opcode, ref: String?, regs: List<Int>, event: String?,
              vararg args: Int, after: Boolean = false, num: Int? = null,
              saves: Map<Int, Int> = emptyMap(), before: Anchor? = null, following: Anchor? = null, following2: Anchor? = null) =
    Site(off, op, ref, regs, after, event, args.toList(), num, saves, before, following, following2)

private val baseTargets = listOf(
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

// Generated from pinned stock APK; see verification/v0115-anchors.txt.
private val additionalTargets = listOf(
Target("classes4.dex", "Lrqa;->o(Lrpd;I)V", 7, listOf(
        s(0x0000, Opcode.IGET_OBJECT, "Lrqa;->y:Lrpf;", listOf(0, 4), null, saves=mapOf(-1 to 5)),
        s(0x00a5, Opcode.INVOKE_DIRECT, "Lrqa;->t(Landroid/os/Bundle;Lakmv;Lamqu;)V", listOf(4, 1, 0, 2), "source_g",4,-1,0),
        s(0x00b5, Opcode.INVOKE_DIRECT, "Lrqa;->t(Landroid/os/Bundle;Lakmv;Lamqu;)V", listOf(4, 6, 2, 5), "source_d",4,-1,2),
        s(0x00c3, Opcode.INVOKE_DIRECT, "Lrqa;->s(Lakmv;)V", listOf(4, 6), "source_cached",4,-1,6),
        s(0x00f3, Opcode.INVOKE_DIRECT, "Lrqa;->s(Lakmv;)V", listOf(4, 5), "source_text",4,-1,5),
        s(0x00fd, Opcode.INVOKE_DIRECT, "Lrqa;->s(Lakmv;)V", listOf(4, 5), "source_empty",4,-1,5)
    ), exactHash="258c1992c4494c23975032101fddd0f21b2ccf50ce93ee090cc2339009c545e8"),
    Target("classes2.dex", "Lasmv;->c(Lslk;Lj\$/util/Optional;ZZ)V", 11, listOf(
        s(0x0196, Opcode.INVOKE_VIRTUAL, "Lslk;->b(Lbkfy;)V", listOf(7, 6), "ghost_unlabelled",6,7),
        s(0x029e, Opcode.INVOKE_VIRTUAL, "Lslk;->b(Lbkfy;)V", listOf(7, 8), "ghost_metadata",8,7),
        s(0x042f, Opcode.INVOKE_VIRTUAL, "Lslk;->b(Lbkfy;)V", listOf(7, 8), "ghost_slim",8,7)
    ), exactHash="3cbb1bc07141d1ef3c717fe6eba5a2257419a902ae2bd0eb9cfc8a69faa972b2"),
    Target("classes2.dex", "Lasmv;->f()Lbkfy;", 7, listOf(
        s(0x00fc, Opcode.RETURN_OBJECT, null, listOf(6), "ghost_shelf",6)
    ), exactHash="5191eb15fa1173c874cbd935630ac9569464b7396c3680b328fa48f8aacb71c6"),
    Target("classes2.dex", "Lasmv;->a(Lbkfr;)Lakmv;", 4, listOf(
        s(0x0000, Opcode.SGET_OBJECT, "Lbfxf;->a:Lbfxf;", listOf(0), null, saves=mapOf(-1 to 3)),
        s(0x007d, Opcode.RETURN_OBJECT, null, listOf(0), "ghost_wrap",-1,0)
    ), exactHash="7578e32082eccbc74c41b96869b498100cbbfe143a9adf65bb5ca82955f6cf69"),
    Target("classes2.dex", "Lasmv;->b(Ljava/lang/String;Z)Lakmv;", 10, listOf(
        s(0x0087, Opcode.RETURN_OBJECT, null, listOf(7), "ghost_text_result",7)
    ), exactHash="ca9f8c953430f42b3fab5f318fdc499998a6234da50af9adca71bda1545004ed"),
    Target("classes.dex", "Lrpd;->m(Z)Lakmv;", 9, listOf(
        s(0x0000, Opcode.INVOKE_VIRTUAL, "Lrpd;->c()Lakmv;", listOf(7), null, saves=mapOf(-1 to 7)),
        s(0x00ca, Opcode.RETURN_OBJECT, null, listOf(7), "ghost_cached_result",-1,7)
    ), exactHash="c396a1951c3074e3cfc4fdb0f6b3929b71f0f72bfb3723e1dd681a944111abfb"),
    Target("classes2.dex", "Lakmq;->c(Lbkfy;)Lj\$/util/Optional;", 5, listOf(
        s(0x0000, Opcode.IGET, "Lbkfy;->b:I", listOf(0, 4), null, saves=mapOf(-1 to 4)),
        s(0x0011, Opcode.INVOKE_STATIC, "Lj\$/util/Optional;->of(Ljava/lang/Object;)Lj\$/util/Optional;", listOf(0), null, saves=mapOf(-2 to 0)),
        s(0x0015, Opcode.RETURN_OBJECT, null, listOf(4), "select_section",-1,-2,4),
        s(0x00bd, Opcode.INVOKE_STATIC, "Lj\$/util/Optional;->of(Ljava/lang/Object;)Lj\$/util/Optional;", listOf(4), null, saves=mapOf(-2 to 4)),
        s(0x00c1, Opcode.RETURN_OBJECT, null, listOf(4), "select_shelf",-1,-2,4),
        s(0x00c7, Opcode.INVOKE_STATIC, "Lj\$/util/Optional;->of(Ljava/lang/Object;)Lj\$/util/Optional;", listOf(0), null, saves=mapOf(-2 to 0)),
        s(0x00cb, Opcode.RETURN_OBJECT, null, listOf(4), "select_bero",-1,-2,4),
        s(0x00d1, Opcode.INVOKE_STATIC, "Lj\$/util/Optional;->of(Ljava/lang/Object;)Lj\$/util/Optional;", listOf(0), null, saves=mapOf(-2 to 0)),
        s(0x00d5, Opcode.RETURN_OBJECT, null, listOf(4), "select_other",-1,-2,4),
        s(0x00dc, Opcode.INVOKE_STATIC, "Lj\$/util/Optional;->of(Ljava/lang/Object;)Lj\$/util/Optional;", listOf(4), null, saves=mapOf(-2 to 4)),
        s(0x00e0, Opcode.RETURN_OBJECT, null, listOf(4), "select_direct",-1,-2,4),
        s(0x00e5, Opcode.RETURN_OBJECT, null, listOf(4), "select_empty",-1,4)
    ), exactHash="9ff9f448f3240970c40907053cfe08b6fa575737e2f5c7d11c815874ea5cbc65"),
    Target("classes2.dex", "Lakmq;->b()Laxrs;", 4, listOf(
        s(0x0025, Opcode.RETURN_OBJECT, null, listOf(0), "model_list",3,0)
    ), exactHash="0de948553e3c4ce308dd6f5df197348d27070a59e40886728e45efcc0b4d73b7"),
    Target("classes2.dex", "Latnz;->be(Lakmq;ILasdg;ZZZ)V", 13, listOf(
        s(0x0000, Opcode.IF_NEZ, null, listOf(7), "host_scope_begin",6,7,9, num=8),
        s(0x0002, Opcode.RETURN_VOID, null, listOf(), "host_scope_noop",6,7),
        s(0x0078, Opcode.INVOKE_DIRECT, "Latnz;->s(Ljava/util/List;Ljava/util/List;ZZ)V", listOf(6, 8, 11, 10, 12), "host_models",6,7,8),
        s(0x0083, Opcode.RETURN_VOID, null, listOf(), "host_scope_end",6,7)
    ), exactHash="811f2148c3e7edca89467f3e422008eb52a16c1fdb9c32b758833c1641d5eb7e"),
    Target("classes2.dex", "Latnz;->F(Ljava/lang/Object;Latsx;IZI)Latie;", 11, listOf(
        s(0x0002, Opcode.INVOKE_INTERFACE, "Latqp;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", listOf(0, 6, 7, 5), "host_f_enter",5,6,0,7, saves=mapOf(-1 to 5,-2 to 6)),
        s(0x0005, Opcode.MOVE_RESULT_OBJECT, null, listOf(7), "host_f_result",-1,-2,7, after=true),
        s(0x0009, Opcode.RETURN_OBJECT, null, listOf(5), "host_f_null",-1,-2,5),
        s(0x002e, Opcode.INVOKE_VIRTUAL, "Latif;->l(ILathm;Z)Latie;", listOf(6, 8, 10, 9), "host_f_attach_x",-1,-2,7,10,6, saves=mapOf(-4 to 10,-5 to 6)),
        s(0x0057, Opcode.INVOKE_VIRTUAL, "Latif;->l(ILathm;Z)Latie;", listOf(0, 8, 6, 9), "host_f_attach_w",-1,-2,7,6,0, saves=mapOf(-4 to 6,-5 to 0)),
        s(0x006e, Opcode.RETURN_OBJECT, null, listOf(6), "host_f_commit",-1,-2,7,-4,-5,6)
    ), exactHash="29c3ed5452fc568c707beaca5f2ccaeeac8701ac878262131fd5dac1fe310746"),
    Target("classes2.dex", "Latnz;->s(Ljava/util/List;Ljava/util/List;ZZ)V", 18, listOf(
        s(0x0000, Opcode.CONST_4, null, listOf(1), "host_s_begin",13,14,15),
        s(0x00a8, Opcode.INVOKE_INTERFACE, "Latqp;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", listOf(2, 1, 9, 13), "host_s_convert",13,1,2,9, saves=mapOf(-1 to 1,-2 to 2)),
        s(0x00ab, Opcode.MOVE_RESULT_OBJECT, null, listOf(2), "host_s_result",13,-1,2,-2, after=true, saves=mapOf(-3 to 2)),
        s(0x00c8, Opcode.INVOKE_VIRTUAL, "Latif;->l(ILathm;Z)Latie;", listOf(1, 7, 2, 6), "host_s_nested",13,-1,-3,3,2,1),
        s(0x00cb, Opcode.GOTO, null, listOf(), "host_s_nested_done",13,-1,-3,3,2,1),
        s(0x00d0, Opcode.INVOKE_DIRECT, "Latnz;->aY(Latqo;Ljava/lang/Object;Latqo;I)V", listOf(13, 3, 1, 2, 11), "host_s_replace",13,3,1,2, num=11),
        s(0x0140, Opcode.RETURN_VOID, null, listOf(), "host_s_end",13)
    ), exactHash="69b19e8ae4a4e5e97c35420363b27cffb287a00ecad2901a0f2af9bd54180d84"),
    Target("classes2.dex", "Latnz;->ah(ILbkfy;)V", 7, listOf(
        s(0x0000, Opcode.IF_LTZ, null, listOf(5), null, saves=mapOf(-1 to 6)),
        s(0x000e, Opcode.MOVE_RESULT_OBJECT, null, listOf(6), "host_ah_optional",4,-1,6, after=true, num=5),
        s(0x001a, Opcode.MOVE_RESULT_OBJECT, null, listOf(2), "host_ah_get",4,-1,6,2, after=true, num=5),
        s(0x001c, Opcode.INVOKE_INTERFACE, "Latqp;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", listOf(1, 2, 3, 4), "host_ah_enter",4,2,1,-1, saves=mapOf(-2 to 2)),
        s(0x001f, Opcode.MOVE_RESULT_OBJECT, null, listOf(1), "host_ah_result",4,-2,1,-1, after=true),
        s(0x002c, Opcode.INVOKE_DIRECT, "Latnz;->aY(Latqo;Ljava/lang/Object;Latqo;I)V", listOf(4, 0, 6, 1, 5), "host_ah_replace",4,0,6,1,-1, num=5),
        s(0x002f, Opcode.RETURN_VOID, null, listOf(), "host_ah_end",4,-1)
    ), exactHash="566016f11cc6a07de3ca81fdbe4cc3a7802093bab77927736a1d20d283019a12"),
    Target("classes2.dex", "Latnz;->aY(Latqo;Ljava/lang/Object;Latqo;I)V", 8, listOf(
        s(0x0000, Opcode.INVOKE_STATIC, "Latnz;->ax(Latqo;)Ljava/lang/String;", listOf(4), "host_replace_begin",3,4,5,6, saves=mapOf(-1 to 3,-2 to 4,-3 to 5,-4 to 6), num=7),
        s(0x0017, Opcode.INVOKE_INTERFACE, "Ljava/util/List;->set(ILjava/lang/Object;)Ljava/lang/Object;", listOf(5, 7, 6), "host_replace_lists",-1,-2,-3,-4, after=true, num=7),
        s(0x004c, Opcode.INVOKE_VIRTUAL, "Latif;->r(Lathm;Lathm;Z)V", listOf(5, 4, 7, 0), "host_replace_attach_x",-1,-2,-4,4,7,5),
        s(0x005d, Opcode.INVOKE_VIRTUAL, "Latif;->r(Lathm;Lathm;Z)V", listOf(5, 4, 7, 0), "host_replace_attach_w",-1,-2,-4,4,7,5),
        s(0x0073, Opcode.RETURN_VOID, null, listOf(), "host_replace_commit",-1,-2,-3,-4)
    ), exactHash="c34522441108eef317f3fa9f98e0655c56d4b48110985e81e16674c5921c60dd"),
    Target("classes4.dex", "Lrsh;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", 21, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(0, 17), null, saves=mapOf(-1 to 18,-2 to 20)),
        s(0x0080, Opcode.RETURN_OBJECT, null, listOf(0), "direct_blaz",-2,-1,0),
        s(0x0125, Opcode.RETURN_OBJECT, null, listOf(0), "direct_bero",-2,-1,0)
    ), exactHash="e27a3419b4d3118441a2e812421b9511960a97533653c05e16996061fe42cc6b"),
    Target("classes.dex", "Lrdo;->g(Lakmv;)V", 4, listOf(
        s(0x0000, Opcode.INVOKE_DIRECT, "Lrdo;->o()V", listOf(2), null, saves=mapOf(-1 to 3)),
        s(0x0010, Opcode.INVOKE_VIRTUAL, "Latnz;->ay(Lakmq;)V", listOf(0, 1), "aux_host",2,-1,0,1)
    ), exactHash="b17f41367d6c03f3abea65ac6411dae76d9d5b5f448b285c4566ecefc1eacf11"),
    Target("classes.dex", "Lrdo;->h(Lakmv;Z)V", 5, listOf(
        s(0x0000, Opcode.INVOKE_DIRECT, "Lrdo;->o()V", listOf(2), null, saves=mapOf(-1 to 3)),
        s(0x0011, Opcode.INVOKE_VIRTUAL, "Latnz;->ao(Lakmq;Lamqu;Landroid/os/Bundle;Z)V", listOf(0, 1, 3, 3, 4), "aux_host",2,-1,0,1)
    ), exactHash="c398bbca156df0938433f49589d8b445ae2a18980eac38b22f7a11b4455d5ad7"),
    Target("classes4.dex", "Lrdl;->g(Lakmv;)V", 4, listOf(
        s(0x0000, Opcode.INVOKE_DIRECT, "Lrdl;->n()V", listOf(2), null, saves=mapOf(-1 to 3)),
        s(0x0010, Opcode.INVOKE_VIRTUAL, "Latnz;->ay(Lakmq;)V", listOf(0, 1), "aux_host",2,-1,0,1)
    ), exactHash="de92643b5f75d8deef05d027cae28d7f567f8bc01bf1441c2485ae032e920e04"),
    Target("classes4.dex", "Lrdl;->h(Lakmv;Z)V", 5, listOf(
        s(0x0000, Opcode.INVOKE_DIRECT, "Lrdl;->n()V", listOf(2), null, saves=mapOf(-1 to 3)),
        s(0x0011, Opcode.INVOKE_VIRTUAL, "Latnz;->ao(Lakmq;Lamqu;Landroid/os/Bundle;Z)V", listOf(0, 1, 3, 3, 4), "aux_host",2,-1,0,1)
    ), exactHash="90c387b7ffe84caa87efcf757c7c4b1e465a00371450e3b63d71585d9b4590b1"),
    Target("classes4.dex", "Lrrh;-><init>(Latqn;Laeyc;Lbkof;Lbero;Latqa;Latsl;Laxmb;Latsx;Latog;Lasur;Lazrt;Lauai;Lyba;)V", 31, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(0, 21), null, saves=mapOf(-1 to 20,-2 to 21)),
        s(0x0040, Opcode.RETURN_VOID, null, listOf(), "bridge_bero",0,-1,-2,4)
    ), exactHash="bb9b405fb4488fdb93340dbf43f001f6fbb9f929d1460adb8c8a5055fdfca16b"),
    Target("classes4.dex", "Lrrl;-><init>(Latqn;Laeyc;Lbkof;Ljava/util/List;ILatqa;Laxmb;Ljava/lang/CharSequence;Lbcje;Latsl;Latsx;Latog;Lasur;Lazrt;Lauai;Lyba;)V", 33, listOf(
        s(0x0035, Opcode.INVOKE_DIRECT_RANGE, "Latoi;-><init>(Latqn;Laeyc;Lbkof;Ljava/util/List;ILatqa;Lauai;Laxmb;Latpx;Latsl;Lasur;Latsx;Latog;Lazrt;Lyba;)V", listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15), null, saves=mapOf(-1 to 3,-2 to 4)),
        s(0x0135, Opcode.RETURN_VOID, null, listOf(), "bridge_rrl",0,-1,-2)
    ), exactHash="9411f5a6f828eda4a52e24fd6296651af5b1fd395c579c880c26fdc03e929fa0"),
    Target("classes2.dex", "Latoi;-><init>(Latqn;Laeyc;Lbkof;Ljava/util/List;ILatqa;Lauai;Laxmb;Latpx;Latsl;Lasur;Latsx;Latog;Lazrt;Lyba;)V", 40, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(0, 24), null, saves=mapOf(-1 to 27,-2 to 28)),
        s(0x0111, Opcode.IPUT_OBJECT, "Latoi;->n:Ljava/util/List;", listOf(3, 0), "bridge_atoi_cached",0,-1,-2,3, after=true),
        s(0x0126, Opcode.IPUT_OBJECT, "Latoi;->n:Ljava/util/List;", listOf(3, 0), "bridge_atoi_converted",0,-1,-2,3, after=true)
    ), exactHash="05157a7bbc7cc9eaba28f2f3bd149e50a29416b442c01179d193ded033e015ee"),
    Target("classes4.dex", "Lrrz;->f()V", 5, listOf(
        s(0x0006, Opcode.INVOKE_VIRTUAL, "Laurc;->g(Ljava/util/List;)Ljava/util/List;", listOf(0, 2), null, saves=mapOf(-1 to 1,-2 to 2)),
        s(0x000c, Opcode.INVOKE_VIRTUAL, "Laexw;->addAll(Ljava/util/Collection;)Z", listOf(2, 0), "bridge_blaz",4,-1,-2,0,2, after=true)
    ), exactHash="7c3c7489e4566ec3d349afd7c34439052847ae51ef7a7d11f26ca73c8b33dcd1"),
    Target("classes2.dex", "Laurc;->g(Ljava/util/List;)Ljava/util/List;", 6, listOf(
        s(0x0000, Opcode.INVOKE_VIRTUAL, "Ljava/lang/Object;->getClass()Ljava/lang/Class;", listOf(5), null, saves=mapOf(-1 to 4,-2 to 5)),
        s(0x0044, Opcode.INVOKE_DIRECT, "Latsi;-><init>(ILjava/util/List;Ljava/util/List;)V", listOf(3, 2, 4, 5), "bridge_callable",3,4,5, after=true),
        s(0x0059, Opcode.RETURN_OBJECT, null, listOf(4), "bridge_list_empty",-1,-2,4),
        s(0x0081, Opcode.RETURN_OBJECT, null, listOf(4), "bridge_list_result",-1,-2,4)
    ), exactHash="ae554545b08546d595742a588a5b2aec7a763d6373b1c424c1e7721e48b7414b"),
    Target("classes2.dex", "Latsi;->call()Ljava/lang/Object;", 7, listOf(
        s(0x0000, Opcode.NEW_INSTANCE, "Ljava/util/ArrayList;", listOf(0), null, saves=mapOf(-1 to 6)),
        s(0x001d, Opcode.INVOKE_INTERFACE, "Latsl;->b(Ljava/lang/Object;Latsk;)V", listOf(5, 4, 2), "bridge_child_enter",6,4,5,2),
        s(0x0020, Opcode.ADD_INT_LIT8, null, listOf(3, 3), "bridge_child_done",6,4,5,2),
        s(0x0023, Opcode.NEW_INSTANCE, "Lbtjx;", listOf(6), null, saves=mapOf(-2 to 0)),
        s(0x002a, Opcode.RETURN_OBJECT, null, listOf(6), "bridge_callable_result",-1,-2,6)
    ), exactHash="8f5f6d026acf76c1db8165dd3ba27a483905a84b27b92b9b7472af8dc82ad7dd"),
    Target("classes2.dex", "Latsh;->a(Ljava/lang/Object;)V", 3, listOf(
        s(0x0000, Opcode.IGET, "Latsh;->b:I", listOf(0, 1), null, saves=mapOf(-1 to 1)),
        s(0x0008, Opcode.INVOKE_VIRTUAL, "Ljava/util/AbstractList;->add(Ljava/lang/Object;)Z", listOf(1, 2), "bridge_sink_abstract",-1,1,2, after=true),
        s(0x000c, Opcode.INVOKE_INTERFACE, "Ljava/util/List;->add(Ljava/lang/Object;)Z", listOf(1, 2), "bridge_sink_list",-1,1,2, after=true)
    ), exactHash="e01979e75f6c841b7aaccebecfd3df1538e914fcb1df0d0e7ce1daed6303975a"),
    Target("classes.dex", "Lqdr;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", 32, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(0, 28), null, saves=mapOf(-1 to 31)),
        s(0x0b39, Opcode.RETURN_OBJECT, null, listOf(3), "direct_bdsp",-1,1,3)
    ), exactHash="16c9cd78c519b56680919a88b128866c4e00cf256fbc04a1f471c0d8d81cad3a"),
    Target("classes6.dex", "Lauax;-><init>(Latqn;Lasur;Lbdsp;)V", 5, listOf(
        s(0x0000, Opcode.INVOKE_DIRECT, "Latod;-><init>()V", listOf(1), null, saves=mapOf(-1 to 4)),
        s(0x0003, Opcode.CONST_CLASS, "Lbdsp;", listOf(0), null, saves=mapOf(-2 to 1)),
        s(0x001b, Opcode.INVOKE_VIRTUAL, "Laurc;->g(Ljava/util/List;)Ljava/util/List;", listOf(4, 2), null, saves=mapOf(-3 to 2)),
        s(0x001e, Opcode.MOVE_RESULT_OBJECT, null, listOf(1), null, after=true, saves=mapOf(-4 to 1)),
        s(0x0024, Opcode.INVOKE_VIRTUAL, "Latjj;->add(Ljava/lang/Object;)Z", listOf(3, 1), "bridge_bdsp",-2,-1,-3,-4,3,1, after=true)
    ), exactHash="ef165d5689f5eb806aafbb505ae3882ddf76209e98ac9c1856789ffe15964008"),
)
private val officialVariants = listOf(
Target("classes10.dex", "Latnz;->s(Ljava/util/List;Ljava/util/List;ZZ)V", 18, listOf(
        s(0x0000, Opcode.CONST_4, null, listOf(1), "host_s_begin",13,14,15),
        s(0x00a8, Opcode.INVOKE_INTERFACE, "Latqp;->a(Ljava/lang/Object;Latsx;Latsp;)Latqo;", listOf(2, 1, 9, 13), "host_s_convert",13,1,2,9, saves=mapOf(-1 to 1,-2 to 2)),
        s(0x00ab, Opcode.MOVE_RESULT_OBJECT, null, listOf(2), "host_s_result",13,-1,2,-2, after=true, saves=mapOf(-3 to 2)),
        s(0x00b6, Opcode.RETURN_VOID, null, listOf(), "host_s_aborted",13),
        s(0x00d0, Opcode.INVOKE_VIRTUAL, "Latif;->l(ILathm;Z)Latie;", listOf(1, 7, 2, 6), "host_s_nested",13,-1,-3,3,2,1),
        s(0x00d3, Opcode.GOTO, null, listOf(), "host_s_nested_done",13,-1,-3,3,2,1),
        s(0x00d8, Opcode.INVOKE_DIRECT, "Latnz;->aY(Latqo;Ljava/lang/Object;Latqo;I)V", listOf(13, 3, 1, 2, 11), "host_s_replace",13,3,1,2, num=11),
        s(0x0148, Opcode.RETURN_VOID, null, listOf(), "host_s_end",13)
    ), exactHash="a7d5c8ea8fb9f28c18b95685fcac5fdc19d4afc3190ddcb06487820d641a8740"),
    Target("classes10.dex", "Latoi;-><init>(Latqn;Laeyc;Lbkof;Ljava/util/List;ILatqa;Lauai;Laxmb;Latpx;Latsl;Lasur;Latsx;Latog;Lazrt;Lyba;)V", 40, listOf(
        s(0x0000, Opcode.MOVE_OBJECT_FROM16, null, listOf(0, 24), null, saves=mapOf(-1 to 27,-2 to 28)),
        s(0x0114, Opcode.IPUT_OBJECT, "Latoi;->n:Ljava/util/List;", listOf(3, 0), "bridge_atoi_cached",0,-1,-2,3, after=true),
        s(0x0129, Opcode.IPUT_OBJECT, "Latoi;->n:Ljava/util/List;", listOf(3, 0), "bridge_atoi_converted",0,-1,-2,3, after=true)
    ), exactHash="0838c617d639ac957a530ef0ad15e5476cfee44c73991e9a43444406d177feb1"),
)
private val targets = baseTargets.map { base ->
    val extra = additionalTargets.singleOrNull { it.descriptor == base.descriptor }
    if (extra == null) base else base.copy(sites=(base.sites+extra.sites).sortedBy { it.offset },exactHash=extra.exactHash)
} + additionalTargets.filter { extra -> baseTargets.none { it.descriptor == extra.descriptor } }

private fun exactRegs(i:Instruction):List<Int> = when(i){
    is RegisterRangeInstruction -> (i.startRegister until i.startRegister+i.registerCount).toList()
    is FiveRegisterInstruction -> listOf(i.registerC,i.registerD,i.registerE,i.registerF,i.registerG).take(i.registerCount)
    is ThreeRegisterInstruction -> listOf(i.registerA,i.registerB,i.registerC)
    is TwoRegisterInstruction -> listOf(i.registerA,i.registerB)
    is OneRegisterInstruction -> listOf(i.registerA)
    else -> emptyList()
}
private fun exactShape(m:MethodImplementation):String {
    val body=buildString {
        append(m.registerCount).append('|')
        for(i in m.instructions){
            // Java generator uses Enum.name(), not dexlib2's lowercase Opcode.name field.
            append(i.opcode.toString()).append(':').append(i.codeUnits).append(':').append(exactRegs(i))
            if(i is ReferenceInstruction)append(":ref=").append(i.reference)
            if(i is DualReferenceInstruction)append(":ref2=").append(i.reference2)
            if(i is WideLiteralInstruction)append(":lit=").append(i.wideLiteral)
            if(i is OffsetInstruction)append(":off=").append(i.codeOffset)
            if(i is SwitchPayload)for(e in i.switchElements)append(":switch=").append(e.key).append(',').append(e.offset)
            if(i is ArrayPayload)append(":array=").append(i.elementWidth).append(',').append(i.arrayElements)
            append('\n')
        }
        for(t in m.tryBlocks){
            append("try:").append(t.startCodeAddress).append(',').append(t.codeUnitCount)
            for(h in t.exceptionHandlers)append(':').append(h.exceptionType).append(',').append(h.handlerCodeAddress)
            append('\n')
        }
    }
    return MessageDigest.getInstance("SHA-256").digest(body.toByteArray(StandardCharsets.UTF_8)).joinToString(""){"%02x".format(it)}
}

private data class Resolution(val indices: Map<Int, Int>, val roles: Map<Int, Int>, val entry: Int)

private fun resolveSites(t: Target, instructions: List<Instruction>, count: Int, types: List<String>): Resolution {
    if(t.exactHash!=null){
        require(count==t.registers) { "Exact register layout changed: ${t.descriptor}" }
        val indices=linkedMapOf<Int,Int>()
        var offset=0
        val byOffset=mutableMapOf<Int,Int>()
        instructions.forEachIndexed { i,ins -> byOffset[offset]=i; offset+=ins.codeUnits }
        for(site in t.sites){
            val index=requireNotNull(byOffset[site.offset]) { "Missing exact boundary ${t.descriptor}:${site.offset}" }
            val ins=instructions[index]
            require(ins.opcode==site.opcode && (site.reference==null||(ins as? ReferenceInstruction)?.reference.toString()==site.reference)
                && exactRegs(ins)==site.operands) { "Exact boundary mismatch ${t.descriptor}:${site.offset}" }
            require((site.args+site.saves.values+listOfNotNull(site.number)).all { it in -5 until count }) { "Exact operand range" }
            indices[site.offset]=index
        }
        return Resolution(indices,(0 until count).associateWith{it},0)
    }
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

private fun verifyTarget(requested: Target): Checked {
    var t=requested
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
        custom={ method,_ -> t.exactHash!=null || method.implementation?.instructions?.any { ins ->
            (ins as? ReferenceInstruction)?.reference?.toString() in references
        }==true })
    fingerprint.clearMatch()
    val m=fingerprint.matchAll(1..1).single().method
    require(descriptor(m)==t.descriptor) { "Fingerprint signature changed: ${t.descriptor}" }
    if(t.exactHash!=null){
        val hash=exactShape(m.implementation!!)
        if(hash!=t.exactHash){
            t=officialVariants.singleOrNull { it.descriptor==requested.descriptor && it.exactHash==hash }
                ?: throw IllegalArgumentException("Unverified exact body ${t.descriptor}: $hash")
            println("Related probe known official variant: ${t.descriptor}")
        }
    }
    val count = m.implementation!!.registerCount
    val instructions = m.implementation!!.instructions.toList()
    val resolution = resolveSites(t, instructions, count, parameterTypes(m))
    require(count + maxOf(EXTRA,parameterTypes(m).size+13) <= 256) { "Probe scratch register limit: ${t.descriptor}" }
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
    val extra=maxOf(EXTRA,parameterTypes(old).size+13)
    if(c.target.exactHash!=null) require(exactShape(original)==c.target.exactHash)
    val expanded = ImmutableMethodImplementation(count + extra, original.instructions, original.tryBlocks, original.debugItems)
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
    require(base + 7 < firstParam + extra)
    m.addInstructions(0, types.mapIndexed { i, type ->
        val op = if (type.startsWith("L") || type.startsWith("[")) "move-object/from16" else "move/from16"
        "$op v${firstParam+i}, v${firstParam+i+extra}"
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
            t.descriptor.startsWith("Lasmv;")||t.descriptor.startsWith("Lrpd;->m(")||t.descriptor.startsWith("Lrqa;->o(") -> "source"
            t.descriptor.startsWith("Lakmq;")||t.descriptor.startsWith("Latnz;")||t.descriptor.startsWith("Lrsh;")||
                t.descriptor.startsWith("Lrdo;")||t.descriptor.startsWith("Lrdl;") -> "host"
            t.descriptor.substringBefore("->") in setOf("Lrrh;","Lrrl;","Latoi;","Lrrz;","Laurc;","Latsi;","Latsh;","Lqdr;","Lauax;") -> "bridge"
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
            for(member in members) {
                val t=resolved.firstOrNull { it.target.descriptor==member.descriptor }?.target?:member
                coverage+=mapOf("group" to key,"method" to t.descriptor,"required" to (key in setOf("source","host","bridge","upstream","rows","elements")).toString(),
                "sites" to t.sites.map { it.offset.toString(16)+":"+(it.event?:if(it.saves.isEmpty())"check" else "save") }+
                    listOfNotNull(t.entryEvent,if(t.returns)"element_exit" else null,if(t.predicateReturns)"predicate_returns" else null),
                "status" to if(reason==null)"installed" else "skipped",
                "reason" to (reason?:"verified"))
            }
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

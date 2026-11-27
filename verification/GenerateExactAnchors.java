// Generates the pinned v21.30.216 exact-body anchor descriptions from a private stock APK.
// Run with the pinned Desktop/Patcher JAR on the classpath; never distributes the APK.
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
class GenerateExactAnchors {
 static List<Integer> regs(Instruction i){
  List<Integer> r=new ArrayList<>();
  if(i instanceof RegisterRangeInstruction x){for(int k=0;k<x.getRegisterCount();k++)r.add(x.getStartRegister()+k);}
  else if(i instanceof FiveRegisterInstruction x){int[] a={x.getRegisterC(),x.getRegisterD(),x.getRegisterE(),x.getRegisterF(),x.getRegisterG()};for(int k=0;k<x.getRegisterCount();k++)r.add(a[k]);}
  else if(i instanceof ThreeRegisterInstruction x){r.add(x.getRegisterA());r.add(x.getRegisterB());r.add(x.getRegisterC());}
  else if(i instanceof TwoRegisterInstruction x){r.add(x.getRegisterA());r.add(x.getRegisterB());}
  else if(i instanceof OneRegisterInstruction x)r.add(x.getRegisterA());
  return r;
 }
 static String shape(MethodImplementation m)throws Exception{
  StringBuilder b=new StringBuilder().append(m.getRegisterCount()).append('|');
  for(Instruction i:m.getInstructions()){
   b.append(i.getOpcode().name()).append(':').append(i.getCodeUnits()).append(':').append(regs(i));
   if(i instanceof ReferenceInstruction x)b.append(":ref=").append(x.getReference());
   if(i instanceof DualReferenceInstruction x)b.append(":ref2=").append(x.getReference2());
   if(i instanceof WideLiteralInstruction x)b.append(":lit=").append(x.getWideLiteral());
   if(i instanceof OffsetInstruction x)b.append(":off=").append(x.getCodeOffset());
   if(i instanceof SwitchPayload x)for(var e:x.getSwitchElements())b.append(":switch=").append(e.getKey()).append(',').append(e.getOffset());
   if(i instanceof ArrayPayload x)b.append(":array=").append(x.getElementWidth()).append(',').append(x.getArrayElements());
   b.append('\n');
  }
  for(var t:m.getTryBlocks()){
   b.append("try:").append(t.getStartCodeAddress()).append(',').append(t.getCodeUnitCount());
   for(var h:t.getExceptionHandlers())b.append(':').append(h.getExceptionType()).append(',').append(h.getHandlerCodeAddress());
   b.append('\n');
  }
  return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b.toString().getBytes(StandardCharsets.UTF_8)));
 }
 static String quote(String x){return x==null?"null":"\""+x.replace("\\","\\\\").replace("\"","\\\"").replace("$","\\$").replace("\n","\\n").replace("\r","\\r")+"\"";}
 static String list(String x){return "listOf("+x+")";}
 static String desc(Method m){return m.getDefiningClass()+"->"+m.getName()+"("+String.join("",m.getParameterTypes())+")"+m.getReturnType();}
 public static void main(String[] args)throws Exception{
  var specs=new LinkedHashMap<String,List<String[]>>();
  for(String l:Files.readAllLines(Path.of(args[1]))){if(l.isBlank()||l.startsWith("#"))continue;String[] p=l.split("\\|",-1);if(p.length!=7)throw new AssertionError(l);specs.computeIfAbsent(p[0],k->new ArrayList<>()).add(p);}
  Map<String,Method> methods=new HashMap<>();Map<String,String> dex=new HashMap<>();
  try(ZipFile z=new ZipFile(args[0])){for(var e:Collections.list(z.entries()))if(e.getName().matches("classes([0-9]+)?\\.dex")){try(var in=new BufferedInputStream(z.getInputStream(e))){
   var d=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in);
   for(var c:d.getClasses())for(var m:c.getMethods()){String s=desc(m);for(String k:specs.keySet())if(s.startsWith(k)){
    if(methods.put(k,m)!=null)throw new AssertionError("ambiguous "+k);dex.put(k,e.getName());}}
  }}}
  for(var e:specs.entrySet()){
   Method m=Objects.requireNonNull(methods.get(e.getKey()),e.getKey());var impl=m.getImplementation();
   Map<Integer,Instruction> at=new TreeMap<>();int pc=0;for(var i:impl.getInstructions()){at.put(pc,i);pc+=i.getCodeUnits();}
   System.out.println("    Target("+quote(dex.get(e.getKey()))+", "+quote(desc(m))+", "+impl.getRegisterCount()+", listOf(");
   var rows=e.getValue();rows.sort(Comparator.comparingInt(p->Integer.parseInt(p[1],16)));
   for(int k=0;k<rows.size();k++){
    var p=rows.get(k);var i=Objects.requireNonNull(at.get(Integer.parseInt(p[1],16)),desc(m)+":"+p[1]);
    String ref=i instanceof ReferenceInstruction x?x.getReference().toString():null;
    String sv=p[4].isEmpty()?"":", saves=mapOf("+p[4]+")";
    String argsPart=p[3].isEmpty()?"":","+p[3];
    String n=p[5].isEmpty()?"":", num="+p[5];
    System.out.println("        s(0x"+p[1]+", Opcode."+i.getOpcode().name()+", "+quote(ref)+", "+list(regs(i).toString().replace("[","").replace("]",""))+", "+quote(p[2].isEmpty()?null:p[2])+argsPart+(p[6].equals("a")?", after=true":"")+sv+n+")"+(k==rows.size()-1?"":","));
   }
   System.out.println("    ), exactHash="+quote(shape(impl))+"),");
  }
 }
}

// Local-only validation. No APK or SDK files are distributed.
import app.morphe.patcher.dex.SdkDexVerifier;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.analysis.*;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
class VerifyDiagnostic {
    static final String PROBE="Lapp/rikunode/morphe/extension/youtube/diagnostic/filters/RelatedItemsProbe;";
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] a)throws Exception{
        if(a.length!=3)throw new IllegalArgumentException("apk sdk extracted-dex-dir");
        File apk=new File(a[0]), sdk=new File(a[1]), dir=new File(a[2]);
        check(dir.mkdirs()||dir.isDirectory(),"dex dir");
        List<DexBackedDexFile> dexes=new ArrayList<>();
        try(ZipFile z=new ZipFile(apk)){
            for(ZipEntry e:Collections.list(z.entries()))if(e.getName().matches("classes([0-9]+)?\\.dex")){
                File f=new File(dir,e.getName());
                try(InputStream in=z.getInputStream(e)){Files.copy(in,f.toPath(),StandardCopyOption.REPLACE_EXISTING);}
                try(InputStream in=new BufferedInputStream(new FileInputStream(f))){dexes.add(DexBackedDexFile.fromInputStream(Opcodes.getDefault(),in));}
            }
        }
        check(!dexes.isEmpty(),"no dex");
        List<ClassProvider> providers=new ArrayList<>();
        for(var dex:dexes)providers.add(new DexClassProvider(dex));
        ClassPath cp=new ClassPath(providers);
        int methods=0,calls=0,coverage=0;Set<String> sites=new TreeSet<>();
        for(var dex:dexes)for(ClassDef cl:dex.getClasses())for(Method m:cl.getMethods()){
            if(m.getImplementation()==null)continue;
            if(cl.getType().equals(PROBE)&&m.getName().equals("coverageManifest")){
                for(Instruction i:m.getImplementation().getInstructions())
                    if(i instanceof ReferenceInstruction ri && ri.getReference() instanceof StringReference sr && sr.getString().contains("\"status\":\"installed\"")){
                        check(!sr.getString().contains("\"status\":\"skipped\""),"coverage partial");
                        coverage++;System.out.println("COVERAGE="+sr.getString());
                    }
            }
            if(cl.getType().startsWith("Lapp/rikunode/"))continue;
            List<Instruction> ins=new ArrayList<>();m.getImplementation().getInstructions().forEach(ins::add);
            List<Integer> hooks=new ArrayList<>();
            for(int i=0;i<ins.size();i++)if(ins.get(i) instanceof ReferenceInstruction ri && ri.getReference() instanceof MethodReference ref){
                check(!(ref.getDefiningClass().contains("app/rikunode/")&&ref.getName().equals("shouldDrop")),"filter injection in diagnostic APK");
                if(ref.getDefiningClass().equals(PROBE)){
                    check(ref.getName().equals("record")&&ref.getReturnType().equals("V"),"non-observation call "+ref);
                    check(ins.get(i).getOpcode()==Opcode.INVOKE_STATIC_RANGE,"not invoke-static/range");
                    hooks.add(i);
                }
            }
            if(hooks.isEmpty())continue;
            methods++;calls+=hooks.size();
            MethodAnalyzer analyzer=new MethodAnalyzer(cp,m,null,false);
            if(analyzer.getAnalysisException()!=null)throw analyzer.getAnalysisException();
            var analyzed=analyzer.getAnalyzedInstructions();
            for(int i:hooks){
                RegisterRangeInstruction rr=(RegisterRangeInstruction)ins.get(i);
                check(rr.getRegisterCount()==8,"record range arity");
                int base=rr.getStartRegister();
                for(int n=0;n<8;n++){
                    RegisterType t=analyzed.get(i).getPreInstructionRegisterType(base+n);
                    boolean ok=n==7 ? (t.category>=RegisterType.NULL&&t.category<=RegisterType.INTEGER) : t.category==RegisterType.NULL||t.category==RegisterType.REFERENCE;
                    check(ok,"bad record register type: "+m+" index="+i+" arg="+n+" "+t);
                }
                // The record call begins with a constant, and exactly seven operand copies/constants.
                Instruction ci=ins.get(i-8);
                check(ci instanceof ReferenceInstruction ri && ri.getReference() instanceof StringReference,"missing site string");
                sites.add(((StringReference)((ReferenceInstruction)ci).getReference()).getString());
            }
            System.out.println("TYPE PASS "+m+" calls="+hooks.size());
        }
        check(methods==22&&coverage==1,"unexpected coverage methods="+methods+" manifests="+coverage);
        for(String s:List.of("controller","reach_controller","reach_watch_t","reach_watch_s","reach_section_bind","bind_predicate","predicate","lookup_request","lookup_optional_first","lookup_empty_first","lookup_result_first","lookup_result_confirmed","lookup_optional_second","lookup_present_second","lookup_result_second"))
            check(sites.contains(s),"missing "+s);
        System.out.println("PASS method analyzer: "+methods+" methods, "+calls+" void calls, sites="+sites.size());
        new SdkDexVerifier(sdk,"36.0.0").verifyDexDirectory(dir);
        System.out.println("PASS SdkDexVerifier");
    }
}

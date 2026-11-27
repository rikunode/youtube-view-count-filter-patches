"""Compare observation APK against official-only output; requires private inspect_dex.py."""
import sys, re, struct, json, subprocess
from pathlib import Path
from zipfile import ZipFile
analysis, receipt, aapt = map(Path, sys.argv[1:4])
sys.path.insert(0,str(analysis))
import inspect_dex as d
source=Path("patches/src/main/kotlin/app/rikunode/morphe/patches/youtube/RelatedItemsProbePatch.kt").read_text(encoding="utf-8")
targets=dict((m,int(n)) for m,n in re.findall(r'Target\("classes[^"]*",\s*"([^"]+)",\s*(\d+),',source))
assert len(targets)==22, len(targets)
def read(path):
    out={}; coverage=[]; custom=set()
    with ZipFile(path) as z:
        for name in z.namelist():
            if not re.fullmatch(r"classes(?:[0-9]+)?\.dex",name):continue
            x=d.Dex(name,z.read(name))
            for s in x.strings:
                if s.startswith('[{') and '"group":"predicate"' in s:
                    coverage.append(json.loads(s))
            for idx,pos in x.code:
                method=x.methods[idx]
                if method in targets:
                    assert method not in out,method
                    out[method]=(x,pos)
                if method.startswith("Lapp/rikunode/"):custom.add(method)
    return out,coverage,custom
def normalized(x,pos,oldreg,patched):
    seq=list(x.instructions(pos));kept=[];calls=0
    for pc,op,u in seq:
        ref=x.reference(op,u)
        if patched and ref and ref[0]=="methods" and "diagnostic/filters/RelatedItemsProbe;->record(" in x.methods[ref[1]]:
            assert op==0x77 and x.methods[ref[1]].endswith(")V");calls+=1;continue
        if patched and op in (2,5,8) and (u[0]>>8>=oldreg or u[1]>=oldreg):continue
        if patched and op in (0x13,0x1a,0x1b) and u[0]>>8>=oldreg:continue
        if op==0 and u[0]==0:continue
        kept.append((pc,op,u))
    def target(p):return next((i for i,(pc,op,u) in enumerate(kept) if pc>=p),len(kept))
    out=[];switch_ref={}
    for pc,op,u in kept:
        if op in (0x2b,0x2c):switch_ref[pc+d.signed(u[1]|u[2]<<16,32)]=pc
    for pc,op,u in kept:
        a=u[0]>>8
        if op in (0x28,0x29,0x2a):
            delta=d.signed(a,8) if op==0x28 else d.signed(u[1],16) if op==0x29 else d.signed(u[1]|u[2]<<16,32)
            out.append(("goto",target(pc+delta)))
        elif 0x32<=op<=0x3d:out.append((op,a,target(pc+d.signed(u[1],16))))
        elif op in (0x26,0x2b,0x2c):out.append((op,a,target(pc+d.signed(u[1]|u[2]<<16,32))))
        elif op==0 and u[0] in (0x100,0x200):
            count=u[1];start=4 if u[0]==0x100 else 2+2*count
            out.append((u[:start],tuple(target(switch_ref[pc]+d.signed(u[i]|u[i+1]<<16,32)) for i in range(start,len(u),2))))
        elif op in (0x1a,0x1b):out.append(("string",a,x.strings[u[1] if op==0x1a else u[1]|u[2]<<16]))
        else:
            ref=x.reference(op,u)
            out.append((op,a,getattr(x,ref[0])[ref[1]],u[2:]) if ref else (op,u))
    return out,calls
def xml(apk,name):
    return subprocess.check_output([str(aapt),"dump","xmltree","--file",name,str(apk)],encoding="utf-8",errors="replace")
def settings(apk,mode):
    manifest=xml(apk,"AndroidManifest.xml")
    package=re.search(r'A: package="([^"]+)"',manifest).group(1)
    activity="app.rikunode.morphe.extension.youtube.diagnostic.settings.RelatedItemsProbeActivity"
    provider="app.rikunode.morphe.extension.youtube.diagnostic.settings.RelatedItemsProbeProvider"
    for cls in (activity,provider):
        matches=[s for s in re.split(r'\n\s*E: ',manifest) if '="'+cls+'"' in s]
        assert len(matches)==int(mode=="probe"),(apk,cls,len(matches))
        if matches:assert re.search(r'android:exported\([^)]*\)=(?:false|\(type 0x12\)0x0)',matches[0]),matches
    if mode=="probe":assert package+".rikunode.relatedprobe.init" in manifest
    with ZipFile(apk) as z:
        variants=[n for n in z.namelist() if re.fullmatch(r"res/xml/morphe_prefs(?:_icons(?:_bold)?)?\.xml",n)]
    assert variants,(apk,"preferences absent")
    for name in variants:
        tree=xml(apk,name)
        for key,wanted in (("rikunode_relatedprobe_settings",mode=="probe"),("rikunode_viewcount_settings",mode=="filter")):
            assert len(re.findall(r'android:key\([^)]*\)="'+key+'"',tree))==int(wanted),(apk,name,key)
        if mode=="probe":
            assert len(re.findall(r'android:targetClass\([^)]*\)="'+activity+'"',tree))==1,(apk,name)
            assert re.search(r'android:targetPackage\([^)]*\)="'+re.escape(package)+'"',tree)
    print("PASS settings/manifest",apk.parent.name,len(variants),"variants")
for env in ("stable","dev14"):
    base,bc,bcustom=read(receipt/(env+"-neither")/"patched.apk")
    patched,coverage,custom=read(receipt/(env+"-probe")/"patched.apk")
    assert set(base)==set(patched)==set(targets)
    assert not bc and not bcustom and len(coverage)==1
    assert all(row["status"]=="installed" for row in coverage[0])
    assert not any("ViewCountFilter;->" in m for m in custom)
    calls=0
    for method in targets:
        x,p=base[method];y,q=patched[method]
        old=struct.unpack_from("<4H",x.data,p);new=struct.unpack_from("<4H",y.data,q)
        assert new[0]==old[0]+24 and new[1]==old[1] and new[3]==old[3],(method,old,new)
        orig,_=normalized(x,p,old[0],False);actual,n=normalized(y,q,old[0],True)
        assert orig==actual,(env,method,len(orig),len(actual))
        calls+=n
    assert calls==53,calls
    print("PASS original instructions, branch/switch targets, register bank and try count",env,len(targets),calls)
    for mode in ("probe","filter","neither"):
        apk=receipt/(env+"-"+mode)/"patched.apk"
        settings(apk,mode)
        if mode=="filter":
            _,c,classes=read(apk)
            assert not c and not any("/diagnostic/" in m for m in classes)
partial,c,_=read(receipt/"stable-partial"/"patched.apk")
assert len(c)==1
rows=c[0];p=[r for r in rows if r["group"]=="predicate"]
assert p and all(r["status"]=="skipped" and r["reason"] for r in p),p
assert all(r["status"]=="installed" for r in rows if r["group"]!="predicate")
x,pos=partial[next(m for m in targets if m.startswith("Lrrn;->C("))]
assert not any(ref and ref[0]=="methods" and "RelatedItemsProbe;->record(" in x.methods[ref[1]] for _,op,u in x.instructions(pos) for ref in [x.reference(op,u)])
print("PASS missing predicate: no injection, other groups installed, coverage skip reason embedded")

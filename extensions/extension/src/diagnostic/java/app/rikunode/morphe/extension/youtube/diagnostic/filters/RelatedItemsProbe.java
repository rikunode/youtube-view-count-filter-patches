package app.rikunode.morphe.extension.youtube.diagnostic.filters;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import java.io.*;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;

/** Opt-in local observer. No callback, app return value, UI or filter setting is changed. */
public final class RelatedItemsProbe {
    private static final int MAX_IDS = 100000, MAX_LIST = 256, MAX_PATH = 4096;
    private static final Object MISSING = new Object();
    private static volatile State state;
    private static final Map<String,Long> early = new LinkedHashMap<>();
    private static final int MAX_SITES = 128;
    private static final List<WeakReference<?>[]> observedControllers=new ArrayList<>();
    private static void rememberController(Object a,Object b){
        if(a==null||b==null)return;
        synchronized(observedControllers){
            Iterator<WeakReference<?>[]> it=observedControllers.iterator();
            while(it.hasNext()){
                WeakReference<?>[] pair=it.next();
                if(pair[0].get()==null||pair[1].get()==null||pair[0].get()==a)it.remove();
            }
            if(observedControllers.size()>=16)observedControllers.remove(0);
            observedControllers.add(new WeakReference<?>[]{new WeakReference<>(a),new WeakReference<>(b)});
        }
    }

    // Replaced in the APK by the patch after resolving all dependency groups.
    public static String coverageManifest() { return "[]"; }

    private static String boundedKey(Map<String,?> map,String key,int limit) {
        return map.containsKey(key)||map.size()<limit-1?key:"other";
    }
    private static final class Counts {
        long attempted,beforeReady,accepted,dropped,afterStop,observerError,emitted;
        JSONObject json() throws Exception {
            return new JSONObject().put("attempted",attempted).put("before_ready",beforeReady)
                .put("accepted",accepted).put("dropped",dropped).put("after_stop",afterStop)
                .put("observer_error",observerError).put("emitted",emitted);
        }
    }
    private static final class Loss {
        final long first=SystemClock.elapsedRealtime();
        long last=first,count;
        void add(){last=SystemClock.elapsedRealtime();count++;}
        JSONObject json() throws Exception {return new JSONObject().put("firstElapsedMs",first)
            .put("lastElapsedMs",last).put("count",count);}
    }

    private RelatedItemsProbe() {}

    public static synchronized void initialize(Context context) {
        if(state==null) startRun(context,"unlabelled");
    }

    public static synchronized void startRun(Context context,String sample) {
        try {
            if(context==null) return;
            State old=state;
            if(old!=null && old.recording) return; // Explicit stop/export before replacing a run.
            if(old!=null && old.writer!=null && old.writer.isAlive()) return;
            State s=new State(context.getApplicationContext());
            s.sample=sample;
            for(Map.Entry<String,Long> e:early.entrySet()){
                Counts c=s.counts(e.getKey());c.attempted+=e.getValue();c.beforeReady+=e.getValue();
                s.noteLoss(e.getKey(),"before_ready",e.getValue(),true);
            }
            early.clear();
            state=s;
            s.start();
        } catch(Throwable ignored) {}
    }

    public static boolean enabled() { return state != null; }
    public static String runId(){State s=state;return s==null?"unavailable":s.run;}

    public static void record(String site, Object a, Object b, Object c, Object d,
                              Object e, Object f, int n) {
        if("controller".equals(site))try{rememberController(a,b);}catch(Throwable ignored){}
        State s=state;
        if(s==null){
            synchronized(RelatedItemsProbe.class){
                s=state;
                if(s==null){
                    String k=boundedKey(early,site,MAX_SITES);
                    Long v=early.get(k);early.put(k,v==null?1L:v+1);return;
                }
            }
        }
        try {
            synchronized(s){
                Counts counter=s.counts(site);counter.attempted++;
                if(!s.recording){counter.afterStop++;return;}
                if(!s.ready){
                    counter.beforeReady++;s.noteLoss(site,"before_ready",1,true);
                    return;
                }
                String previous=s.activeSite;s.activeSite=site;
                try{s.observe(site,a,b,c,d,e,f,n);}
                catch(Throwable ignored){counter.observerError++;s.noteLoss(site,"observer_error",1,true);s.stopForLoss("observer_error");}
                finally{s.activeSite=previous;}
            }
        }catch(Throwable ignored){}
    }

    public static void mark(String sample) {
        State s = state;
        if (s == null) return;
        try { synchronized(s) {
            if(!s.recording||!s.ready)return;
            if (!Arrays.asList("watch_initial","watch_continuation","watch_reopen",
                    "watch_metadata","watch_comments","home","search","subscriptions",
                    "playlist","watch_playlist","player_retained","unlabelled").contains(sample)) return;
            s.sample = sample;
            for(Row row:s.rows.values())row.lastGeometry=null;
            s.emit(s.event("sample_mark","operator"));
        }} catch (Throwable ignored) {}
    }

    public static void stop() {
        State s = state;
        if (s == null) return;
        try { synchronized(s) {
            if (s.recording && s.ready) s.emit(s.event("run_end","operator"));
            if(s.recording&&!s.ready)s.noteLoss("operator","stopped_before_ready",1,true);
            s.recording = false;
            s.queue.close();
        }} catch (Throwable ignored) {}
    }

    public static String status() {
        State s = state;
        if (s == null) return "Unavailable";
        synchronized(s) {
            return "Run: " + s.run + "\nWriter ready: " + s.ready + "\nRecording: " + s.recording
                    + "\nSample: " + s.sample + "\nAccepted: " + s.events + "\nDropped: " + s.dropped
                    + "\nCoverage: " + (s.partial?"PARTIAL":"installed; not a verification result")
                    + (s.loss == null ? "" : "\nLoss: " + s.loss)
                    + "\nWriter finished: " + (s.writer!=null&&!s.writer.isAlive());
        }
    }

    /** Call only on a worker after Stop. The output is the private UTF-8 JSONL file. */
    public static void export(OutputStream out) throws Exception {
        State s = state;
        if (s == null) throw new IOException("Probe unavailable");
        stop();
        if(s.writer!=null)s.writer.join(15000);
        if(s.writer==null || s.writer.isAlive())throw new IOException("Writer has not finished");
        try(InputStream in=new FileInputStream(s.file)){
            byte[] buffer=new byte[8192];
            for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);
        }
        synchronized(s){out.write((s.summary("export").toString()+"\n").getBytes(StandardCharsets.UTF_8));}
        out.flush();
    }

    private static final class IdentityRef extends WeakReference<Object> {
        final int hash;
        IdentityRef(Object o, ReferenceQueue<Object> q) { super(o,q); hash=System.identityHashCode(o); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (!(other instanceof IdentityRef)) return false;
            Object a=get(), b=((IdentityRef)other).get();
            return a != null && a == b;
        }
    }

    private static final class Bind {
        final long generation, section, controller, model, epoch;
        final boolean verified;
        Bind(long g,long s,long c,long m,long epoch,boolean verified) {
            generation=g;section=s;controller=c;model=m;this.epoch=epoch;this.verified=verified;
        }
    }

    private static final class Frame {
        final long id, a, b, c;
        long edge, outgoingEdge;
        long parentElement;
        Set<Long> memberships = Collections.emptySet();
        String key, parentPath;
        Integer type;
        boolean pathSeen;
        long p1,p2,p3;
        Frame(long id,long a,long b,long c) {this.id=id;this.a=a;this.b=b;this.c=c;}
        boolean matches(long a,long b,long c) { return this.a==a && this.b==b && this.c==c; }
    }

    private static final class Edge {
        final long id, parent, child, context, litho, parentElement;
        final Set<Long> memberships;
        Edge(long id,long parent,long child,long context,long litho,long parentElement,Set<Long> memberships) {
            this.id=id;this.parent=parent;this.child=child;this.context=context;
            this.litho=litho;this.parentElement=parentElement;this.memberships=memberships;
        }
    }

    private static final class Row {
        final long id, view, row, holder, latiq;
        final WeakReference<View> ref;
        final Set<Long> memberships;
        String lastGeometry;
        boolean bound;
        Row(long id,long view,long row,long holder,long latiq,View v,Set<Long> memberships) {
            this.id=id;this.view=view;this.row=row;this.holder=holder;this.latiq=latiq;
            ref=new WeakReference<>(v);this.memberships=memberships;
        }
    }

    private static final class Root {
        final long lastj, bytes, ctx, litho, row;
        final Set<Long> memberships;
        Root(long lastj,long bytes,long ctx,long litho,long row,Set<Long> memberships) {
            this.lastj=lastj;this.bytes=bytes;this.ctx=ctx;this.litho=litho;this.row=row;
            this.memberships=memberships;
        }
    }

    private static final class State {
        final String run = UUID.randomUUID().toString();
        final Mac mac;
        final File file;
        final CaptureQueue queue=new CaptureQueue();
        final Map<String,Counts> counters=new LinkedHashMap<>();
        final Map<String,Loss> losses=new LinkedHashMap<>();
        final JSONArray coverage;
        Thread writer;
        volatile boolean ready;
        boolean partial, causalLoss;
        String activeSite;
        final ReferenceQueue<Object> collected = new ReferenceQueue<>();
        final Map<IdentityRef,Long> identities = new HashMap<>();
        final Map<Long,Set<Long>> controllers = new HashMap<>();
        final Map<Long,Set<Long>> models = new HashMap<>();
        final Map<Long,Bind> binds = new HashMap<>();
        final Map<Long,Long> sectionEpoch = new HashMap<>(), sectionLatest = new HashMap<>();
        final Map<Long,Set<Long>> ownership = new HashMap<>();
        final Map<Long,Edge> lazy = new HashMap<>();
        final Map<Long,Row> rows = new HashMap<>();
        final ThreadLocal<Map<String,Frame>> frames = new ThreadLocal<>();
        final ThreadLocal<Map<String,Root>> roots = new ThreadLocal<>();
        final Handler main = new Handler(Looper.getMainLooper());
        long next=1, events, dropped;
        int serializedSamples;
        volatile boolean recording = true;
        String sample="unlabelled", loss;

        State(Context context) throws Exception {
            File dir=new File(context.getFilesDir(),"related-items-runs");
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create run directory");
            file=new File(dir,run+".jsonl");
            coverage=new JSONArray(coverageManifest());
            partial=coverage.length()==0;
            for(int i=0;i<coverage.length();i++)if(!"installed".equals(coverage.getJSONObject(i).optString("status")))partial=true;
            byte[] secret=new byte[32];
            new SecureRandom().nextBytes(secret);
            mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret,"HmacSHA256"));
            Arrays.fill(secret,(byte)0);
        }

        void start() {
            synchronized(this){
                try{emit(event("run_start","provider").put("coverage",coverage).put("partial",partial));}
                catch(Exception ex){stopForLoss("start_error");}
            }
            writer=new Thread(() -> {
                List<CaptureQueue.Item> batch=Collections.emptyList();
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file,false),64*1024)){
                    synchronized(this){
                        if(recording){
                            emit(event("writer_ready","writer"));
                            ready=true;
                            synchronized(observedControllers){
                                for(WeakReference<?>[] pair:observedControllers){
                                    Object a=pair[0].get(),b=pair[1].get();
                                    if(a!=null&&b!=null)controllerEvent(a,b,"previous_observed_reference");
                                }
                            }
                        }
                    }
                    while(true){
                        batch=queue.takeBatch();
                        if(batch.isEmpty())break;
                        for(CaptureQueue.Item item:batch)out.write(item.bytes);
                        out.flush();
                        synchronized(this){for(CaptureQueue.Item item:batch)counts(item.site).emitted++;}
                        batch=Collections.emptyList();
                    }
                    byte[] end;
                    synchronized(this){end=(summary("writer").toString()+"\n").getBytes(StandardCharsets.UTF_8);}
                    out.write(end);
                }catch(Throwable ignored){
                    synchronized(this){
                        for(CaptureQueue.Item item:batch){counts(item.site).dropped++;noteLoss(item.site,"writer_error",1,true);}
                        for(CaptureQueue.Item item:queue.abort()){counts(item.site).dropped++;noteLoss(item.site,"writer_error",1,true);}
                        stopForLoss("writer_error");
                    }
                }
            },"related-probe-writer");
            writer.setDaemon(true);
            writer.start();
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    try {
                        synchronized(State.this) {
                            if (!recording) return;
                            Iterator<Row> it=rows.values().iterator();
                            while (it.hasNext()) {
                                Row row=it.next();
                                if (row.ref.get()==null) it.remove();
                                else if(row.bound) geometry(row,false);
                            }
                        }
                        main.postDelayed(this,750);
                    } catch(Throwable ignored) { synchronized(State.this) { stopForLoss("geometry_error"); } }
                }
            },750);
        }

        Counts counts(String site){
            String key=boundedKey(counters,site,MAX_SITES);
            Counts c=counters.get(key);
            if(c==null){c=new Counts();counters.put(key,c);}
            return c;
        }

        void noteLoss(String site,String reason,long count,boolean causal){
            partial=true;causalLoss|=causal;
            if(loss==null)loss=reason;
            String key=boundedKey(losses,site+":"+reason,512);
            Loss l=losses.get(key);if(l==null){l=new Loss();losses.put(key,l);}
            l.last=SystemClock.elapsedRealtime();l.count+=count;
        }

        JSONObject summary(String source) throws Exception {
            JSONObject c=new JSONObject(),l=new JSONObject();
            for(Map.Entry<String,Counts> e:counters.entrySet())c.put(e.getKey(),e.getValue().json());
            for(Map.Entry<String,Loss> e:losses.entrySet())l.put(e.getKey(),e.getValue().json());
            return event("run_summary",source).put("coverage",coverage).put("partial",partial)
                .put("causalLoss",causalLoss).put("captureStopped",!recording)
                .put("accepted",events).put("dropped",dropped).put("counters",c).put("losses",l);
        }

        void stopForLoss(String reason) {
            noteLoss(activeSite==null?"internal":activeSite,reason,1,true);
            recording=false;queue.close();
        }

        JSONObject event(String event,String site) throws Exception {
            return new JSONObject().put("run",run).put("sample",sample).put("event",event)
                    .put("site",site).put("elapsedMs",SystemClock.elapsedRealtime());
        }

        void emit(JSONObject o) throws Exception {
            String event=o.optString("event");
            String site=activeSite==null?o.optString("site"):activeSite;
            Counts c=counts(site);
            if(!recording){c.dropped++;dropped++;noteLoss(site,"closed",1,true);return;}
            boolean highFrequency="element".equals(event)||"element_edge".equals(event);
            boolean bulk=highFrequency && "unjoined".equals(o.optString("join")) &&
                "no_exact_reference".equals(o.optString("joinReason"));
            int lane=event.startsWith("run_")||event.equals("writer_ready")||event.equals("sample_mark")
                ?CaptureQueue.CONTROL:bulk?CaptureQueue.BULK:highFrequency?CaptureQueue.CORRELATED:CaptureQueue.UPSTREAM;
            byte[] bytes=(o.toString()+"\n").getBytes(StandardCharsets.UTF_8);
            String reason=queue.offer(new CaptureQueue.Item(site,bytes,lane));
            if(reason!=null){c.dropped++;dropped++;noteLoss(site,reason,1,!bulk);return;}
            c.accepted++;events++;
        }

        long id(Object o) {
            if(o==null || o==MISSING) return 0;
            IdentityRef dead;
            while((dead=(IdentityRef)collected.poll())!=null) identities.remove(dead);
            IdentityRef key=new IdentityRef(o,null);
            Long found=identities.get(key);
            if(found!=null) return found;
            if(next>=MAX_IDS) { stopForLoss("identity_limit"); return 0; }
            long v=next++;
            identities.put(new IdentityRef(o,collected),v);
            return v;
        }

        long serial() {
            if(next>=MAX_IDS) { stopForLoss("identity_limit"); return 0; }
            return next++;
        }

        static Object field(Object o,String name) {
            if(o==null || o==MISSING) return MISSING;
            try {
                for(Class<?> k=o.getClass();k!=null;k=k.getSuperclass()) {
                    try { Field f=k.getDeclaredField(name); f.setAccessible(true); return f.get(o); }
                    catch(NoSuchFieldException ignored) {}
                }
            } catch(Throwable ignored) {}
            return MISSING;
        }

        static boolean type(Object o,String name) {
            return o!=null && o!=MISSING && o.getClass().getName().equals(name);
        }

        static Object nil(Object value) { return value==null || value==MISSING ? JSONObject.NULL:value; }
        static String reason(Object value) { return value==MISSING?"unavailable":value==null?"null":"ok"; }

        static String descriptor(Object o) {
            if(o==null || o==MISSING) return null;
            String n=o.getClass().getName().replace('.','/');
            return n.startsWith("[")?n:"L"+n+";";
        }

        JSONObject typed(Object o) throws Exception {
            return new JSONObject().put("id",nilId(id(o))).put("type",nil(descriptor(o)))
                    .put("reason",reason(o));
        }

        static Object nilId(long n) { return n==0?JSONObject.NULL:n; }

        void obj(JSONObject j,String name,Object o) throws Exception { j.put(name,typed(o)); }

        // Preserve ONLY statically confirmed non-personal keys; all other segments
        // get an HMAC token. The secret and unredacted paths never leave the process.
        String token(String value) {
            if(value==null || value.isEmpty()) return value;
            if(Arrays.asList("related-items","comments-entry-point","slim-video-metadata",
                    "METADATA_GHOST_CARDS","video_lockup_with_signal").contains(value)) return value;
            byte[] digest=mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder("#");
            for(byte b:digest) {
                int v=b&255;
                out.append("0123456789abcdef".charAt(v>>>4)).append("0123456789abcdef".charAt(v&15));
            }
            return out.toString();
        }

        void path(JSONObject j,String name,Object raw) throws Exception {
            if(!(raw instanceof String)) {
                j.put(name,JSONObject.NULL).put(name+"Reason",reason(raw).equals("ok")?"unsupported":reason(raw));
                return;
            }
            String value=(String)raw;
            if(value.length()>MAX_PATH) {
                j.put(name,JSONObject.NULL).put(name+"Reason","path_truncated");
                return;
            }
            String[] parts=value.split(",",-1);
            if(parts.length>MAX_LIST) {
                j.put(name,JSONObject.NULL).put(name+"Reason","path_truncated");
                return;
            }
            StringBuilder out=new StringBuilder();
            for(int i=0;i<parts.length;i++) {
                if(i>0)out.append(',');
                out.append(token(parts[i]));
            }
            j.put(name,out.toString()).put(name+"Reason","ok");
        }

        boolean present(Object o,String fieldName) {
            Object bits=field(o,fieldName);
            return bits instanceof Integer && (((Integer)bits)&0x20)!=0;
        }

        JSONObject sectionMeta(Object section,Object model) throws Exception {
            JSONObject j=new JSONObject();
            obj(j,"sectionModel",model);
            j.put("modelType",nil(descriptor(model))).put("sectionType",nil(descriptor(section)));
            Object bits=field(model,"c"), raw=field(model,"i");
            if(!(bits instanceof Integer) || !type(model,"bggp")) {
                j.put("idPresent",JSONObject.NULL).put("sectionId",JSONObject.NULL).put("sectionIdReason","unavailable");
            } else {
                boolean p=present(model,"c");
                j.put("idPresent",p);
                j.put("sectionId",raw instanceof String?token((String)raw):JSONObject.NULL);
                j.put("sectionIdReason",!p?"presence_false":raw instanceof String?(((String)raw).isEmpty()?"empty":"ok"):reason(raw));
            }
            return j;
        }

        void merge(JSONObject j,JSONObject values) throws Exception {
            Iterator<String> keys=values.keys();
            while(keys.hasNext()) { String k=keys.next();j.put(k,values.get(k)); }
        }

        JSONArray list(Object values) throws Exception {
            JSONArray out=new JSONArray();
            if(!(values instanceof Iterable<?>)) return out;
            int n=0;
            for(Object item:(Iterable<?>)values) {
                if(n++>=MAX_LIST) { stopForLoss("collection_limit");break; }
                out.put(typed(item));
            }
            return out;
        }

        void items(JSONObject j,String name,Object values) throws Exception {
            obj(j,name+"Collection",values);
            j.put(name,values instanceof Iterable<?>?list(values):JSONObject.NULL);
            j.put(name+"Reason",values instanceof Iterable<?>?"ok":values==null?"null":"unavailable");
        }

        Set<Long> singleton(long n) {
            return n==0?Collections.emptySet():Collections.singleton(n);
        }

        Set<Long> union(Set<Long> a,Set<Long> b) {
            Set<Long> c=new LinkedHashSet<>(a);c.addAll(b);return c;
        }

        Set<Long> get(Map<Long,Set<Long>> map,long k) {
            Set<Long> values=map.get(k);
            return values==null?Collections.emptySet():values;
        }

        void associate(Map<Long,Set<Long>> map,long k,Set<Long> values) {
            if(k==0 || values.isEmpty())return;
            Set<Long> target=map.get(k);
            if(target==null){target=new LinkedHashSet<>();map.put(k,target);}
            target.addAll(values);
            if(target.size()>MAX_LIST) stopForLoss("membership_limit");
        }

        Set<Long> live(Set<Long> values) {
            Set<Long> out=new LinkedHashSet<>();
            for(long n:values) {
                Bind b=binds.get(n);
                if(b!=null && Objects.equals(sectionEpoch.get(b.section),b.epoch))out.add(n);
            }
            return out;
        }

        Set<Long> membership(Object o) { return live(get(ownership,id(o))); }

        void own(Object o,Set<Long> set) { associate(ownership,id(o),live(set)); }
        void ownItems(Object values,Set<Long> set) {
            if(!(values instanceof Iterable<?>))return;
            int n=0;
            for(Object o:(Iterable<?>)values) {
                if(n++>=MAX_LIST){stopForLoss("collection_limit");break;}
                own(o,set);
            }
        }

        Set<Long> sectionBind(Object section,Object model) {
            long sectionId=id(section), modelId=id(model);
            Set<Long> all=new LinkedHashSet<>();
            for(long n:membership(model)) {
                Bind b=binds.get(n);
                if(b!=null && b.section==sectionId && b.model==modelId)all.add(n);
            }
            return all;
        }

        void join(JSONObject j,Set<Long> candidate) throws Exception {
            Set<Long> set=live(candidate);
            JSONArray ids=new JSONArray();
            for(long n:set)ids.put(n);
            j.put("memberships",ids);
            Bind b=set.size()==1?binds.get(set.iterator().next()):null;
            j.put("join",set.size()>1?"ambiguous":b!=null && b.verified && !causalLoss?"exact":"unjoined");
            j.put("controller",b==null?JSONObject.NULL:nilId(b.controller));
            j.put("section",b==null?JSONObject.NULL:nilId(b.section));
            j.put("bind",b==null?JSONObject.NULL:nilId(b.generation));
            j.put("joinReason",set.size()>1?"multiple_live_memberships":b==null?"no_exact_reference":!b.verified?"controller_or_input_unverified":causalLoss?"causal_loss":"ok");
        }

        Bind createBind(Object section,Object model,long controller,boolean verified,boolean update) {
            long sectionId=id(section),modelId=id(model), gen=serial();
            Long previousEpoch=sectionEpoch.get(sectionId);
            long epoch=update && previousEpoch!=null?previousEpoch:gen;
            if(gen==0 || sectionId==0)return null;
            sectionEpoch.put(sectionId,epoch);
            Bind bind=new Bind(gen,sectionId,controller,modelId,epoch,verified);
            binds.put(gen,bind);sectionLatest.put(sectionId,gen);
            own(model,singleton(gen));
            ownItems(field(model,"e"),singleton(gen));
            own(field(model,"d"),singleton(gen));
            return bind;
        }

        long controller(Object llpu) {
            Set<Long> c=get(controllers,id(llpu));
            return c.size()==1?c.iterator().next():0;
        }

        // The stack is only a call-frame discriminator for the paired sites in
        // ONE invocation. It never supplies section membership or a screen label.
        // Every cross-method link below requires the actual identity tuple.
        String frameKey(String cls,String method) {
            StackTraceElement[] stack=Thread.currentThread().getStackTrace();
            for(int i=0;i<stack.length;i++) {
                StackTraceElement frame=stack[i];
                if(cls.equals(frame.getClassName()) && method.equals(frame.getMethodName()))
                    return cls+"."+method+"@"+(stack.length-i);
            }
            return null;
        }

        Map<String,Frame> frameMap() {
            Map<String,Frame> map=frames.get();
            if(map==null){map=new HashMap<>();frames.set(map);}
            if(map.size()>128){map.clear();stopForLoss("frame_limit");}
            return map;
        }

        Frame begin(String cls,String method,Object a,Object b,Object c) {
            String key=frameKey(cls,method);
            if(key==null)return null;
            Frame f=new Frame(serial(),id(a),id(b),id(c));
            frameMap().put(key,f);return f;
        }

        Frame frame(String cls,String method) {
            String key=frameKey(cls,method);
            return key==null?null:frameMap().get(key);
        }

        // Reject an ancestor that happens to have the same tuple: only the
        // specified immediate host call chain is accepted, even under recursion.
        boolean callerChain(String... chain) {
            StackTraceElement[] stack=Thread.currentThread().getStackTrace();
            for(int i=0;i<stack.length;i++) {
                if (!(stack[i].getClassName()+"."+stack[i].getMethodName()).equals(chain[0])) continue;
                if(i+chain.length>stack.length)return false;
                for(int j=1;j<chain.length;j++)
                    if(!(stack[i+j].getClassName()+"."+stack[i+j].getMethodName()).equals(chain[j]))return false;
                return true;
            }
            return false;
        }

        void frameEnd(String cls,String method) {
            String key=frameKey(cls,method);
            if(key!=null)frameMap().remove(key);
        }

        Map<String,Root> rootMap() {
            Map<String,Root> map=roots.get();
            if(map==null){map=new HashMap<>();roots.set(map);}
            if(map.size()>128){map.clear();stopForLoss("root_frame_limit");}
            return map;
        }

        void rootPut(String cls,String method,Root root) {
            String k=frameKey(cls,method);
            if(k!=null)rootMap().put(k,root);
        }

        Root rootGet(String cls,String method) {
            String k=frameKey(cls,method);
            return k==null?null:rootMap().get(k);
        }

        void callId(JSONObject j,String key,Frame frame) throws Exception {
            j.put(key,frame==null?JSONObject.NULL:nilId(frame.id));
            j.put(key+"Reason",frame==null?"frame_unavailable":"ok");
        }

        void watch(String site,Object llpu,Object model) throws Exception {
            boolean pre=site.endsWith("_pre");String method=site.contains("_t_")?"t":"s";
            Frame f=pre?begin("rqa",method,llpu,model,null):frame("rqa",method);
            if(f!=null && f.a!=id(llpu))f=null;
            JSONObject j=event("watch_input",method.equals("t")?(pre?"rqa.t:008f":"rqa.t:0095"):(pre?"rqa.s:003f":"rqa.s:0043"));
            callId(j,"input",f);
            long c=controller(llpu);
            j.put("controller",nilId(c)).put("phase",pre?"pre":"post");
            obj(j,"controllerHost",llpu);obj(j,"inputModel",model);
            Object bkfr=field(model,"a"), elements=field(bkfr,"g");
            obj(j,"bkfr",bkfr);obj(j,"itemsCollection",elements);
            JSONArray children=new JSONArray();
            if(elements instanceof Iterable<?>) {
                int seen=0;
                for(Object child:(Iterable<?>)elements) {
                    if(seen++>=MAX_LIST){stopForLoss("watch_list_limit");break;}
                    JSONObject node=typed(child);
                    Object bits=field(child,"b"),section=field(child,"m");
                    node.put("modelPresent",bits instanceof Integer?present(child,"b"):JSONObject.NULL);
                    merge(node,sectionMeta(null,section));
                    items(node,"children",field(section,"e"));
                    children.put(node);
                    if(c!=0 && f!=null && type(child,"bkfy") && present(child,"b") && type(section,"bggp"))
                        associate(models,id(section),singleton(c));
                }
                j.put("items",children).put("itemsReason","ok");
            }else j.put("items",JSONObject.NULL).put("itemsReason","unavailable");
            emit(j);
            if(!pre)frameEnd("rqa",method);
        }

        void bind(Object section,Object lakmn,Object latsp) throws Exception {
            Object model=field(lakmn,"a");
            long c=controller(latsp);
            Set<Long> source=get(models,id(model));
            boolean verified=c!=0 && source.size()==1 && source.contains(c);
            Bind b=createBind(section,model,c,verified,false);
            Frame f=begin("rsh","a",section,model,latsp);
            if(f!=null && b!=null)f.p1=b.generation;
            JSONObject j=event("section_bind","rsh.a:005f");
            obj(j,"receiver",section);obj(j,"lakmn",lakmn);obj(j,"latsp",latsp);
            merge(j,sectionMeta(section,model));
            items(j,"children",field(model,"e"));
            join(j,b==null?Collections.emptySet():singleton(b.generation));
            emit(j);
        }

        void update(String site,Object section,Object value,Object response,Object receiver,Object mode) throws Exception {
            Object old=field(section,"i");
            long sec=id(section);
            Long previous=sectionLatest.get(sec);
            long prev=previous==null?0L:previous;
            Bind prior=binds.get(prev);
            long c=prior==null?0:prior.controller;
            boolean verified=prior!=null && prior.verified && type(section,"rrn")
                    && present(old,"c") && "related-items".equals(field(old,"i"));
            JSONObject j=event("section_update",site.equals("update_input")?"ltq.a:0127":site.equals("update_model")?"ltq.a:014f":"ltq.a:01cc");
            obj(j,"destination",section);obj(j,"destinationModel",old);
            j.put("destinationIdPresent",type(old,"bggp")?present(old,"c"):JSONObject.NULL);
            Object oldId=field(old,"i");
            j.put("destinationSectionId",oldId instanceof String?token((String)oldId):JSONObject.NULL);
            obj(j,"callbackDestination",field(receiver,"c"));obj(j,"mode",mode);
            if(site.equals("update_input")) {
                Frame f=begin("ltq","a",section,value,receiver);
                callId(j,"update",f);obj(j,"responseModel",value);
                merge(j,sectionMeta(section,value));items(j,"responseChildren",field(value,"e"));
                join(j,prior==null?Collections.emptySet():singleton(prior.generation));
            } else if(site.equals("update_model")) {
                Frame f=frame("ltq","a");
                if(f!=null && !f.matches(id(section),id(response),id(receiver)))f=null;
                callId(j,"update",f);obj(j,"responseModel",response);obj(j,"synthesizedLakmn",value);
                Object model=field(value,"a");
                merge(j,sectionMeta(section,model));items(j,"children",field(model,"e"));
                Bind b=createBind(section,model,c,verified && f!=null && field(receiver,"c")==section,true);
                join(j,b==null?Collections.emptySet():singleton(b.generation));
                frameEnd("ltq","a");
            } else {
                Frame f=begin("ltq","a",section,value,receiver);
                callId(j,"update",f);
                Bind b=createBind(section,null,c,verified && field(receiver,"c")==section,true);
                Set<Long> m=b==null?Collections.emptySet():singleton(b.generation);
                ownItems(value,m);items(j,"rows",value);merge(j,sectionMeta(section,old));join(j,m);
                if(f!=null && b!=null)f.p1=b.generation;
            }
            emit(j);
        }

        void children(String site,Object section,Object lakmn,Object value,Object renderer) throws Exception {
            Object model=field(lakmn,"a");
            Set<Long> m=sectionBind(section,model);
            if(site.equals("children_bind_enter")) {
                Frame f=(callerChain("atqv.j","rsh.a") || callerChain("atqv.j","qca.j","rsh.a") || callerChain("atqv.j","qca.j","rrn.j","rsh.a"))?frame("rsh","a"):null;
                if(f!=null && f.a==id(section) && f.b==id(model) && f.p1!=0 && Objects.equals(sectionLatest.get(id(section)),f.p1)){m=singleton(f.p1);f.p1=0;}
                else {Bind b=createBind(section,model,0,false,false);m=b==null?Collections.emptySet():singleton(b.generation);}
            } else if(site.equals("children_append")) {
                Frame f=callerChain("rrn.B","ltq.a")?frame("ltq","a"):null;
                m=f!=null && f.a==id(section) && f.b==id(lakmn)?singleton(f.p1):Collections.emptySet();
                if(f!=null)frameEnd("ltq","a");
            }
            JSONObject j=event("section_children",site.equals("children_bind_enter")?"atqv.j:0000":site.equals("children_input")?"atqv.mO:0010":
                    site.equals("children_rows")?"atqv.mO:0014":site.equals("children_elements")?"atqv.j:00a9":
                    site.equals("children_element_rows")?"atqv.j:00cc":site.equals("children_mode")?"atqv.hX:000a":"rrn.B:0013");
            obj(j,"receiver",section);
            if(!site.equals("children_append"))obj(j,"lakmn",lakmn);
            merge(j,sectionMeta(section,model));
            if(site.equals("children_bind_enter")) {items(j,"children",field(model,"e"));}
            else if(site.equals("children_input")) {items(j,"children",value);ownItems(value,m);}
            else if(site.equals("children_elements")) {
                obj(j,"bggn",value);obj(j,"renderer",renderer);own(value,m);own(renderer,m);
            } else if(site.equals("children_mode"))obj(j,"mode",value);
            else {Object list=site.equals("children_append")?lakmn:value;items(j,"rows",list);ownItems(list,m);}
            join(j,m);emit(j);
        }

        void geometry(Row row,boolean force) throws Exception {
            View v=row.ref.get();
            if(v==null)return;
            boolean attached=v.isAttachedToWindow();
            Rect rect=new Rect();
            boolean visible=attached && v.getGlobalVisibleRect(rect);
            if(visible){int[] location=new int[2];v.getRootView().getLocationOnScreen(location);rect.offset(location[0],location[1]);}
            String geometry=attached+":"+visible+":"+rect.flattenToString();
            if(!force && geometry.equals(row.lastGeometry))return;
            row.lastGeometry=geometry;
            JSONObject j=event("row_visible","view_geometry");
            j.put("rowBind",row.id).put("view",row.view).put("row",row.row)
                    .put("attached",attached).put("visible",visible)
                    .put("rect",visible?new JSONArray(Arrays.asList(rect.left,rect.top,rect.right,rect.bottom)):JSONObject.NULL)
                    .put("rectReason",visible?"ok":"not_visible");
            join(j,row.memberships);emit(j);
        }

        void row(String site,Object view,Object rowObj,Object holder,Object latiq) throws Exception {
            JSONObject j=event(site.equals("row_bind")?"row_bind":"row_observation",
                    site.equals("row_view")?"atjd.E:0005":site.equals("row_prepare")?"atjd.E:0041":"atjd.E:0054");
            long v=id(view),r=id(rowObj),h=id(holder),l=id(latiq);
            obj(j,"view",view);obj(j,"row",rowObj);obj(j,"holder",holder);obj(j,"latiq",latiq);
            if(site.equals("row_view")){
                Row old=rows.remove(v);
                if(old!=null)j.put("invalidatedRowBind",old.id);
            }else if(site.equals("row_prepare") && view instanceof View && v!=0 && r!=0 && l!=0) {
                if(rows.size()>=512){stopForLoss("row_limit");return;}
                if(h!=0){
                    Iterator<Row> existing=rows.values().iterator();
                    while(existing.hasNext())if(existing.next().holder==h)existing.remove();
                }
                Row row=new Row(serial(),v,r,h,l,(View)view,membership(rowObj));
                rows.put(v,row);j.put("rowBind",row.id);join(j,row.memberships);
                Frame f=begin("atjd","E",view,rowObj,latiq);if(f!=null)f.p1=row.id;
            }else if(site.equals("row_bind")){
                Row row=rows.get(v);
                if(row!=null && row.row==r && row.holder==h && row.latiq==l){
                    row.bound=true;j.put("rowBind",row.id);join(j,row.memberships);
                    if(Looper.myLooper()==Looper.getMainLooper())geometry(row,true);
                }else{j.put("rowBind",JSONObject.NULL);join(j,Collections.emptySet());}
                frameEnd("atjd","E");
            }
            emit(j);
        }

        void root(String site,Object a,Object b,Object c,Object d,Object e,Object f) throws Exception {
            JSONObject j=event("elements_root",site.equals("root_construct")?"astj.<init>:002f":
                    site.equals("root_row")?"astr.k:000f":site.equals("root_vwn_in")?"vwn.b:0000":
                    site.equals("root_vwn_bytes")?"vwn.b:0004":"vpo.b:003b");
            Set<Long> m=Collections.emptySet();
            if(site.equals("root_construct")) {
                Object renderer=field(a,"a"), bytes=field(a,"c");
                obj(j,"lastj",a);obj(j,"renderer",renderer);obj(j,"bytes",bytes);
                m=membership(renderer);own(a,m);
            }else if(site.equals("root_row")){
                Frame frame=frame("atjd","E");
                Row row=frame==null?null:rows.get(frame.a);
                if(row!=null && (row.id!=frame.p1 || row.row!=frame.b || row.latiq!=frame.c || row.latiq!=id(a)))row=null;
                obj(j,"latiq",a);obj(j,"lastj",b);obj(j,"litho",c);obj(j,"context",d);
                m=row==null?Collections.emptySet():live(row.memberships);
                long rb=row==null?0:row.id;
                j.put("rowBind",nilId(rb));
                rootPut("astr","k",new Root(id(b),0,id(d),id(c),rb,m));
            }else if(site.equals("root_vwn_in")){
                obj(j,"litho",a);obj(j,"context",b);obj(j,"lastj",c);
                Root source=callerChain("vwn.b","astr.k")?rootGet("astr","k"):null;
                long rb=0;
                if(source!=null && source.lastj==id(c) && source.ctx==id(b) && source.litho==id(a)) {
                    rb=source.row;m=live(source.memberships);
                }
                m=union(m,membership(c));
                rootPut("vwn","b",new Root(id(c),0,id(b),id(a),rb,m));
                j.put("rowBind",nilId(rb));
            }else if(site.equals("root_vwn_bytes")){
                obj(j,"litho",a);obj(j,"context",b);obj(j,"lastj",c);obj(j,"bytes",d);
                Root r=rootGet("vwn","b");
                long rb=0;
                if(r!=null && r.lastj==id(c) && r.ctx==id(b) && r.litho==id(a) && field(c,"c")==d){
                    m=live(r.memberships);rb=r.row;
                    rootPut("vwn","b",new Root(id(c),id(d),id(b),id(a),rb,m));
                }
                j.put("rowBind",nilId(rb));
            }else{
                obj(j,"inputLitho",a);obj(j,"inputContext",b);obj(j,"bytes",c);
                obj(j,"litho",d);obj(j,"context",e);obj(j,"root",f);
                Root r=callerChain("vpo.b","vwn.b")?rootGet("vwn","b"):null;
                long rb=0;
                if(r!=null && r.bytes!=0 && r.bytes==id(c) && r.ctx==id(b) && r.litho==id(a)){
                    m=live(r.memberships);rb=r.row;
                    j.put("lastjId",nilId(r.lastj));
                }
                rootPut("vpo","b",new Root(id(f),id(c),id(e),id(d),rb,m));
                j.put("rowBind",nilId(rb));
            }
            join(j,m);emit(j);
        }

        void element(String site,Object a,Object b,Object c,int n) throws Exception {
            JSONObject j=event("element",site.equals("element_enter")?"vpo.d:0000":site.equals("element_type")?"vpo.d:007f":
                    site.equals("element_key")?"vpo.d:00c7":site.equals("element_path")?"vpo.d:0162":"vpo.d:return");
            Frame f;
            if(site.equals("element_enter")) {
                f=begin("vpo","d",a,b,c);
                if(f!=null) {
                    Frame immediate=callerChain("vpo.d","vpo.f")?frame("vpo","f"):null;
                    Frame delayed=callerChain("vpo.d","vpn.a")?frame("vpn","a"):null;
                    Edge edge=null;
                    if(immediate!=null && immediate.p1==id(a) && immediate.p2==id(b) && immediate.p3==id(c) && immediate.edge!=0){
                        edge=new Edge(immediate.edge,immediate.c,id(c),id(b),id(a),immediate.parentElement,immediate.memberships);
                        immediate.p1=immediate.p2=immediate.p3=0;
                    }
                    if(delayed!=null && delayed.p1==id(a) && delayed.p2==id(b) && delayed.p3==id(c) && delayed.edge!=0){
                        edge=new Edge(delayed.edge,delayed.c,id(c),id(b),id(a),delayed.parentElement,delayed.memberships);
                        delayed.p1=delayed.p2=delayed.p3=0;
                    }
                    if(edge!=null){
                        f.edge=edge.id;f.parentElement=edge.parentElement;f.memberships=live(edge.memberships);
                    }else{
                        Root root=callerChain("vpo.d","vpo.a","vpo.b")?rootGet("vpo","b"):null;
                        if(root!=null && root.lastj==id(c) && root.ctx==id(b) && root.litho==id(a)){
                            f.memberships=union(f.memberships,root.memberships);
                            j.put("rowBind",nilId(root.row));
                        }
                    }
                    Object raw=field(b,"n");
                    f.parentPath=raw instanceof String?(String)raw:null;
                    path(j,"parentPath",raw);
                }else path(j,"parentPath",field(b,"n"));
                obj(j,"litho",a);obj(j,"context",b);obj(j,"node",c);
                j.put("elementType",nil(descriptor(c)));
            }else{
                f=frame("vpo","d");
                if(f!=null && (f.b!=id(a)||f.c!=id(b)))f=null;
                obj(j,"context",a);obj(j,"node",b);j.put("elementType",nil(descriptor(b)));
                if(site.equals("element_type")){if(f!=null)f.type=n;}
                if(site.equals("element_key")){if(f!=null)f.key=c instanceof String?(String)c:null;}
                if(site.equals("element_path")){
                    if(f!=null)f.pathSeen=true;
                    path(j,"currentPath",c);
                    path(j,"parentPath",f==null?field(a,"n"):f.parentPath);
                }
                if(site.equals("element_exit")){
                    obj(j,"result",c);
                    j.put("missingPath",f==null || !f.pathSeen);
                    frameEnd("vpo","d");
                }
            }
            callId(j,"element",f);
            j.put("edge",f==null?JSONObject.NULL:nilId(f.edge));
            j.put("parentElement",f==null?JSONObject.NULL:nilId(f.parentElement));
            j.put("typeExt",f==null||f.type==null?JSONObject.NULL:f.type)
                    .put("typeExtReason",f==null||f.type==null?"not_observed":"ok");
            j.put("key",f==null||f.key==null?JSONObject.NULL:token(f.key))
                    .put("keyReason",f==null||f.key==null?"not_observed":"ok");
            String countStatus="not_checked",countSource="none";
            if(site.equals("element_path") && type(b,"wfo")) {countStatus="unsupported";countSource="wfo";}
            else if(site.equals("element_path") && f!=null && "video_lockup_with_signal".equals(f.key) && type(b,"wvb")){
                countSource="serialized_probe";
                if(serializedSamples>=32)countStatus="not_checked";
                else{
                    serializedSamples++;
                    try{
                        Object raw=b.getClass().getMethod("f").invoke(b);
                        if(raw instanceof byte[] && ((byte[])raw).length<=256*1024){
                            long count=ViewCountParser.parse(new String((byte[])raw,StandardCharsets.UTF_8));
                            if(count>=0)j.put("count",count);
                            countStatus="unverified";
                            j.put("countReason",count>=0?"serialized_display_value":"missing_or_ambiguous");
                        }else{countStatus="unsupported";j.put("countReason","serialization_limit_or_type");}
                    }catch(Throwable ignored){countStatus="error";}
                }
            }
            j.put("countStatus",countStatus).put("countSource",countSource);
            join(j,f==null?Collections.emptySet():f.memberships);
            if("path_truncated".equals(j.optString("parentPathReason")) || "path_truncated".equals(j.optString("currentPathReason")))j.put("join","unjoined").put("joinReason","path_truncated");
            emit(j);
        }

        void edge(String site,Object a,Object b,Object c,Object d,Object e,int n) throws Exception {
            String offset=site.equals("edge_d_1")?"vpo.d:04dc":site.equals("edge_d_2")?"vpo.d:0562":
                    site.equals("edge_f_enter")?"vpo.f:0000":site.equals("edge_child")?"vpo.f:0048":
                    site.equals("edge_lazy")?"vpo.f:005f":site.equals("edge_immediate")?"vpo.f:0085":"vpn.a:0012";
            JSONObject j=event("element_edge",offset);
            Set<Long> m=Collections.emptySet();
            long edge=0,parent=0;
            if(site.startsWith("edge_d_")){
                Frame f=frame("vpo","d");
                if(f!=null && f.b==id(a) && f.c==id(b)){
                    f.p1=id(c);f.p2=id(d);f.p3=id(e);
                    f.outgoingEdge=serial();edge=f.outgoingEdge;parent=f.id;m=live(f.memberships);
                }
                obj(j,"parentContext",a);obj(j,"parentNode",b);obj(j,"actualLitho",c);
                obj(j,"actualContext",d);obj(j,"actualParent",e);
            }else if(site.equals("edge_f_enter")){
                Frame caller=callerChain("vpo.f","vpo.d")?frame("vpo","d"):null;
                Frame f=begin("vpo","f",a,b,c);
                if(f!=null && caller!=null && caller.outgoingEdge!=0 && caller.p1==id(a) && caller.p2==id(b) && caller.p3==id(c)){
                    f.memberships=live(caller.memberships);f.parentElement=caller.id;
                    m=f.memberships;edge=caller.outgoingEdge;parent=caller.id;
                    caller.p1=caller.p2=caller.p3=0;caller.outgoingEdge=0;
                }
                obj(j,"litho",a);obj(j,"parentContext",b);obj(j,"parentNode",c);
            }else if(site.equals("edge_lazy_call")){
                Edge source=lazy.get(id(a));
                Frame f=begin("vpn","a",a,c,d);
                if(f!=null && source!=null && source.child==id(d)){
                    f.edge=source.id;f.p1=id(b);f.p2=id(c);f.p3=id(d);
                    f.memberships=live(source.memberships);f.parentElement=source.parentElement;
                    m=f.memberships;edge=f.edge;parent=f.parentElement;
                    j.put("originalParentContext",nilId(source.context));
                }
                obj(j,"callable",a);obj(j,"litho",b);obj(j,"actualContext",c);obj(j,"child",d);
            }else{
                Frame f=frame("vpo","f");
                if(f!=null && (f.c!=id(a)||f.b!=id(b)))f=null;
                if(f!=null){
                    m=live(f.memberships);parent=f.parentElement;
                    if(site.equals("edge_child")){
                        f.edge=serial();f.p3=id(c);f.p1=f.p2=0;
                    }
                    if(f.p3==id(c)){
                        edge=f.edge;
                        if(site.equals("edge_lazy")){
                            Edge lazyEdge=new Edge(edge,id(a),id(c),id(b),id(e),parent,m);
                            Edge old=lazy.put(id(d),lazyEdge);
                            if(old!=null && old.id!=edge){lazy.remove(id(d));edge=0;m=Collections.emptySet();}
                        }else if(site.equals("edge_immediate")){
                            f.p1=id(d);f.p2=id(b);
                        }
                    }
                }
                obj(j,"parentNode",a);obj(j,"parentContext",b);obj(j,"child",c);
                if(site.equals("edge_lazy")){obj(j,"callable",d);obj(j,"litho",e);}
                else obj(j,"litho",d);
            }
            j.put("edge",nilId(edge)).put("parentElement",nilId(parent)).put("childIndex",n<0?JSONObject.NULL:n);
            join(j,m);emit(j);
        }

        void controllerEvent(Object a,Object b,String phase) throws Exception {
            JSONObject j=event("controller","rzm.nu:0912").put("phase",phase);
            boolean valid=type(a,"rqa")&&type(b,"lpu")&&field(a,"am")==b;
            if(valid)associate(controllers,id(b),singleton(id(a)));
            j.put("controller",valid?nilId(id(a)):JSONObject.NULL).put("valid",valid);
            obj(j,"controllerObject",a);obj(j,"controllerHost",b);emit(j);
        }

        void observe(String site,Object a,Object b,Object c,Object d,Object e,Object f,int n) throws Exception {
            if(site.equals("controller"))controllerEvent(a,b,"live");
            else if(site.startsWith("reach_")){
                JSONObject j=event("hook_reach",site);obj(j,"receiver",a);obj(j,"input",b);emit(j);
            }else if(site.equals("predicate")||site.equals("bind_predicate")){
                JSONObject j=event("related_predicate",site).put("result",n!=0);
                obj(j,"lakmn",a);obj(j,"controllerHost",b);
                merge(j,sectionMeta(null,field(a,"a")));emit(j);
            }else if(site.startsWith("lookup_")){
                JSONObject j=event("related_lookup",site);
                obj(j,"receiver",a);
                if(site.equals("lookup_request"))j.put("relatedKey","related-items".equals(b));
                else{
                    obj(j,"result",b);obj(j,"controllerHost",c);
                    j.put("controller",nilId(controller(c)));
                    j.put("relatedKey","related-items".equals(d));
                    if(site.equals("lookup_empty_first"))j.put("empty",n!=0);
                    if(site.equals("lookup_present_second"))j.put("present",n!=0);
                    if(site.startsWith("lookup_result")){
                        j.put("isRelatedSection",type(b,"rrn"));
                        merge(j,sectionMeta(b,field(b,"i")));
                    }
                }
                emit(j);
            }else if(site.startsWith("watch_"))watch(site,a,b);
            else if(site.equals("section_bind"))bind(a,b,c);
            else if(site.equals("update_input"))update(site,a,b,null,c,d);
            else if(site.equals("update_model"))update(site,a,b,c,d,e);
            else if(site.equals("update_rows"))update(site,a,b,null,c,d);
            else if(site.startsWith("children_"))children(site,a,b,c,d);
            else if(site.startsWith("child_")){
                JSONObject j=event("child_renderer",site.equals("child_atqs")?"atqs.b:0038":"rpi.b:000f");
                obj(j,"child",a);obj(j,"renderer",b);
                Set<Long> m=membership(a);own(b,m);join(j,m);emit(j);
            }else if(site.startsWith("root_"))root(site,a,b,c,d,e,f);
            else if(site.startsWith("element_"))element(site,a,b,c,n);
            else if(site.startsWith("edge_"))edge(site,a,b,c,d,e,n);
            else if(site.startsWith("row_"))row(site,a,b,c,d);
        }
    }
}

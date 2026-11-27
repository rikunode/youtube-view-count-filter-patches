import app.rikunode.morphe.extension.youtube.diagnostic.filters.RelatedItemsProbe;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;
// JVM-only fixtures for actual observer/queue code. No real YouTube or ART.
class lpu {}
class rqa {public lpu am=new lpu();}
class bggp {public int c=32;public String i="related-items";public List<Object> e=new ArrayList<>();}
class lakmn {public bggp a=new bggp();}
class bkfd {public int b,c;bkfd(int b,int c){this.b=b;this.c=c;}}
class blaz {}
class bggs {public int b,h;public Object R,dC;}
class berp {public int e=32;public bkfd bc=new bkfd(1,5);}
class bero {public List<Object> c=new ArrayList<>();}
class bkoh {public int b=4;public bero e=new bero();}
class bkof {public int b=0x1000000;public bkoh u=new bkoh();}
class lakmm {public bkof a=new bkof();}
class lakmu {public bkof a=new bkof();}
class bkfy {public int b,e;public bggp m;public bkof E;public blaz bD;}
class bkfr {public List<Object> g=new ArrayList<>();}
class lakmq {public bkfr a=new bkfr();}
class lakmv {public lakmq g=new lakmq();}
class atnz {
 public List<Object> A=new ArrayList<>(),B=new ArrayList<>();
 Object F(Object input,Object result){
  Object converter=new Object(),row=new Object(),parent=new Object(),attachment=new Object();
  ProbeRuntimeTest.full("host_f_enter",this,input,converter,null,null,null,-1);
  ProbeRuntimeTest.full("host_f_result",this,input,result,null,null,null,-1);
  if(result==null){ProbeRuntimeTest.full("host_f_null",this,input,null,null,null,null,-1);return null;}
  A.add(result);B.add(input);
  ProbeRuntimeTest.full("host_f_attach_x",this,input,result,row,parent,null,-1);
  ProbeRuntimeTest.full("host_f_commit",this,input,result,row,parent,attachment,-1);
  return result;
 }
 void aY(Object old,Object model,Object next){
  ProbeRuntimeTest.full("host_replace_begin",this,old,model,next,null,null,0);
  A.set(0,next);B.set(0,model);
  ProbeRuntimeTest.full("host_replace_lists",this,old,model,next,null,null,0);
  ProbeRuntimeTest.full("host_replace_attach_x",this,old,next,new Object(),new Object(),new Object(),-1);
  ProbeRuntimeTest.full("host_replace_commit",this,old,model,next,null,null,-1);
 }
 void s(Object model,Object container){
  ProbeRuntimeTest.full("host_s_begin",this,Collections.singletonList(model),null,null,null,null,-1);
  F(model,container);
  ProbeRuntimeTest.full("host_s_end",this,null,null,null,null,null,-1);
 }
 void be(lakmq m,Object input,Object result){
  ProbeRuntimeTest.full("host_scope_begin",this,m,null,null,null,null,1);
  ProbeRuntimeTest.full("host_models",this,m,Collections.singletonList(input),null,null,null,-1);
  s(input,result);
  ProbeRuntimeTest.full("host_scope_end",this,m,null,null,null,null,-1);
 }
}
class atsi {
 void call(Object child,Object output){
  Object sink=new Object(),list=new ArrayList<>(),converter=new Object();
  ProbeRuntimeTest.full("bridge_child_enter",this,child,converter,sink,null,null,-1);
  ProbeRuntimeTest.full("bridge_sink_list",new Object(),list,output,null,null,null,-1);
  ProbeRuntimeTest.full("bridge_sink_list",sink,list,output,null,null,null,-1);
  ProbeRuntimeTest.full("bridge_child_done",this,child,converter,sink,null,null,-1);
 }
}
class ProbeRuntimeTest {
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static void full(String site,Object a,Object b,Object c,Object d,Object e,Object f,int n){RelatedItemsProbe.record(site,a,b,c,d,e,f,n);}
 static void rec(String site,Object a,Object b,Object c,int n){full(site,a,b,c,null,null,null,n);}
 static void waitReady()throws Exception{
  long end=System.nanoTime()+5_000_000_000L;
  while(!RelatedItemsProbe.status().contains("Writer ready: true")&&System.nanoTime()<end)Thread.sleep(10);
  check(RelatedItemsProbe.status().contains("Writer ready: true"),"writer_ready");
 }
 static List<JSONObject> export()throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();RelatedItemsProbe.export(out);
  check(out.size()<=16*1024*1024,"file bound");
  List<JSONObject> rows=new ArrayList<>();
  for(String l:out.toString(StandardCharsets.UTF_8).split("\n"))if(!l.isEmpty())rows.add(new JSONObject(l));
  return rows;
 }
 static JSONObject last(List<JSONObject> rows){return rows.get(rows.size()-1);}
 static long count(List<JSONObject> rows,String event){return rows.stream().filter(j->event.equals(j.optString("event"))).count();}
 static JSONObject site(List<JSONObject> rows,String site){
  return rows.stream().filter(j->site.equals(j.optString("site"))).findFirst().orElseThrow();
 }
 static long id(JSONObject j,String key)throws Exception{return j.getJSONObject(key).getLong("id");}
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);rqa controller=new rqa();
  rec("controller",controller,controller.am,null,-1);
  Context context=new Context(dir.toFile());RelatedItemsProbe.initialize(context);waitReady();
  String first=RelatedItemsProbe.runId();Object generic=new Object();
  for(int i=0;i<24563;i++)rec("element_enter",generic,generic,generic,-1);
  check(RelatedItemsProbe.status().contains("Recording: true"),"bulk overflow must not stop capture");
  RelatedItemsProbe.mark("watch_initial");
  for(int i=0;i<5;i++)rec("edge_d_2",generic,generic,generic,-1);
  rec("edge_child",generic,generic,generic,-1);
  rec("element_enter",generic,generic,generic,-1);
  rec("predicate",new lakmn(),null,null,1);
  rec("controller",controller,controller.am,null,-1);

  bkfy ghost=new bkfy();ghost.b=32;ghost.m=new bggp();ghost.m.c=0;ghost.m.i="";
  for(int[] x:new int[][]{{1,6},{0,5},{1,99}}){
   bggs child=new bggs();child.h=0x20000000;child.dC=new bkfd(x[0],x[1]);ghost.m.e.add(child);
  }
  bggs mixed=new bggs();mixed.b=0x10000000;mixed.R=new blaz();ghost.m.e.add(mixed);
  rec("ghost_unlabelled",ghost,new Object(),null,-1);
  lakmv model=new lakmv();model.g.a.g.add(ghost);rec("ghost_wrap",model.g.a,model,null,-1);
  rec("source_cached",controller,new Object(),model,-1);
  bkfy source=new bkfy();source.b=0x800000;source.E=new bkof();source.E.u.e.c.add(new berp());
  model.g.a.g.add(source);
  lakmm payload=new lakmm();payload.a=source.E;Object optional=new Object();
  rec("select_bero",source,payload,optional,-1);
  rec("select_section",ghost,new lakmn(),new Object(),-1);
  rec("select_shelf",source,source.E,new Object(),-1);
  rec("select_other",source,new lakmu(),new Object(),-1);
  rec("select_direct",source,new blaz(),new Object(),-1);
  rec("select_empty",source,new Object(),null,-1);
  atnz host=new atnz();Object container=new Object();host.be(model.g,payload,container);
  host.aY(container,new Object(),new Object());host.F(payload,null);
  atnz secondHost=new atnz();secondHost.be(model.g,payload,new Object());
  new atsi().call(payload,container);
  RelatedItemsProbe.stop();rec("reach_watch_s",controller,null,null,-1);
  List<JSONObject> rows=export();JSONObject summary=last(rows);
  check(count(rows,"run_start")==1&&count(rows,"writer_ready")==1,"run metadata");
  check(rows.get(0).getInt("schemaVersion")==2,"schema");
  check(count(rows,"related_predicate")==1,"upstream predicate preserved");
  check(count(rows,"controller")>=1,"controller preserved");
  check(count(rows,"sample_mark")==1,"marker preserved");
  check(summary.getBoolean("partial"),"pre-target loss marked");
  check(!summary.getBoolean("discoveryIncomplete"),"pre-target losses must not poison discovery window");
  JSONObject bulk=summary.getJSONObject("counters").getJSONObject("element_enter");
  check(bulk.getLong("attempted")==24564,"attempts");
  check(bulk.getLong("dropped")>0,"expected bounded pre-target loss");
  check(bulk.getLong("accepted")==bulk.getLong("emitted"),"writer drain");
  check(bulk.getLong("accepted")+bulk.getLong("dropped")==24564,"accounting");
  JSONObject edge=summary.getJSONObject("counters").getJSONObject("edge_d_2");
  check(edge.getLong("attempted")==5&&edge.getLong("accepted")==5&&edge.getLong("emitted")==5&&edge.getLong("dropped")==0,"5 edge_d_2 retained after feed budget");
  check(summary.getJSONObject("budget").getJSONObject("edge_d_2").getLong("accepted")==5,"edge reservation used");
  check(summary.getJSONObject("counters").getJSONObject("reach_watch_s").getLong("after_stop")==1,"after-stop accounting");
  check(summary.getJSONObject("counters").getJSONObject("controller").getLong("before_ready")==1,"pre-init accounting");
  JSONArray children=site(rows,"ghost_unlabelled").getJSONArray("children");
  check(children.length()==4&&children.getJSONObject(0).getInt("ghostType")==6,"actual enum");
  check(!children.getJSONObject(1).getBoolean("enumPresent")&&children.getJSONObject(1).isNull("ghostType"),"absent enum");
  check(children.getJSONObject(2).getInt("ghostType")==99&&"unknown".equals(children.getJSONObject(2).getString("ghostName")),"unknown enum");
  check(children.getJSONObject(3).getBoolean("compositePresent"),"mixed model");
  check(count(rows,"model_select")==6,"all optional branches");
  JSONObject selected=site(rows,"select_bero"),converted=site(rows,"host_f_result"),committed=site(rows,"host_f_commit");
  check(id(selected,"payload")==id(converted,"input"),"selection -> actual input identity");
  check(id(converted,"result")==id(committed,"result"),"actual output identity");
  check("committed".equals(committed.getString("state"))&&!committed.isNull("generation"),"actual commit");
  check("committed".equals(site(rows,"host_replace_commit").getString("state")),"replace commit");
  check("null_result".equals(site(rows,"host_f_null").getString("state")),"null converter");
  check(rows.stream().filter(j->"host_f_commit".equals(j.optString("site"))).count()==2,"distinct host commits");
  List<JSONObject> sinks=new ArrayList<>();for(JSONObject row:rows)if("bridge_sink_list".equals(row.optString("site")))sinks.add(row);
  check(sinks.size()==2&&sinks.get(0).isNull("inputChild")&&sinks.get(1).getLong("inputChild")==id(selected,"payload"),"same-frame sink identity only");
  long seq=0;for(JSONObject j:rows){long next=j.getLong("seq");check(next>seq,"seq order");seq=next;}
  System.out.println("PASS burst attempted="+bulk.getLong("attempted")+" accepted="+bulk.getLong("accepted")+" dropped="+bulk.getLong("dropped")+" emitted="+bulk.getLong("emitted")+"; edge_d_2=5/5; discoveryIncomplete=false");
  System.out.println("PASS ghost enum presence/unknown/mixed, six selection events, exact operand identity, host commit/null/replace, distinct hosts, callback alias rejection");
  RelatedItemsProbe.startRun(context,"watch_initial");waitReady();check(!first.equals(RelatedItemsProbe.runId()),"new run id");
  for(int i=0;i<12000;i++)rec("element_enter",generic,generic,generic,-1);
  List<JSONObject> next=export();JSONObject end=last(next);
  check(count(next,"run_start")==1&&count(next,"writer_ready")==1,"fresh run metadata");
  check(count(next,"controller")==1,"observed controller snapshot");
  check(end.getBoolean("discoveryIncomplete")&&end.getBoolean("decisionBlocked"),"discovery loss blocks decision");
  check(count(next,"model_select")==0,"old events do not leak");
  try(var files=Files.list(dir.resolve("related-items-runs"))){check(files.count()==2,"separate files");}
  System.out.println("PASS discovery loss blocked, restart/export, isolated run files and counters");
 }
}

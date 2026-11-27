import app.rikunode.morphe.extension.youtube.diagnostic.filters.RelatedItemsProbe;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;
// JVM-only test of actual recording/export code. No ART, geometry or real YouTube graph.
class lpu {}
class rqa {public lpu am=new lpu();}
class bggp {public int c=32;public String i="related-items";public List<Object> e=new ArrayList<>();}
class lakmn {public bggp a=new bggp();}
class ProbeRuntimeTest {
 static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
 static void rec(String site,Object a,Object b,Object c,int n){RelatedItemsProbe.record(site,a,b,c,null,null,null,n);}
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
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);rqa controller=new rqa();
  rec("controller",controller,controller.am,null,-1);
  Context context=new Context(dir.toFile());
  RelatedItemsProbe.initialize(context);waitReady();
  String first=RelatedItemsProbe.runId();
  Object generic=new Object();
  // Reported after-stop volume, injected here as a live synthetic bulk burst.
  for(int i=0;i<24563;i++)rec("element_enter",generic,generic,generic,-1);
  check(RelatedItemsProbe.status().contains("Recording: true"),"bulk overflow must not stop capture");
  rec("predicate",new lakmn(),null,null,1);
  rec("controller",controller,controller.am,null,-1);
  RelatedItemsProbe.mark("watch_initial");RelatedItemsProbe.stop();
  rec("reach_watch_s",controller,null,null,-1);
  List<JSONObject> rows=export();JSONObject summary=last(rows);
  check(count(rows,"run_start")==1&&count(rows,"writer_ready")==1,"run metadata");
  check(count(rows,"related_predicate")==1,"upstream predicate lost after burst");
  check(count(rows,"controller")>=1,"controller lost after burst");
  check(count(rows,"sample_mark")==1,"marker lost after burst");
  check(summary.getBoolean("partial"),"loss not marked partial");
  JSONObject bulk=summary.getJSONObject("counters").getJSONObject("element_enter");
  check(bulk.getLong("attempted")==24563,"attempts");
  check(bulk.getLong("dropped")>0,"expected bounded bulk drops");
  check(bulk.getLong("accepted")==bulk.getLong("emitted"),"writer drain");
  check(bulk.getLong("accepted")+bulk.getLong("dropped")==24563,"event accounting");
  check(summary.getJSONObject("counters").getJSONObject("reach_watch_s").getLong("after_stop")==1,"after-stop accounting");
  check(summary.getJSONObject("counters").getJSONObject("controller").getLong("before_ready")==1,"pre-init accounting");
  check(summary.getJSONObject("losses").length()>0,"loss intervals");
  System.out.println("PASS burst attempted="+bulk.getLong("attempted")+" accepted="+bulk.getLong("accepted")+" dropped="+bulk.getLong("dropped")+" emitted="+bulk.getLong("emitted"));
  RelatedItemsProbe.startRun(context,"watch_initial");waitReady();
  check(!first.equals(RelatedItemsProbe.runId()),"new run id");
  rec("predicate",new lakmn(),null,null,1);
  List<JSONObject> next=export();
  check(count(next,"run_start")==1&&count(next,"writer_ready")==1,"fresh run metadata");
  check(count(next,"controller")==1,"observed controller snapshot");
  check(count(next,"related_predicate")==1,"new run predicate");
  try(var files=Files.list(dir.resolve("related-items-runs"))){check(files.count()==2,"separate files");}
  System.out.println("PASS stop/restart/export, per-site counters, metadata, controller snapshot");
 }
}

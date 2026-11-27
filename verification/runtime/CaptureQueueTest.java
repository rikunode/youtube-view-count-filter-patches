package app.rikunode.morphe.extension.youtube.diagnostic.filters;
import java.util.*;
import java.util.concurrent.*;
class CaptureQueueTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static CaptureQueue.Item item(String site,int size,int lane){return new CaptureQueue.Item(site,new byte[size],lane);}
 public static void main(String[] args)throws Exception{
  CaptureQueue q=new CaptureQueue();
  for(int i=0;i<128;i++)check(q.offer(item("bulk"+i,1,CaptureQueue.BULK))==null,"pre-target accepted");
  check("queue_limit".equals(q.offer(item("overflow",1,CaptureQueue.BULK))),"pre-target cap");
  for(int i=0;i<576;i++)check(q.offer(item("discovery"+i,1,CaptureQueue.DISCOVERY))==null,"discovery reserved");
  check("queue_limit".equals(q.offer(item("overflow",1,CaptureQueue.DISCOVERY))),"discovery pending cap");
  for(int i=0;i<5;i++)check(q.offer(item("edge_d_2",1,CaptureQueue.DISCOVERY_EDGE))==null,"edge independent reservation");
  for(int i=0;i<768;i++)check(q.offer(item("correlated"+i,1,CaptureQueue.CORRELATED))==null,"correlated accepted");
  check("queue_limit".equals(q.offer(item("overflow",1,CaptureQueue.CORRELATED))),"correlated cap");
  check(q.offer(item("upstream",1,CaptureQueue.UPSTREAM))==null,"upstream reserved");
  check(q.offer(item("marker",1,CaptureQueue.CONTROL))==null,"marker reserved");
  q.close();int n=0;
  for(List<CaptureQueue.Item> b;(b=q.takeBatch()).size()>0;)for(var i:b){
   if(n<128)check(i.site.equals("bulk"+n),"FIFO pre");
   else if(n<704)check(i.site.equals("discovery"+(n-128)),"FIFO discovery");
   else if(n<709)check(i.site.equals("edge_d_2"),"FIFO edge");
   else if(n<1477)check(i.site.equals("correlated"+(n-709)),"FIFO correlated");
   else check(i.site.equals(n==1477?"upstream":"marker"),"FIFO marker");
   n++;
  }
  check(n==1479,"drain count");
  q=new CaptureQueue();
  for(int i=0;i<2000;i++){check(q.offer(item("pre",1,CaptureQueue.BULK))==null,"pre total");if(i%128==127)q.takeBatch();}
  check("lane_budget".equals(q.offer(item("overflow",1,CaptureQueue.BULK))),"pre cannot borrow");
  q.takeBatch();
  for(int i=0;i<8936;i++){check(q.offer(item("discover",1,CaptureQueue.DISCOVERY))==null,"discovery total");if(i%128==127)q.takeBatch();}
  check("lane_budget".equals(q.offer(item("overflow",1,CaptureQueue.DISCOVERY))),"discovery total cap");
  for(int i=0;i<5;i++)check(q.offer(item("edge_d_2",1,CaptureQueue.DISCOVERY_EDGE))==null,"5 critical edges after both quotas exhausted");
  long[][] usage=q.usage();
  check(usage[CaptureQueue.DISCOVERY_EDGE][4]==5,"edge usage");
  check(Arrays.stream(usage).mapToLong(v->v[0]).sum()==512+9000+9000+11000,"original count bound");
  check(Arrays.stream(usage).mapToLong(v->v[1]).sum()==(512L+5*1024+5*1024+4*1024)*1024,"original byte bound");
  q=new CaptureQueue();check("line_limit".equals(q.offer(item("large",CaptureQueue.MAX_LINE+1,CaptureQueue.UPSTREAM))),"line cap");
  for(int i=0;i<16;i++)check(q.offer(item("bytes",CaptureQueue.MAX_LINE,CaptureQueue.UPSTREAM))==null,"pending bytes");
  check("queue_limit".equals(q.offer(item("bytes",1,CaptureQueue.UPSTREAM))),"pending bytes cap");
  q.takeBatch();
  for(int i=0;i<24;i++){check(q.offer(item("bytes",CaptureQueue.MAX_LINE,CaptureQueue.UPSTREAM))==null,"total bytes");q.takeBatch();}
  check("lane_budget".equals(q.offer(item("bytes",1,CaptureQueue.UPSTREAM))),"total bytes cap");
  CaptureQueue wake=new CaptureQueue();ExecutorService exec=Executors.newSingleThreadExecutor();
  try{Future<List<CaptureQueue.Item>> f=exec.submit(wake::takeBatch);
   wake.offer(item("wake",1,CaptureQueue.UPSTREAM));check(f.get(2,TimeUnit.SECONDS).size()==1,"event wake");
   wake.close();check(wake.takeBatch().isEmpty(),"close wake");
  }finally{exec.shutdownNow();}
  System.out.println("PASS pre-target + discovery + edge reservation, original limits, FIFO, event wake, byte/line caps");
 }
}

package app.rikunode.morphe.extension.youtube.diagnostic.filters;
import java.util.*;
import java.util.concurrent.*;
class CaptureQueueTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static CaptureQueue.Item item(String site,int size,int lane){return new CaptureQueue.Item(site,new byte[size],lane);}
 public static void main(String[] args)throws Exception{
  CaptureQueue q=new CaptureQueue();
  for(int i=0;i<768;i++)check(q.offer(item("bulk"+i,1,CaptureQueue.BULK))==null,"bulk accepted");
  check("queue_limit".equals(q.offer(item("overflow",1,CaptureQueue.BULK))),"bulk cap");
  for(int i=0;i<768;i++)check(q.offer(item("correlated"+i,1,CaptureQueue.CORRELATED))==null,"correlated accepted");
  check("queue_limit".equals(q.offer(item("overflow",1,CaptureQueue.CORRELATED))),"correlated cap");
  check(q.offer(item("upstream",1,CaptureQueue.UPSTREAM))==null,"upstream reserved");
  check(q.offer(item("marker",1,CaptureQueue.CONTROL))==null,"marker reserved");
  q.close();int n=0;
  for(List<CaptureQueue.Item> b;(b=q.takeBatch()).size()>0;)for(var i:b){
   if(n<768)check(i.site.equals("bulk"+n),"global FIFO bulk");
   else if(n<1536)check(i.site.equals("correlated"+(n-768)),"global FIFO correlated");
   else check(i.site.equals(n==1536?"upstream":"marker"),"global FIFO priority");
   n++;
  }
  check(n==1538,"drain count");
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
  System.out.println("PASS FIFO, reserved lanes, event wake, pending/total byte and line limits");
 }
}

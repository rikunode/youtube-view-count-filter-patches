package app.rikunode.morphe.extension.youtube.diagnostic.filters;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Non-blocking producers, event-driven FIFO consumer. All six quotas sum to
 * the previous four-lane limits. A prior feed cannot borrow discovery space. */
final class CaptureQueue {
    static final int CONTROL=0, UPSTREAM=1, CORRELATED=2, BULK=3, DISCOVERY=4, DISCOVERY_EDGE=5;
    static final String[] NAMES={"control","upstream","correlated","pre_target","discovery","edge_d_2"};
    static final int MAX_LINE=128*1024;
    static final long MAX_FILE=16L*1024*1024;
    private static final int[] PENDING={64,768,768,128,576,64};
    private static final long[] PENDING_BYTES={256*1024,2*1024*1024,2*1024*1024,256*1024,1728*1024,64*1024};
    private static final int[] TOTAL={512,9000,9000,2000,8936,64};
    private static final long[] TOTAL_BYTES={512*1024,5*1024*1024,5*1024*1024,512*1024,3520*1024,64*1024};
    static final class Item {
        final String site;
        final byte[] bytes;
        final int lane;
        Item(String site,byte[] bytes,int lane){this.site=site;this.bytes=bytes;this.lane=lane;}
    }
    private final ArrayDeque<Item> queue=new ArrayDeque<>();
    private final int[] pending=new int[NAMES.length], total=new int[NAMES.length];
    private final long[] pendingBytes=new long[NAMES.length], totalBytes=new long[NAMES.length];
    private boolean closed;
    synchronized String offer(Item item) {
        int lane=item.lane, size=item.bytes.length;
        if(closed)return "closed";
        if(size>MAX_LINE)return "line_limit";
        if(total[lane]>=TOTAL[lane] || totalBytes[lane]+size>TOTAL_BYTES[lane])return "lane_budget";
        if(pending[lane]>=PENDING[lane] || pendingBytes[lane]+size>PENDING_BYTES[lane])return "queue_limit";
        queue.addLast(item);pending[lane]++;pendingBytes[lane]+=size;
        total[lane]++;totalBytes[lane]+=size;notifyAll();return null;
    }
    synchronized List<Item> takeBatch() throws InterruptedException {
        while(queue.isEmpty()&&!closed)wait();
        List<Item> out=new ArrayList<>(Math.min(queue.size(),128));
        while(!queue.isEmpty()&&out.size()<128){
            Item i=queue.removeFirst();pending[i.lane]--;pendingBytes[i.lane]-=i.bytes.length;out.add(i);
        }
        return out;
    }
    synchronized boolean preTargetExhausted(){
        return total[BULK]>=TOTAL[BULK]||totalBytes[BULK]>=TOTAL_BYTES[BULK]-1024;
    }
    synchronized long[][] usage(){
        long[][] out=new long[NAMES.length][];
        for(int i=0;i<NAMES.length;i++)out[i]=new long[]{TOTAL[i],TOTAL_BYTES[i],PENDING[i],PENDING_BYTES[i],total[i],totalBytes[i],pending[i],pendingBytes[i]};
        return out;
    }
    synchronized void close(){closed=true;notifyAll();}
    synchronized List<Item> abort(){
        closed=true;List<Item> out=new ArrayList<>(queue);queue.clear();notifyAll();return out;
    }
}

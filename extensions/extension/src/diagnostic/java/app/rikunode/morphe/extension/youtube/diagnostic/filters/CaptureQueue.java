package app.rikunode.morphe.extension.youtube.diagnostic.filters;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Non-blocking producers, blocking batch consumer. FIFO across reserved lanes.
 * A 256-event shared queue previously stopped both runs during the first 90/234ms.
 * Keep independent quotas: bulk cannot consume the upstream/control reserve.
 */
final class CaptureQueue {
    static final int CONTROL=0, UPSTREAM=1, CORRELATED=2, BULK=3;
    static final int MAX_LINE=128*1024;
    static final long MAX_FILE=16L*1024*1024;
    // Leave 1 MiB for final coverage/counter/loss summaries, even after a full capture.
    private static final int[] PENDING={64,768,768,768};
    private static final long[] PENDING_BYTES={256*1024,2*1024*1024,2*1024*1024,2*1024*1024};
    private static final int[] TOTAL={512,9000,9000,11000};
    private static final long[] TOTAL_BYTES={512*1024,5*1024*1024,5*1024*1024,4*1024*1024};
    static final class Item {
        final String site;
        final byte[] bytes;
        final int lane;
        Item(String site,byte[] bytes,int lane){this.site=site;this.bytes=bytes;this.lane=lane;}
    }
    private final ArrayDeque<Item> queue=new ArrayDeque<>();
    private final int[] pending=new int[4], total=new int[4];
    private final long[] pendingBytes=new long[4], totalBytes=new long[4];
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
    synchronized void close(){closed=true;notifyAll();}
    synchronized List<Item> abort(){
        closed=true;List<Item> out=new ArrayList<>(queue);queue.clear();notifyAll();return out;
    }
}

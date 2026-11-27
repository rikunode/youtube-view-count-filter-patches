package app.rikunode.morphe.extension.youtube.diagnostic.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import app.rikunode.morphe.extension.youtube.diagnostic.filters.RelatedItemsProbe;
import java.io.OutputStream;

final class RelatedItemsProbeUI {
    static final int EXPORT=10421;
    private RelatedItemsProbeUI(){}
    static void show(Activity activity){
        ScrollView scroll=new ScrollView(activity);
        LinearLayout column=new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(28,30,28,28);scroll.addView(column);
        TextView title=new TextView(activity);title.setText("Related items diagnostic probe");
        title.setTextSize(21);column.addView(title);
        TextView status=new TextView(activity);status.setTextIsSelectable(true);
        status.setText(RelatedItemsProbe.status());column.addView(status);
        TextView help=new TextView(activity);
        help.setText("Stop the previous run and export it first. Start a new run, refresh until Writer ready: true, then reopen playback (including controller recreation). Sample labels are notes, not classification. PARTIAL means evidence is incomplete.");
        column.addView(help);
        String[] samples={"watch_initial","watch_continuation","watch_reopen","watch_metadata",
            "watch_comments","home","search","subscriptions","playlist","watch_playlist","player_retained","unlabelled"};
        Spinner picker=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,samples);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);column.addView(picker);
        Button refresh=new Button(activity);refresh.setText("Refresh status");
        refresh.setOnClickListener(v->status.setText(RelatedItemsProbe.status()));column.addView(refresh);
        Button start=new Button(activity);start.setText("Start new run");
        start.setOnClickListener(v->{RelatedItemsProbe.startRun(activity,(String)picker.getSelectedItem());
            status.setText(RelatedItemsProbe.status());});column.addView(start);
        Button mark=new Button(activity);mark.setText("Mark sample");
        mark.setOnClickListener(v->{RelatedItemsProbe.mark((String)picker.getSelectedItem());
            status.setText(RelatedItemsProbe.status());});column.addView(mark);
        Button stop=new Button(activity);stop.setText("Stop");
        stop.setOnClickListener(v->{RelatedItemsProbe.stop();status.setText(RelatedItemsProbe.status());});column.addView(stop);
        Button export=new Button(activity);export.setText("Export current run (JSONL)");
        export.setOnClickListener(v->{
            RelatedItemsProbe.stop();status.setText(RelatedItemsProbe.status());
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/x-ndjson");
            intent.putExtra(Intent.EXTRA_TITLE,"related-items-"+RelatedItemsProbe.runId()+".jsonl");
            try{activity.startActivityForResult(intent,EXPORT);}
            catch(Exception ex){Toast.makeText(activity,"Cannot open export destination",Toast.LENGTH_LONG).show();}
        });column.addView(export);
        activity.setContentView(scroll);
    }
    static void export(Activity activity,Uri uri){
        new Thread(()->{
            boolean ok=false;
            try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"w")){
                if(out!=null){RelatedItemsProbe.export(out);ok=true;}
            }catch(Throwable ignored){}
            final boolean result=ok;
            activity.runOnUiThread(()->Toast.makeText(activity,
                result?"JSONL exported":"JSONL export failed",Toast.LENGTH_LONG).show());
        },"related-probe-export").start();
    }
}

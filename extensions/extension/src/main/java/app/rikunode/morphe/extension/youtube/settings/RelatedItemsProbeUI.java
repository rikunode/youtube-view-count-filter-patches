package app.rikunode.morphe.extension.youtube.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import app.rikunode.morphe.extension.youtube.filters.RelatedItemsProbe;
import java.io.OutputStream;

final class RelatedItemsProbeUI {
    static final int EXPORT = 10421;
    private RelatedItemsProbeUI() {}

    static void show(Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(28,30,28,28);
        scroll.addView(column);
        TextView title = new TextView(activity);
        title.setText("関連動画 記録専用プローブ / Related items probe");
        title.setTextSize(21);
        column.addView(title);
        TextView status = new TextView(activity);
        status.setText(RelatedItemsProbe.status());
        status.setTextIsSelectable(true);
        column.addView(status);
        TextView instructions=new TextView(activity);
        instructions.setText("画面ラベルは採取メモのみです。表示行の陽性・陰性は別途確認してください。採取後は停止して JSONL を書き出してください。新しい run はアプリ再起動時に開始します。");
        column.addView(instructions);
        String[] samples={"watch_initial","watch_continuation","watch_reopen","watch_metadata",
                "watch_comments","home","search","subscriptions","playlist","watch_playlist","player_retained"};
        Spinner picker=new Spinner(activity);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,samples);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        picker.setAdapter(adapter);
        column.addView(picker);
        Button mark=new Button(activity);
        mark.setText("採取ラベルを記録 / Mark sample");
        mark.setOnClickListener(view -> {
            RelatedItemsProbe.mark((String)picker.getSelectedItem());
            status.setText(RelatedItemsProbe.status());
        });
        column.addView(mark);
        Button stop=new Button(activity);
        stop.setText("採取停止 / Stop");
        stop.setOnClickListener(view -> {
            RelatedItemsProbe.stop();
            status.setText(RelatedItemsProbe.status());
        });
        column.addView(stop);
        Button export=new Button(activity);
        export.setText("JSONL 書き出し / Export");
        export.setOnClickListener(view -> {
            RelatedItemsProbe.stop();
            status.setText(RelatedItemsProbe.status());
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/x-ndjson");
            intent.putExtra(Intent.EXTRA_TITLE,"related-items-probe.jsonl");
            try { activity.startActivityForResult(intent,EXPORT); }
            catch(Exception ex) { Toast.makeText(activity,"書き出し先を開けません",Toast.LENGTH_LONG).show(); }
        });
        column.addView(export);
        activity.setContentView(scroll);
    }

    static void export(Activity activity,Uri uri) {
        new Thread(() -> {
            boolean ok=false;
            try(OutputStream out=activity.getContentResolver().openOutputStream(uri,"w")){
                if(out!=null){RelatedItemsProbe.export(out);ok=true;}
            }catch(Throwable ignored){}
            final boolean result=ok;
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    result?"JSONL 書き出し完了":"JSONL 書き出し失敗",Toast.LENGTH_LONG).show());
        },"related-probe-export").start();
    }
}

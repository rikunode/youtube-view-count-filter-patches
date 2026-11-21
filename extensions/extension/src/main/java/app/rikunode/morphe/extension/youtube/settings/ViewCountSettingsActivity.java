package app.rikunode.morphe.extension.youtube.settings;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import app.rikunode.morphe.extension.youtube.filters.ViewCountFilter;
import app.rikunode.morphe.extension.youtube.filters.ViewCountProbe;

public final class ViewCountSettingsActivity extends Activity {
    private SharedPreferences preferences;
    private EditText lower;
    private EditText upper;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(ViewCountFilter.PREF_FILE, MODE_PRIVATE);
        ViewCountFilter.initialize(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setPadding(28, 30, 28, 28);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        TextView heading = new TextView(this);
        heading.setText("再生回数フィルタ / View count filter");
        heading.setTextSize(21);
        column.addView(heading);

        toggle(column, "ホーム / Home", "rikunode_viewcount_home");
        toggle(column, "検索 / Search", "rikunode_viewcount_search");
        toggle(column, "登録チャンネル / Subscriptions", "rikunode_viewcount_subscriptions");
        lower = number(column, "この回数未満を非表示 / Hide below",
                preferences.getLong("rikunode_viewcount_less_than", 1000L));
        upper = number(column, "この回数超過を非表示 / Hide above",
                preferences.getLong("rikunode_viewcount_greater_than", 1_000_000_000_000L));

        Button save = new Button(this);
        save.setText("保存 / Save");
        save.setOnClickListener((View view) -> {
            try {
                long low = Long.parseLong(lower.getText().toString().trim());
                long high = Long.parseLong(upper.getText().toString().trim());
                if (low < 0 || high < 0 || low > high) throw new NumberFormatException();
                preferences.edit().putLong("rikunode_viewcount_less_than", low)
                        .putLong("rikunode_viewcount_greater_than", high).apply();
                Toast.makeText(this, "保存しました / Saved", Toast.LENGTH_SHORT).show();
                finish();
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "0以上、下限 ≤ 上限の整数を指定してください", Toast.LENGTH_LONG).show();
            }
        });
        column.addView(save);

        TextView probeHeading = new TextView(this);
        probeHeading.setText("診断 / Probe");
        probeHeading.setTextSize(19);
        column.addView(probeHeading);
        TextView probeLog = new TextView(this);
        probeLog.setTextIsSelectable(true);
        probeLog.setText(ViewCountProbe.report(this));
        column.addView(probeLog);

        Button copy = new Button(this);
        copy.setText("コピー / Copy");
        copy.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("View count probe", ViewCountProbe.report(this)));
                Toast.makeText(this, "コピーしました / Copied", Toast.LENGTH_SHORT).show();
            }
        });
        column.addView(copy);
        Button clear = new Button(this);
        clear.setText("消去 / Clear");
        clear.setOnClickListener(view -> {
            ViewCountProbe.clear(this);
            probeLog.setText(ViewCountProbe.report(this));
        });
        column.addView(clear);
        setContentView(scroll);
    }

    private void toggle(LinearLayout column, String label, String key) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setChecked(preferences.getBoolean(key, false));
        box.setOnCheckedChangeListener((button, checked) ->
                preferences.edit().putBoolean(key, checked).apply());
        column.addView(box);
    }

    private EditText number(LinearLayout column, String label, long value) {
        TextView title = new TextView(this);
        title.setText(label);
        column.addView(title);
        EditText entry = new EditText(this);
        entry.setInputType(InputType.TYPE_CLASS_NUMBER);
        entry.setSingleLine(true);
        entry.setText(Long.toString(value));
        column.addView(entry);
        return entry;
    }
}

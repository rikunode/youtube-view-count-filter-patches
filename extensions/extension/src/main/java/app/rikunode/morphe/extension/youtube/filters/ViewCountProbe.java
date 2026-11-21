package app.rikunode.morphe.extension.youtube.filters;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, diagnostic-only sampling of the exact wls.k() value seen by the filter. */
public final class ViewCountProbe {
    private static final String KEY = "rikunode_viewcount_probe_v1";
    private static final int MAX_PATHS = 96;
    private static final int MAX_CHARS = 200;
    private static final int MAX_COUNT = 1_000_000_000;
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
    private static boolean loaded;
    private static int overflow;

    private ViewCountProbe() {}

    private static void load(SharedPreferences prefs) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject saved = new JSONObject(prefs.getString(KEY, "{}"));
            overflow = Math.max(0, saved.optInt("overflow", 0));
            JSONArray entries = saved.optJSONArray("paths");
            if (entries == null) return;
            for (int i = 0; i < Math.min(entries.length(), MAX_PATHS); i++) {
                JSONArray entry = entries.optJSONArray(i);
                if (entry == null) continue;
                String path = entry.optString(0, "");
                if (path.length() > MAX_CHARS) continue;
                counts.put(path, Math.min(MAX_COUNT, Math.max(0, entry.optInt(1, 0))));
            }
        } catch (Throwable ignored) { counts.clear(); overflow = 0; }
    }

    private static void save(SharedPreferences prefs) throws Exception {
        JSONArray paths = new JSONArray();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            JSONArray row = new JSONArray();
            row.put(e.getKey());
            row.put(e.getValue());
            paths.put(row);
        }
        JSONObject root = new JSONObject();
        root.put("paths", paths);
        root.put("overflow", overflow);
        prefs.edit().putString(KEY, root.toString()).apply();
    }

    public static void record(Context context, Object rawPath) {
        try {
            if (context == null) return;
            String path = rawPath == null ? "<null>" : rawPath.toString();
            if (path.length() > MAX_CHARS) path = path.substring(0, MAX_CHARS) + "…";
            SharedPreferences prefs = context.getSharedPreferences(ViewCountFilter.PREF_FILE, Context.MODE_PRIVATE);
            synchronized (LOCK) {
                load(prefs);
                Integer old = counts.get(path);
                if (old != null) counts.put(path, Math.min(MAX_COUNT, old + 1));
                else if (counts.size() < MAX_PATHS) counts.put(path, 1);
                else overflow = Math.min(MAX_COUNT, overflow + 1);
                save(prefs);
            }
        } catch (Throwable ignored) { /* Never affect the filter. */ }
    }

    public static String report(Context context) {
        synchronized (LOCK) {
            try {
                load(context.getSharedPreferences(ViewCountFilter.PREF_FILE, Context.MODE_PRIVATE));
                StringBuilder out = new StringBuilder("Probe: wls.k() identifiers (max 96 distinct, 200 chars each)\n");
                for (Map.Entry<String, Integer> e : counts.entrySet())
                    out.append(e.getValue()).append(" ×  ").append(e.getKey()).append('\n');
                out.append("Overflow samples: ").append(overflow).append('\n');
                return out.toString();
            } catch (Throwable ignored) { return "Probe data unavailable"; }
        }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            counts.clear();
            overflow = 0;
            loaded = true;
            try {
                context.getSharedPreferences(ViewCountFilter.PREF_FILE, Context.MODE_PRIVATE)
                        .edit().remove(KEY).apply();
            } catch (Throwable ignored) { }
        }
    }
}
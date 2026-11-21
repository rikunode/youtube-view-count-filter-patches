package app.rikunode.morphe.extension.youtube.filters;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only probe v2. One-level, bounded, content-based converter context snapshot. */
public final class ViewCountProbe {
    private static final String KEY = "rikunode_viewcount_probe_v2";
    private static final int MAX_ROWS = 96;
    private static final int MAX_FIELDS = 16;
    private static final int MAX_PATH = 140;
    private static final int MAX_VALUE = 72;
    private static final int MAX_CONTEXT = 520;
    private static final int MAX_COUNT = 1_000_000_000;
    private static final Object LOCK = new Object();
    private static final LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
    private static boolean loaded;
    private static int overflow;

    private ViewCountProbe() {}

    private static String cut(String s, int max) {
        if (s == null) return "<null>";
        s = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String simpleValue(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?>)
            return cut(String.valueOf(value), MAX_VALUE);
        // Do not stringify arbitrary objects: their toString/identity hashes are unstable.
        return "<" + value.getClass().getName() + ">";
    }

    private static String fingerprint(Object xcv) {
        if (xcv == null) return "<null>";
        StringBuilder out = new StringBuilder(xcv.getClass().getName());
        try {
            Field[] fields = xcv.getClass().getDeclaredFields();
            Arrays.sort(fields, (a, b) -> a.getName().compareTo(b.getName()));
            int seen = 0;
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                if (++seen > MAX_FIELDS) { out.append(";more-fields"); break; }
                out.append(';').append(f.getName()).append('=');
                try {
                    f.setAccessible(true);
                    out.append(simpleValue(f.get(xcv)));
                } catch (Throwable ignored) { out.append("<inaccessible>"); }
                if (out.length() >= MAX_CONTEXT) break;
            }
        } catch (Throwable ignored) { out.append(";<reflection-unavailable>"); }
        return cut(out.toString(), MAX_CONTEXT);
    }

    private static void load(SharedPreferences prefs) {
        if (loaded) return;
        loaded = true;
        try {
            JSONObject saved = new JSONObject(prefs.getString(KEY, "{}"));
            overflow = Math.max(0, saved.optInt("overflow", 0));
            JSONArray rows = saved.optJSONArray("rows");
            if (rows == null) return;
            for (int i = 0; i < Math.min(rows.length(), MAX_ROWS); i++) {
                JSONArray row = rows.optJSONArray(i);
                if (row == null) continue;
                String key = row.optString(0, "");
                if (key.length() > 850) continue;
                counts.put(key, Math.min(MAX_COUNT, Math.max(0, row.optInt(1, 0))));
            }
        } catch (Throwable ignored) { counts.clear(); overflow = 0; }
    }

    private static void save(SharedPreferences prefs) throws Exception {
        JSONArray rows = new JSONArray();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            JSONArray row = new JSONArray();
            row.put(e.getKey());
            row.put(e.getValue());
            rows.put(row);
        }
        JSONObject root = new JSONObject();
        root.put("rows", rows);
        root.put("overflow", overflow);
        prefs.edit().putString(KEY, root.toString()).apply();
    }

    public static void recordV2(Context context, Object rawPath, Object xcv, Object list, Object xds) {
        try {
            if (context == null) return;
            String path = cut(rawPath == null ? "<null>" : String.valueOf(rawPath), MAX_PATH);
            String contextPrint = fingerprint(xcv);
            String listPrint;
            if (list instanceof List<?>) {
                List<?> items = (List<?>) list;
                Object first = items.isEmpty() ? null : items.get(0);
                listPrint = "size=" + items.size() + ";first="
                        + (first == null ? "<none>" : first.getClass().getName());
            } else {
                listPrint = list == null ? "null" : "not-List:" + list.getClass().getName();
            }
            String xdsName = xds == null ? "<null>" : xds.getClass().getName();
            String key = "id=" + path + " | xcv=" + contextPrint
                    + " | list=" + cut(listPrint, 140) + " | xds=" + cut(xdsName, 110);
            SharedPreferences prefs = context.getSharedPreferences(ViewCountFilter.PREF_FILE, Context.MODE_PRIVATE);
            synchronized (LOCK) {
                load(prefs);
                Integer old = counts.get(key);
                if (old != null) counts.put(key, Math.min(MAX_COUNT, old + 1));
                else if (counts.size() < MAX_ROWS) counts.put(key, 1);
                else overflow = Math.min(MAX_COUNT, overflow + 1);
                save(prefs);
            }
        } catch (Throwable ignored) { /* Probing must never affect filter behavior. */ }
    }

    public static String report(Context context) {
        synchronized (LOCK) {
            try {
                load(context.getSharedPreferences(ViewCountFilter.PREF_FILE, Context.MODE_PRIVATE));
                StringBuilder out = new StringBuilder("Probe v2: identifier + Lxcv fields (1 level) + List + Lxds\n");
                out.append("Bounded to 96 distinct combinations; counts by content\n");
                for (Map.Entry<String, Integer> e : counts.entrySet())
                    out.append(e.getValue()).append(" x ").append(e.getKey()).append('\n');
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
package app.rikunode.morphe.extension.youtube.filters;

import android.content.Context;
import android.content.SharedPreferences;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ViewCountFilter {
    private static volatile Context application;
    public static final String PREF_FILE = "rikunode_viewcount";

    private ViewCountFilter() {}

    public static void initialize(Context context) {
        if (context != null) application = context.getApplicationContext();
    }

    public static void probe() { /* Legacy marker; not a filter. */ }

    /**
     * Fail-open hook for the APK-side Litho component converter.
     * All conversion and cleanup is performed by the host before this is called.
     */
    public static boolean shouldDrop(Object component) {
        try {
            Context app = application;
            if (app == null || component == null) return false;
            SharedPreferences prefs = app.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
            Method pathMethod = component.getClass().getMethod("k");
            Object rawPath = pathMethod.invoke(component);
            try { ViewCountProbe.record(app, rawPath); } catch (Throwable ignored) { }
            String path = rawPath == null ? "" : rawPath.toString().toLowerCase(Locale.ROOT);

            String key;
            if (path.contains("search")) key = "rikunode_viewcount_search";
            else if (path.contains("subscription")) key = "rikunode_viewcount_subscriptions";
            else if (path.contains("home") || path.contains("feed")) key = "rikunode_viewcount_home";
            else return false;

            if (!prefs.getBoolean(key, false)) return false;

            Object bytes = component.getClass().getMethod("f").invoke(component);
            if (!(bytes instanceof byte[])) return false;
            long count = ViewCountParser.parse(new String((byte[]) bytes, StandardCharsets.UTF_8));
            if (count < 0) return false;
            long less = prefs.getLong("rikunode_viewcount_less_than", 1000L);
            long greater = prefs.getLong("rikunode_viewcount_greater_than", 1_000_000_000_000L);
            return count < less || count > greater;
        } catch (Throwable ignored) {
            return false;
        }
    }
}

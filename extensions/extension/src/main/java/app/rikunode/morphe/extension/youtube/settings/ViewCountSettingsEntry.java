package app.rikunode.morphe.extension.youtube.settings;

import android.content.Context;
import android.content.Intent;
import app.rikunode.morphe.extension.youtube.filters.ViewCountFilter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ViewCountSettingsEntry {
    private ViewCountSettingsEntry() {}

    /** Installs an ordinary preference on the APK's own PreferenceScreen. */
    public static void install(Object screen) {
        if (screen == null) return;
        try {
            Method getContext = screen.getClass().getMethod("getContext");
            Context context = (Context) getContext.invoke(screen);
            if (context == null) return;
            ViewCountFilter.initialize(context);

            ClassLoader loader = screen.getClass().getClassLoader();
            Class<?> preferenceClass = Class.forName("androidx.preference.Preference", false, loader);
            Method find = screen.getClass().getMethod("findPreference", CharSequence.class);
            if (find.invoke(screen, "rikunode_viewcount_settings") != null) return;

            Constructor<?> constructor = preferenceClass.getConstructor(Context.class);
            Object preference = constructor.newInstance(context);
            preferenceClass.getMethod("setKey", String.class).invoke(preference, "rikunode_viewcount_settings");
            preferenceClass.getMethod("setTitle", CharSequence.class)
                    .invoke(preference, "View count filter / 再生回数フィルタ");
            preferenceClass.getMethod("setSummary", CharSequence.class)
                    .invoke(preference, "独立した再生回数フィルタの設定");

            Class<?> listener = Class.forName(
                    "androidx.preference.Preference$OnPreferenceClickListener", false, loader);
            Object click = Proxy.newProxyInstance(loader, new Class<?>[]{listener},
                    (proxy, method, args) -> {
                        if ("onPreferenceClick".equals(method.getName())) {
                            context.startActivity(new Intent(context, ViewCountSettingsActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            return true;
                        }
                        return false;
                    });
            preferenceClass.getMethod("setOnPreferenceClickListener", listener).invoke(preference, click);
            screen.getClass().getMethod("addPreference", preferenceClass).invoke(screen, preference);
        } catch (Throwable ignored) {
            // If this YouTube build changes its settings internals, do not crash the app.
        }
    }
}

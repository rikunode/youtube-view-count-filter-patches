package app.rikunode.morphe.extension.youtube.settings;

import android.content.Context;
import android.content.Intent;
import app.rikunode.morphe.extension.youtube.filters.ViewCountFilter;

public final class ViewCountSettingsEntry {
    private ViewCountSettingsEntry() {}

    /** Installs an ordinary preference on the APK's own PreferenceScreen. */
    public static void install(Object screen) {
        if (screen == null) return;
        try {
            // YouTube 21.30.216 keeps androidx.preference class names but obfuscates its API.
            ClassLoader loader = screen.getClass().getClassLoader();
            Class<?> preferenceClass = Class.forName("androidx.preference.Preference", false, loader);
            Class<?> groupClass = Class.forName("androidx.preference.PreferenceGroup", false, loader);
            Context context = (Context) preferenceClass.getField("j").get(screen);
            if (context == null) return;
            ViewCountFilter.initialize(context);
            // Official Morphe merges our add-on XML into morphe_prefs. Avoid
            // showing a duplicate entry on YouTube's own settings screen.
            if (context.getResources().getIdentifier(
                    "morphe_prefs", "xml", context.getPackageName()) != 0) return;

            // PreferenceGroup.l(CharSequence) = findPreference on this exact build.
            if (groupClass.getMethod("l", CharSequence.class)
                    .invoke(screen, "rikunode_viewcount_settings") != null) return;
            Object preference = preferenceClass.getConstructor(Context.class).newInstance(context);
            preferenceClass.getMethod("L", String.class)
                    .invoke(preference, "rikunode_viewcount_settings");
            preferenceClass.getMethod("Q", CharSequence.class)
                    .invoke(preference, "View count filter / 再生回数フィルタ");
            preferenceClass.getMethod("n", CharSequence.class)
                    .invoke(preference, "独立した再生回数フィルタの設定");

            // Preference.E() invokes the Intent in field s when the row is clicked.
            Intent intent = new Intent(context, ViewCountSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            preferenceClass.getField("s").set(preference, intent);
            // PreferenceGroup.ai(Preference) = addPreference on this exact build.
            groupClass.getMethod("ai", preferenceClass).invoke(screen, preference);
        } catch (Throwable ignored) {
            // If this YouTube build changes its settings internals, do not crash the app.
        }
    }
}

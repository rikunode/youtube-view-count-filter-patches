package app.rikunode.morphe.extension.youtube.diagnostic.settings;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import app.rikunode.morphe.extension.youtube.diagnostic.filters.RelatedItemsProbe;

/** Only the separate diagnostic patch registers this provider. */
public final class RelatedItemsProbeProvider extends ContentProvider {
    @Override public boolean onCreate() {
        RelatedItemsProbe.initialize(getContext());
        return true;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String order) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}

package app.rikunode.morphe.extension.youtube.diagnostic.settings;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
public final class RelatedItemsProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        RelatedItemsProbeUI.show(this);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if(request==RelatedItemsProbeUI.EXPORT && result==RESULT_OK && data!=null && data.getData()!=null)
            RelatedItemsProbeUI.export(this,data.getData());
    }
}

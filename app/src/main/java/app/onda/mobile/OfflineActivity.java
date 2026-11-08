package app.onda.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Legacy entry point kept for compatibility (notification taps and any saved
 * intents from older versions). The player is now a screen inside
 * MainActivity, so this simply forwards there and finishes.
 */
public final class OfflineActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_player", true);
        startActivity(intent);
        finish();
    }
}

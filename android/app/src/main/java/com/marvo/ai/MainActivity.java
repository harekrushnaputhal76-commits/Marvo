package com.marvo.ai;

import android.os.Bundle;
import android.util.Log;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MarvoDownload";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MarvoNativeBridge.class);
        super.onCreate(savedInstanceState);

        Log.d(TAG, "MainActivity onCreate: Initializing OfflineBrainDownloader...");
        try {
            OfflineBrainDownloader.getInstance().startDownload(this, false);
        } catch (Exception e) {
            Log.e(TAG, "MainActivity: Error initializing OfflineBrainDownloader: " + e.getMessage(), e);
        }
        // Wake-Word permanently disabled: 100% Zero background battery drain
    }
}

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

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "MainActivity onPause: halting background threads and pausing webview timers for 0% battery drain");
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().onPause();
                getBridge().getWebView().pauseTimers();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error pausing bridge webview: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity onResume: resuming webview timers");
        try {
            if (getBridge() != null && getBridge().getWebView() != null) {
                getBridge().getWebView().onResume();
                getBridge().getWebView().resumeTimers();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error resuming bridge webview: " + e.getMessage());
        }
    }
}

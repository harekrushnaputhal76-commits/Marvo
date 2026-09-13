package com.marvo.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = "MarvoAssistant";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Marvo Assistant Triggered via Hardware Button!");

        // Finish activity after 2 seconds for now (UI will be added later)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    finish();
                }
            }
        }, 2000);
    }
}

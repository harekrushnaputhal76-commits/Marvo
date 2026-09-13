package com.marvo.ai;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class AssistantActivity extends AppCompatActivity {
    private static final String TAG = "MarvoAssistant";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.slide_up_assistant, 0);
        setContentView(R.layout.activity_assistant);
        Log.d(TAG, "Marvo Assistant Triggered via Hardware Button!");

        // Tap outside bottom sheet to dismiss
        View rootLayout = findViewById(R.id.assistantRootLayout);
        if (rootLayout != null) {
            rootLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
        }

        View bottomSheet = findViewById(R.id.bottomSheetContainer);
        if (bottomSheet != null) {
            bottomSheet.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Consume click so tapping the sheet itself does not dismiss
                }
            });
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_down_assistant);
    }
}

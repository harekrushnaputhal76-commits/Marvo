package com.marvo.ai;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MarvoNativeBridge.class);
        super.onCreate(savedInstanceState);
        // Wake-Word permanently disabled: 100% Zero background battery drain
    }
}

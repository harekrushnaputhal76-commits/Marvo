package com.marvo.ai;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/**
 * TiltEngine — Native Android equivalent (SENSOR_DELAY_UI, lifecycle-aware, LERP).
 * Frame-rate independent exponential smoothing with strict battery preservation (stops on background).
 */
public class TiltEngine implements SensorEventListener, DefaultLifecycleObserver {

    public interface OnTiltUpdateListener {
        void onUpdate(float pitch, float roll);
    }

    private final SensorManager sensorManager;
    private final Sensor rotationSensor;
    private final float maxTilt;
    private final float lerpSpeed;
    private final OnTiltUpdateListener onUpdate;

    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    private volatile float targetX = 0f;
    private volatile float targetY = 0f;
    private float currentX = 0f;
    private float currentY = 0f;
    private long lastTs = 0L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;

    public TiltEngine(Context context, float maxTilt, float lerpSpeed, OnTiltUpdateListener onUpdate) {
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = this.sensorManager != null ? this.sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) : null;
        if (sensor == null && this.sensorManager != null) {
            sensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }
        this.rotationSensor = sensor;
        this.maxTilt = maxTilt > 0 ? maxTilt : 16f;
        this.lerpSpeed = lerpSpeed > 0 ? lerpSpeed : 6f;
        this.onUpdate = onUpdate;
    }

    public TiltEngine(Context context, OnTiltUpdateListener onUpdate) {
        this(context, 16f, 6f, onUpdate);
    }

    private float clamp(float v, float m) {
        if (v < -m) return -m;
        if (v > m) return m;
        return v;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null) return;
        try {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            float pitchDeg = (float) Math.toDegrees(orientation[1]);
            float rollDeg = (float) Math.toDegrees(orientation[2]);
            targetX = clamp(pitchDeg, maxTilt);
            targetY = clamp(rollDeg, maxTilt);
        } catch (Exception ignored) {}
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private final Runnable frameRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = System.nanoTime();
            float dt = (lastTs == 0L) ? 0f : (now - lastTs) / 1_000_000_000f;
            lastTs = now;
            float alpha = 1f - (float) Math.exp(-lerpSpeed * dt);
            currentX += (targetX - currentX) * alpha;
            currentY += (targetY - currentY) * alpha;
            if (onUpdate != null) {
                onUpdate.onUpdate(currentX, currentY);
            }
            handler.postDelayed(this, 33); // ~30fps throttle
        }
    };

    public synchronized void start() {
        if (running) return;
        running = true;
        lastTs = 0L;
        if (sensorManager != null && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI);
        }
        handler.post(frameRunnable);
    }

    public synchronized void stop() {
        running = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        handler.removeCallbacks(frameRunnable);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        start();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        stop();
    }
}


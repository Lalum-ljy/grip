package com.example.grip;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GyroscopeActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor linearAccelerationSensor;
    private Sensor orientationSensor;

    private TextView tvLinearX, tvLinearY, tvLinearZ;
    private TextView tvOrientAzimuth, tvOrientPitch, tvOrientRoll;

    private Button btnStart, btnStop, btnSimulateCall;
    private boolean isSensorRunning = false;

    private TextView tvBaseRoll;
    private TextView tvDetectionResult;
    private TextView tvDetectionResult2;
    private TextView tvFinalResult;
    private float baseRollValue = 0.0f;
    private float[] linearAccelValues = new float[3];
    private float[] prevLinearAccelValues = new float[3];
    private float[] orientationValues = new float[3];
    private java.util.ArrayList<Float> recentRollValues = new java.util.ArrayList<>();
    private java.util.ArrayList<Long> recentTimeStamps = new java.util.ArrayList<>();
    private static final float ACCELERATION_THRESHOLD = 0.4f;
    private static final long STABLE_DURATION_MS = 750;
    private static final float ROLL_THRESHOLD = 1.1f;
    private static final float ACCELERATION_CHANGE_THRESHOLD = 1.25f;
    private static final float ACCELERATION_LARGE_CHANGE_THRESHOLD = 4.0f;
    private static final long ACCELERATION_CHECK_INTERVAL_MS = 700;
    private static final long BASE_VALUE_UPDATE_INTERVAL_MS = 1200;
    private static final float EMA_ALPHA = 0.2f; // 平滑系数（0~1，越小越平滑）
    private float[] filteredRoll = {0.0f}; // 滤波后的Roll值
    private float[] filteredLinearAccel = new float[3]; // 滤波后的线性加速度
    private String detectedHand = null;
    private String detectedHand2 = null;
    private long lastAccelerationCheckTime = 0;
    private long lastBaseValueUpdateTime = 0;
    private float[] prevAccelCheckValues = new float[3];
    private Integer algorithm2FirstValue = null;
    private long sensorStartTime = 0;
    private android.view.WindowManager windowManager;
    private android.view.View callPopupView;
    private android.view.WindowManager.LayoutParams popupLayoutParams;
    private boolean isPopupShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gyroscope);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        tvLinearX = findViewById(R.id.tvLinearX);
        tvLinearY = findViewById(R.id.tvLinearY);
        tvLinearZ = findViewById(R.id.tvLinearZ);
        tvOrientAzimuth = findViewById(R.id.tvOrientAzimuth);
        tvOrientPitch = findViewById(R.id.tvOrientPitch);
        tvOrientRoll = findViewById(R.id.tvOrientRoll);
        tvBaseRoll = findViewById(R.id.tvBaseRoll);
        tvDetectionResult = findViewById(R.id.tvDetectionResult);
        tvDetectionResult2 = findViewById(R.id.tvDetectionResult2);
        tvFinalResult = findViewById(R.id.tvFinalResult);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnSimulateCall = findViewById(R.id.btnSimulateCall);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        orientationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSensors();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopSensors();
            }
        });

        btnSimulateCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                simulateIncomingCall();
            }
        });
    }

    private void startSensors() {
        if (!isSensorRunning) {
            if (linearAccelerationSensor != null) {
                sensorManager.registerListener(this, linearAccelerationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (orientationSensor != null) {
                sensorManager.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }

            isSensorRunning = true;
            sensorStartTime = System.currentTimeMillis();
        }
    }

    private void stopSensors() {
        if (isSensorRunning) {
            sensorManager.unregisterListener(this);
            isSensorRunning = false;
            detectedHand = null;
            detectedHand2 = null;
            lastAccelerationCheckTime = 0;
            lastBaseValueUpdateTime = 0;
            recentRollValues.clear();
            recentTimeStamps.clear();
            filteredRoll[0] = 0.0f;
            algorithm2FirstValue = null;
            for (int i = 0; i < 3; i++) {
                prevAccelCheckValues[i] = 0;
                filteredLinearAccel[i] = 0;
            }
            tvDetectionResult.setText("计算中");
            tvDetectionResult2.setText("计算中");
            tvFinalResult.setText("计算中");
            hidePopup();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSensors();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_LINEAR_ACCELERATION:
                for (int i = 0; i < 3; i++) {
                    prevLinearAccelValues[i] = linearAccelValues[i];
                    linearAccelValues[i] = event.values[i];
                    // 加速度滤波
                    filteredLinearAccel[i] = EMA_ALPHA * event.values[i] + (1 - EMA_ALPHA) * filteredLinearAccel[i];
                }
                // UI可选择显示滤波后的值
                tvLinearX.setText(String.format("X: %.4f (滤波后: %.4f)", linearAccelValues[0], filteredLinearAccel[0]));
                tvLinearY.setText(String.format("Y: %.4f (滤波后: %.4f)", linearAccelValues[1], filteredLinearAccel[1]));
                tvLinearZ.setText(String.format("Z: %.4f (滤波后: %.4f)", linearAccelValues[2], filteredLinearAccel[2]));
                break;

            case Sensor.TYPE_ORIENTATION:
                // 原始值赋值
                orientationValues[0] = event.values[0];
                orientationValues[1] = event.values[1];
                orientationValues[2] = event.values[2];
                
                // 对Roll值做指数移动平均滤波
                filteredRoll[0] = EMA_ALPHA * event.values[2] + (1 - EMA_ALPHA) * filteredRoll[0];
                
                // UI显示改用滤波后的值（也可保留原始值对比）
                tvOrientAzimuth.setText(String.format("Azimuth (方位角): %.2f", orientationValues[0]));
                tvOrientPitch.setText(String.format("Pitch (俯仰角): %.2f", orientationValues[1]));
                tvOrientRoll.setText(String.format("Roll (翻滚角): %.2f (滤波后: %.2f)", orientationValues[2], filteredRoll[0]));
                break;
        }

        long currentTime = System.currentTimeMillis();
        recentRollValues.add(filteredRoll[0]);
        recentTimeStamps.add(currentTime);

        for (int i = 0; i < recentTimeStamps.size(); i++) {
            if (currentTime - recentTimeStamps.get(i) > STABLE_DURATION_MS) {
                recentTimeStamps.remove(i);
                recentRollValues.remove(i);
                i--;
            }
        }

        if (currentTime - lastBaseValueUpdateTime > BASE_VALUE_UPDATE_INTERVAL_MS) {
            calculateBaseValue();
            lastBaseValueUpdateTime = currentTime;
        }
        updateDetectionResult();
        updateDetectionResult2();
        updateFinalResult();
        tvBaseRoll.setText(String.format("Roll基准值: %.2f", baseRollValue));
    }

    private void updateDetectionResult() {
        if (!isSensorRunning) {
            tvDetectionResult.setText("计算中");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAccelerationCheckTime > ACCELERATION_CHECK_INTERVAL_MS) {
            boolean accelerationChanged = false;
            boolean largeAccelerationChange = false;
            for (int i = 0; i < 3; i++) {
                float change = Math.abs(linearAccelValues[i] - prevAccelCheckValues[i]);
                if (change > ACCELERATION_CHANGE_THRESHOLD) {
                    accelerationChanged = true;
                }
                if (change > ACCELERATION_LARGE_CHANGE_THRESHOLD) {
                    largeAccelerationChange = true;
                    break;
                }
            }

            if (accelerationChanged && !largeAccelerationChange) {
                detectedHand = null;
            }

            for (int i = 0; i < 3; i++) {
                prevAccelCheckValues[i] = linearAccelValues[i];
            }
            lastAccelerationCheckTime = currentTime;
        }

        if (detectedHand == null) {
            float currentRoll = filteredRoll[0];

            if (currentRoll > baseRollValue + ROLL_THRESHOLD) {
                detectedHand = "左手";
            } else if (currentRoll < baseRollValue - ROLL_THRESHOLD) {
                detectedHand = "右手";
            } else {
                tvDetectionResult.setText("检测中");
                return;
            }
        }

        tvDetectionResult.setText(detectedHand);
    }

    private void updateDetectionResult2() {
        if (!isSensorRunning) {
            tvDetectionResult2.setText("计算中");
            return;
        }

        float currentLinearX = linearAccelValues[0];

        if (algorithm2FirstValue == null) {
            if (currentLinearX > 1.0f) {
                algorithm2FirstValue = 0;
            } else if (currentLinearX < -1.0f) {
                algorithm2FirstValue = 1;
            }
        } else {
            if (algorithm2FirstValue == 0 && currentLinearX < -1.0f) {
                detectedHand2 = "右手";
                algorithm2FirstValue = null;
            } else if (algorithm2FirstValue == 1 && currentLinearX > 1.0f) {
                detectedHand2 = "左手";
                algorithm2FirstValue = null;
            }
        }

        if (detectedHand2 != null) {
            tvDetectionResult2.setText(detectedHand2);
        }
    }

    private void updateFinalResult() {
        if (!isSensorRunning) {
            tvFinalResult.setText("计算中");
            return;
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceStart = currentTime - sensorStartTime;

        if (timeSinceStart < 1000) {
            if (detectedHand != null) {
                tvFinalResult.setText(detectedHand);
            } else {
                tvFinalResult.setText("检测中");
            }
        } else {
            if (detectedHand != null && detectedHand2 != null) {
                if (detectedHand.equals("左手") && detectedHand2.equals("左手")) {
                    tvFinalResult.setText("左手");
                } else if (detectedHand.equals("右手") && detectedHand2.equals("右手")) {
                    tvFinalResult.setText("右手");
                } else {
                    tvFinalResult.setText("检测中");
                }
            } else {
                tvFinalResult.setText("检测中");
            }
        }

        updatePopupPosition();
    }

    private void calculateBaseValue() {
        if (!isSensorRunning) return;

        long currentTime = System.currentTimeMillis();

        if (recentTimeStamps.size() > 0) {
            float sum = 0.0f;
            for (Float roll : recentRollValues) {
                sum += roll;
            }
            baseRollValue = sum / recentRollValues.size();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void simulateIncomingCall() {
        if (isPopupShowing) {
            return;
        }

        if (!hasOverlayPermission()) {
            showPermissionDialog();
            return;
        }

        windowManager = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
        callPopupView = getLayoutInflater().inflate(R.layout.popup_call, null);

        popupLayoutParams = new android.view.WindowManager.LayoutParams(
                1080,
                480,
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
        );

        popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
        popupLayoutParams.x = 0;
        popupLayoutParams.y = 50;

        callPopupView.findViewById(R.id.btnReject).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                stopSensors();
            }
        });

        callPopupView.findViewById(R.id.btnAccept).setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startSensors();
            }
        });

        try {
            windowManager.addView(callPopupView, popupLayoutParams);
            isPopupShowing = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        updatePopupPosition();
    }

    private boolean hasOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            return android.provider.Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void showPermissionDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("权限请求")
                .setMessage("需要'显示在其他应用之上'权限来显示来电模拟悬浮窗")
                .setPositiveButton("去设置", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, 1001);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001) {
            if (hasOverlayPermission()) {
                simulateIncomingCall();
            }
        }
    }

    private void hidePopup() {
        if (isPopupShowing && callPopupView != null) {
            try {
                windowManager.removeView(callPopupView);
                isPopupShowing = false;
                callPopupView = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String currentPopupMode = null;

    private void updatePopupPosition() {
        if (!isPopupShowing || callPopupView == null) {
            return;
        }

        String finalResult = tvFinalResult.getText().toString();
        String newMode = "center";
        
        if (finalResult.equals("左手")) {
            newMode = "left";
        } else if (finalResult.equals("右手")) {
            newMode = "right";
        }
        
        // 只有当模式发生变化时才切换布局
        if (!newMode.equals(currentPopupMode)) {
            try {
                windowManager.removeView(callPopupView);
                
                if (newMode.equals("left") || newMode.equals("right")) {
                    // 侧边布局
                    callPopupView = getLayoutInflater().inflate(R.layout.popup_call_side, null);
                    
                    // 设置按钮点击事件
                    callPopupView.findViewById(R.id.btnReject).setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            stopSensors();
                        }
                    });

                    callPopupView.findViewById(R.id.btnAccept).setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            startSensors();
                        }
                    });
                    
                    // 更新布局参数
                    popupLayoutParams.width = 540;
                    popupLayoutParams.height = 540;
                    
                    if (newMode.equals("left")) {
                        popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
                        popupLayoutParams.x = 20;
                        popupLayoutParams.y = 450;
                    } else {
                        popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
                        popupLayoutParams.x = -20;
                        popupLayoutParams.y = 450;
                    }
                } else {
                    // 中央布局
                    callPopupView = getLayoutInflater().inflate(R.layout.popup_call, null);
                    
                    // 设置按钮点击事件
                    callPopupView.findViewById(R.id.btnReject).setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            stopSensors();
                        }
                    });

                    callPopupView.findViewById(R.id.btnAccept).setOnClickListener(new android.view.View.OnClickListener() {
                        @Override
                        public void onClick(android.view.View v) {
                            startSensors();
                        }
                    });
                    
                    // 更新布局参数
                    popupLayoutParams.width = 1080;
                    popupLayoutParams.height = 480;
                    popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                    popupLayoutParams.x = 0;
                    popupLayoutParams.y = 50;
                }
                
                windowManager.addView(callPopupView, popupLayoutParams);
                currentPopupMode = newMode;
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (newMode.equals("left") || newMode.equals("right")) {
            // 同一侧模式，只更新位置参数
            popupLayoutParams.width = 540;
            popupLayoutParams.height = 540;
            
            if (newMode.equals("left")) {
                popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
                popupLayoutParams.x = 20;
                popupLayoutParams.y = 450;
            } else {
                popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.RIGHT;
                popupLayoutParams.x = -20;
                popupLayoutParams.y = 450;
            }
            
            windowManager.updateViewLayout(callPopupView, popupLayoutParams);
        } else {
            // 中央模式，只更新位置参数
            popupLayoutParams.width = 1080;
            popupLayoutParams.height = 480;
            popupLayoutParams.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
            popupLayoutParams.x = 0;
            popupLayoutParams.y = 50;
            
            windowManager.updateViewLayout(callPopupView, popupLayoutParams);
        }
    }
}

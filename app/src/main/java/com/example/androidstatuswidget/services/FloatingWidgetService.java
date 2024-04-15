package com.example.androidstatuswidget.services;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.example.androidstatuswidget.R;

import java.util.Objects;


public class FloatingWidgetService extends Service {
    private static final String TAG = "FloatingWidgetService";
    private static final int REQUEST_CODE_OVERLAY_PERMISSION = 1001;
    private static final String ACTION_MANAGE_OVERLAY_PERMISSION =
            "com.example.androidstatuswidget.ACTION_MANAGE_OVERLAY_PERMISSION";
    private WindowManager mWindowManager;
    private View mFloatingWidget;
    private TextView batteryTextView;
    private TextView batteryPercentageTextView;
    private TextView batteryLifeTextView;
    private TextView networkTextView;
    private TextView operatorTextView;
    private TelephonyManager telephonyManager;
    private OverlayPermissionReceiver overlayPermissionReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate()");
        overlayPermissionReceiver = new OverlayPermissionReceiver();
        IntentFilter filter = new IntentFilter(ACTION_MANAGE_OVERLAY_PERMISSION);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY); // Set priority if needed
        registerReceiver(overlayPermissionReceiver, filter, null, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (!Settings.canDrawOverlays(getApplicationContext())) {
                requestOverlayPermission();
            } else {
                createFloatingWidget();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, Objects.requireNonNull(e.getMessage()));
            stopSelf(); // Stop the service if an error occurs
        }
        return START_STICKY;
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void createFloatingWidget()
    {
        // Inflate the floating widget layout
        mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        batteryPercentageTextView = mFloatingWidget.findViewById(R.id.batteryPercentageTextView);
        batteryTextView = mFloatingWidget.findViewById(R.id.batteryTextView);
        batteryLifeTextView = mFloatingWidget.findViewById(R.id.batteryLifeTextView);
        networkTextView = mFloatingWidget.findViewById(R.id.networkTextView);
        operatorTextView = mFloatingWidget.findViewById(R.id.operatorTextView);

        // Get system services
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // Add phone state listener to get network and operator info
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        // Update battery info
        updateBatteryInfo();

        // Add the view to the window manager
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        mWindowManager.addView(mFloatingWidget, params);
        Log.d(TAG, "Service started");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (mFloatingWidget != null && mWindowManager != null) {
                mWindowManager.removeView(mFloatingWidget);
            }
            // Remove phone state listener
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            unregisterReceiver(overlayPermissionReceiver);
            Log.d(TAG, "Service destroyed");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // PhoneStateListener to update network and operator info
    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            super.onServiceStateChanged(serviceState);
            updateNetworkInfo();
        }

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            updateNetworkInfo();
        }
    };

    // Method to update battery info
    @SuppressLint("SetTextI18n")
    private void updateBatteryInfo() {
        Intent batteryIntent = getApplicationContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
        int batteryLife = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        String batteryHealthText;
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                batteryHealthText = "Good";
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                batteryHealthText = "Dead";
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                batteryHealthText = "Overheat";
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                batteryHealthText = "Over Voltage";
                break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                batteryHealthText = "Unspecified Failure";
                break;
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            default:
                batteryHealthText = "Unknown";
        }

        batteryTextView.setText("Battery Health: " + batteryHealthText);
        batteryPercentageTextView.setText("Battery Percentage: " + ((level * 100) / scale) + "%");
        batteryLifeTextView.setText("Battery Life: " + batteryLife + " mV");
    }

    // Method to update network info
    private void updateNetworkInfo() {
        String networkInfo = "";
        // Network strength
        if (telephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM) {
            // For GSM phones, signal strength is measured in dBm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int level = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    level = telephonyManager.getSignalStrength().getLevel();
                }
                int dBm = -113 + 2 * level;
                networkInfo += "Signal Strength: " + dBm + " dBm";
            } else {
                // For older devices without dBm support, you can use getLevel() method
                // to get the signal strength in bars
                int level = telephonyManager.getSignalStrength().getLevel();
                networkInfo += "Signal Strength: " + level + " bars";
            }
        } else {
            // For CDMA phones, signal strength is measured in dBm
            int dBm = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                dBm = telephonyManager.getSignalStrength().getCdmaDbm();
            }
            networkInfo += "Signal Strength: " + dBm + " dBm";
        }

        // Network operator
        String operatorName = telephonyManager.getNetworkOperatorName();

        networkTextView.setText(networkInfo);
        operatorTextView.setText("Network Operator: " + operatorName);
    }

    private class OverlayPermissionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Settings.canDrawOverlays(getApplicationContext())) {
                createFloatingWidget();
            } else {
                Log.e(TAG, "Overlay permission not granted");
                Toast.makeText(context, "Overlay permission not granted", Toast.LENGTH_SHORT).show();
                stopSelf();
            }
        }
    }
}

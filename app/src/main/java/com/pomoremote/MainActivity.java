package com.pomoremote;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pomoremote.service.PomodoroService;
import com.pomoremote.timer.TimerState;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private PomodoroService service;
    private boolean isBound = false;
    
    private TextView tvTimer;
    private TextView tvPhase;
    private TextView tvStatus;
    private TextView tvProgress;
    private Button btnToggle;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            PomodoroService.LocalBinder localBinder = (PomodoroService.LocalBinder) binder;
            service = localBinder.getService();
            isBound = true;
            updateUI(service.getCurrentState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isBound && service != null) {
                updateUI(service.getCurrentState());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTimer = findViewById(R.id.tvTimer);
        tvPhase = findViewById(R.id.tvPhase);
        tvStatus = findViewById(R.id.tvConnectionStatus);
        tvProgress = findViewById(R.id.tvProgress);
        btnToggle = findViewById(R.id.btnToggle);
        
        findViewById(R.id.btnSettings).setOnClickListener(v -> 
            startActivity(new Intent(this, SettingsActivity.class)));
            
        btnToggle.setOnClickListener(v -> {
            if (isBound && service != null) service.toggleTimer();
        });
        
        findViewById(R.id.btnSkip).setOnClickListener(v -> {
            if (isBound && service != null) service.skipTimer();
        });
        
        findViewById(R.id.btnReset).setOnClickListener(v -> {
            if (isBound && service != null) service.resetTimer();
        });

        startService();
        requestNotificationPermission();
    }

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1;

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - notifications will work
            } else {
                // Permission denied - optionally inform user about limited functionality
            }
        }
    }

    private void startService() {
        Intent intent = new Intent(this, PomodoroService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, new IntentFilter("com.pomoremote.STATE_UPDATE"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateReceiver, new IntentFilter("com.pomoremote.STATE_UPDATE"));
        }
        
        if (isBound && service != null) {
            service.connect();
            updateUI(service.getCurrentState());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(stateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

    private void updateUI(TimerState state) {
        if (state == null) return;

        int minutes = (int) state.remaining / 60;
        int seconds = (int) state.remaining % 60;
        tvTimer.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));

        String phaseName = state.phase;
        if (TimerState.PHASE_WORK.equals(state.phase)) phaseName = "Focus";
        else if (TimerState.PHASE_SHORT.equals(state.phase)) phaseName = "Short Break";
        else if (TimerState.PHASE_LONG.equals(state.phase)) phaseName = "Long Break";
        tvPhase.setText(phaseName);
        
        tvProgress.setText("Completed: " + state.completed);

        if (TimerState.STATUS_RUNNING.equals(state.status)) {
            btnToggle.setText("Pause");
        } else {
            btnToggle.setText("Start");
        }
        
        if (service != null) {
            if (service.isConnected()) {
                tvStatus.setText("Connected");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            } else {
                tvStatus.setText("Offline");
                tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_offline));
            }
        }
    }
}

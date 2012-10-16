package org.appanalysis;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Intent;
import android.content.Context;
import android.util.Log;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class TaintDroidNotifyController extends Activity {
    private static final String TAG = TaintDroidNotifyController.class.getSimpleName();
    
    private static Button startButton;
    private static Button stopButton;

    private OnClickListener onClickStartButton = new OnClickListener() {
        public void onClick(View v) {
            Intent i = new Intent(getApplicationContext(), TaintDroidNotifyService.class);
            startService(i);
            updateButtonState();
        }
    };

    private OnClickListener onClickStopButton = new OnClickListener() {
        public void onClick(View v) {
            Intent i = new Intent(getApplicationContext(), TaintDroidNotifyService.class);
            stopService(i);
            updateButtonState();
        }
    };
    
    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (service.service.getClassName().equals(TaintDroidNotifyService.class.getName())) {
                return true;
            }
        }
        return false;
    }
    
    private void updateButtonState() {
        if (isServiceRunning()) {
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
        } else {
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        }
    }
    
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        
        if(hasFocus)
            updateButtonState();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control);
        
        boolean running = isServiceRunning();

        startButton = (Button) findViewById(R.id.StartButton);
        startButton.setOnClickListener(onClickStartButton);
        
        stopButton = (Button) findViewById(R.id.StopButton);
        stopButton.setOnClickListener(onClickStopButton);
        
        updateButtonState();
    }
}

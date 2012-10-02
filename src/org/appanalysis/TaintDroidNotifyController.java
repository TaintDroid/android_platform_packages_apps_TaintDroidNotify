package org.appanalysis;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class TaintDroidNotifyController extends Activity {
    private static final String TAG = TaintDroidNotifyController.class.getSimpleName();

    private OnClickListener onClickStartButton = new OnClickListener() {
        public void onClick(View v) {
            Intent i = new Intent(getApplicationContext(), TaintDroidNotifyService.class);
            startService(i);
        }
    };

    private OnClickListener onClickStopButton = new OnClickListener() {
        public void onClick(View v) {
            Intent i = new Intent(getApplicationContext(), TaintDroidNotifyService.class);
            stopService(i);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.control);

        Button b = (Button) findViewById(R.id.StartButton);
        b.setOnClickListener(onClickStartButton);
        b.setEnabled(true);

        b = (Button) findViewById(R.id.StopButton);
        b.setOnClickListener(onClickStopButton);
        b.setEnabled(true);
    }
}

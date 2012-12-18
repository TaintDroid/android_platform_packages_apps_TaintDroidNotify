package org.appanalysis;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class TaintDroidNotifyDetail extends Activity {

    private void setInfo() {
        Bundle b = getIntent().getExtras();

        String appname = b.getString(TaintDroidNotifyService.KEY_APPNAME);
        String dest = b.getString(TaintDroidNotifyService.KEY_DEST);
        String taint = b.getString(TaintDroidNotifyService.KEY_TAINT);
        String data = b.getString(TaintDroidNotifyService.KEY_DATA);
        int id = b.getInt(TaintDroidNotifyService.KEY_ID);
        String timestamp = b.getString(TaintDroidNotifyService.KEY_TIMESTAMP);

        TextView tv = (TextView) findViewById(R.id.DetailAppTextView);
        tv.setText(appname);

        tv = (TextView) findViewById(R.id.DetailDestTextView);
        tv.setText(dest);

        tv = (TextView) findViewById(R.id.DetailTaintTextView);
        tv.setText(taint);

        tv = (TextView) findViewById(R.id.DetailDataTextView);
        tv.setText(data);
        
        tv = (TextView) findViewById(R.id.DetailTimestampTextView);
        tv.setText(timestamp);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.detail);
        setInfo();
    }
}

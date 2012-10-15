package org.appanalysis;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.*;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;

public class TaintDroidNotifyService extends Service {
    private static final String TAG = TaintDroidNotifyService.class.getSimpleName();

    private static Hashtable<Integer, String> ttable = new Hashtable<Integer, String>();
    static {
        // ttable.put(new Integer(0x00000000), "No taint");
        ttable.put(new Integer(0x00000001), "Location");
        ttable.put(new Integer(0x00000002), "Address Book (ContactsProvider)");
        ttable.put(new Integer(0x00000004), "Microphone Input");
        ttable.put(new Integer(0x00000008), "Phone Number");
        ttable.put(new Integer(0x00000010), "GPS Location");
        ttable.put(new Integer(0x00000020), "NET-based Location");
        ttable.put(new Integer(0x00000040), "Last known Location");
        ttable.put(new Integer(0x00000080), "camera");
        ttable.put(new Integer(0x00000100), "accelerometer");
        ttable.put(new Integer(0x00000200), "SMS");
        ttable.put(new Integer(0x00000400), "IMEI");
        ttable.put(new Integer(0x00000800), "IMSI");
        ttable.put(new Integer(0x00001000), "ICCID (SIM card identifier)");
        ttable.put(new Integer(0x00002000), "Device serial number");
        ttable.put(new Integer(0x00004000), "User account information");
        ttable.put(new Integer(0x00008000), "browser history");
    }

    private volatile static boolean isRunning = false;

    public static final String KEY_APPNAME = "KEY_APPNAME";
    public static final String KEY_IPADDRESS = "KEY_IPADDRESS";
    public static final String KEY_TAINT = "KEY_TAINT";
    public static final String KEY_DATA = "KEY_DATA";
    public static final String KEY_ID = "KEY_ID";
    public static final String KEY_TIMESTAMP = "KEY_TIMESTAMP";
    
    private BlockingQueue logQueue;
    private static final int LOGQUEUE_MAXSIZE = 4096;

    private volatile boolean doCapture = false;
    private Thread captureThread = null;


    public static class Starter extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(!isRunning && intent.getAction() != null) {
                if(intent.getAction().equals(Intent.ACTION_USER_PRESENT)) {
                    context.startService(new Intent(context, TaintDroidNotifyService.class));
                }
            }
        }
    };

    private class Producer implements Runnable {
    	private final BlockingQueue queue;
    	Producer(BlockingQueue q) { queue = q; }
        public void run() {
            LogcatDevice lc = LogcatDevice.getInstance();
            while(doCapture && lc.isOpen()) {
                try {
                    // read an entry and insert it to our content provider
                    LogEntry le = lc.readLogEntry();
                    if(le != null) {
                        queue.put(le);
                    }
                }
                catch(Exception e) {
                    Log.e(TAG, "Could not read a log entry: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    };

    private volatile boolean doRead = false;
    private Thread readThread = null;
    private class Consumer implements Runnable {
    	private final BlockingQueue queue;
    	Consumer(BlockingQueue q) { queue = q; }
        public void run()
        {
            LogEntry prev = null;
            while(doRead) {
            	try {
            		LogEntry le = (LogEntry)queue.take();
                    processLogEntry(le);
            	}
            	catch (InterruptedException e) {
            		Log.e(TAG, "Could not read log entry: " + e.getMessage());
            	}
            }
        }
    }

    private String get_processname(int pid) {
        ActivityManager mgr = (ActivityManager) getApplicationContext().getSystemService(
            Context.ACTIVITY_SERVICE);

        String pname = "";
        List<RunningAppProcessInfo> apps = mgr.getRunningAppProcesses();
        for(RunningAppProcessInfo pinfo : apps) {
            if(pinfo.pid == pid) {
                pname = pinfo.processName;
                break;
            }
        }

        return pname;
    }

    private String get_ipaddress(String msg) {
    	Pattern p = Pattern.compile("\\((.+)\\) ");
        Matcher m = p.matcher(msg);

        if(m.find() && m.groupCount() > 0) {
        	String result = m.group(1);
        	// remove trailing junk
        	if (result.contains(")"))
        		result = result.substring(0,result.indexOf(")")-1);
            return result;
        }
        else {
            return null;
        }
    }

    private String get_taint(String msg) {
    	// match hex digits
    	Pattern p = Pattern.compile("with tag 0x(\\p{XDigit}+) ");
        Matcher m = p.matcher(msg);

        if(m.find() && m.groupCount() > 0) {

            String match = m.group(1);

            // get back int
            int taint;
            try {
                taint = Integer.parseInt(match, 16);
            }
            catch(NumberFormatException e) {
                return "Unknown Taint: " + match;
            }

            if(taint == 0x0) {
                return "No taint";
            }

            // for each taint
            ArrayList<String> list = new ArrayList<String>();
            int t;
            String tag;
            
            // check each bit
            for (int i=0; i<32; i++) {
            	t = (taint>>i) & 0x1;
            	tag = ttable.get(new Integer(t<<i));
                if(tag != null) {
                    list.add(tag);
                }
            }

            // build output
            StringBuilder sb = new StringBuilder("");
            if(list.size() > 1) {
                for(int i = 0; i < list.size() - 1; i++) {
                    sb.append(list.get(i) + ", ");
                }
                sb.append(list.get(list.size() - 1));
            }
            else {
                if(!list.isEmpty()) {
                    sb.append(list.get(0));
                }
            }

            return sb.toString();
        }
        else {
            return "No Taint Found";
        }
    }

    private String get_data(String msg) {
        int start = msg.indexOf("data=[") + 6;
        return msg.substring(start);
    }

	private int noti_id = 0;

    private void sendTaintDroidNotification(int id, String ipaddress, String taint, String appname, String data, String timestamp) {
        Notification notification = new Notification.BigTextStyle(
        new Notification.Builder(this)
        .setContentTitle("TaintDroid")
        .setContentText(appname)
        .setSmallIcon(R.drawable.icon))
        .bigText(appname+"\n"+ipaddress+"\n"+taint)
        .build();

        // set intent to launch detail
        Bundle extras = new Bundle();
        extras.putString(KEY_APPNAME, appname);
        extras.putString(KEY_IPADDRESS, ipaddress);
        extras.putString(KEY_TAINT, taint);
        extras.putString(KEY_DATA, data);
        extras.putInt(KEY_ID, id);
        extras.putString(KEY_TIMESTAMP, timestamp);

        Intent i = new Intent(this, TaintDroidNotifyDetail.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.putExtras(extras);

        PendingIntent pi = PendingIntent.getActivity(this, id, i, PendingIntent.FLAG_UPDATE_CURRENT);
        notification.contentIntent = pi;

        // set led
        notification.ledOnMS = 500;
        notification.ledOffMS = 500;
        notification.ledARGB = 0x00ff0000;
        notification.flags |= Notification.FLAG_SHOW_LIGHTS;

        // send it
        NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mgr.notify(noti_id, notification);
		noti_id++;
    }

    private boolean isTaintedSend(String msg) {
        // covers "libcore.os.send" and "libcore.os.sendto"
        return msg.contains("libcore.os.send");
    }
    
    private boolean isTaintedSSLSend(String msg) {
    	return msg.contains("SSLOutputStream.write");
    }

    private void processLogEntry(LogEntry le) {
		String timestamp = le.getTimestamp();
		String msg = le.getMessage(); 
        boolean taintedSend = isTaintedSend(msg);
        boolean taintedSSLSend = isTaintedSSLSend(msg);
        if(taintedSend || taintedSSLSend) {
            String ip = get_ipaddress(msg);
            String taint = get_taint(msg);
            String app = get_processname(le.getPid());
            String data = get_data(msg);
            if (taintedSSLSend)
            	ip=ip+" (SSL)";

            sendTaintDroidNotification(le.hashCode(), ip, taint, app, data, timestamp);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // we don't bind to this service
        return null;
    }

    @Override
    public void onCreate() {
        return;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(isRunning) {
            return START_STICKY;
        }

        logQueue = new ArrayBlockingQueue<LogEntry>(LOGQUEUE_MAXSIZE);
        this.captureThread = new Thread(new Producer(logQueue));
        captureThread.setDaemon(true);

        this.readThread = new Thread(new Consumer(logQueue));
        readThread.setDaemon(true);

        try {
            LogcatDevice.getInstance().open();
        }
        catch(IOException e) {
            Log.e(TAG, "Could not open the log device: " + e.getMessage());
            return START_STICKY;
        }

        this.doCapture = true;
        captureThread.start();

        this.doRead = true;
        readThread.start();

        isRunning = true;

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // stop the thread
        this.doCapture = false;
        this.doRead = false;

        // close the log
        try {
            LogcatDevice.getInstance().close();
        }
        catch(IOException e) {
            Log.e(TAG, "Could not close the log device properly: " + e.getMessage());
        }

        // destroy the thread
        this.captureThread.interrupt();
        this.readThread.interrupt();
        this.captureThread = null;
        this.readThread = null;
        
        isRunning = false;
    }
}

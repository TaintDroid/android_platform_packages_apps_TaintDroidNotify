package org.appanalysis;

import android.util.Log;
import java.util.Date;

public class LogEntry {
    private static final String LOGTAG = LogEntry.class.getSimpleName();

    private String timestamp;
    private int pid;
    private String tag;
    private String message;

    private LogEntry() {}
    
    public static LogEntry fromLine(String line) {
        String[] tokens = line.split("\\s+");
        
        // skip over "--------- beginning of /dev/log/system" etc
        if (tokens[0].equals("---------"))
            return null;
        
        LogEntry le = new LogEntry();
        
        // "MM-dd HH:mm:ss.SSS"
        le.timestamp = tokens[0]+" "+tokens[1];
        
        // should be "TaintLog"
        le.tag = tokens[2].substring(tokens[2].indexOf("/"),tokens[2].indexOf("("));
        
        // pid
        le.pid = Integer.valueOf((line.substring(line.indexOf("(")+1,line.indexOf(")"))).trim());
        
        // data
        int messageStart = line.indexOf("): ");
        int messageEnd = line.lastIndexOf("]");
        le.message = line.substring(messageStart, messageEnd);
        
        return le;
    }

    public String getTimestamp() {
        return this.timestamp;
    }

    public int getPid() {
        return this.pid;
    }

    public String getTag() {
        return this.tag;
    }

    public String getMessage() {
        return this.message;
    }
}

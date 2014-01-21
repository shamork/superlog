package feiw;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LogSource {
    public static final int stIdle = 0;
    public static final int stConnecting = 1;
    public static final int stConnected = 2;

    public static abstract class LogFilter {
        public static final String OP_EQUALS = " = ";
        public static final String OP_CONTAINS = "contains";
        public static final String OP_GREATERTHAN = " > ";
        public static final String OP_LESSTHEN = " < ";
        public static final String FIELD_LEVEL = "level";
        public static final String FIELD_TIME = "time";
        public static final String FIELD_CONTENT = "args";
        
        public abstract boolean filterLog(final LogParser parser, final String item);
        
        String mName;
        LogFilter(String n) {
            mName = n;
        }
        
        void setName(String n) {
            mName = n;
        }
        
        String getName() {
            return mName;
        }
        
        public LogFilter and(final LogFilter f) {
            return new LogFilter(getName() + " and " + f.getName()) {
                @Override
                public boolean filterLog(final LogParser parser, final String item) {
                   return LogFilter.this.filterLog(parser, item) && f.filterLog(parser, item);
                }
            };
        }
        
        public  LogFilter or(final LogFilter f) {
            return new LogFilter(getName() + " or " + f.getName()) {
                @Override
                public boolean filterLog(final LogParser parser, final String item) {
                   return LogFilter.this.filterLog(parser, item) || f.filterLog(parser, item);
                }
            };
        }
            public static LogFilter newLogFilter(String field, String op, final Object dstObj) {
            if (FIELD_LEVEL.equals(field)) {
                if (OP_EQUALS.equals(op)) {
                    return new LogFilter(field + " " + op + " " + dstObj) {
                        @Override
                        public boolean filterLog(final LogParser parser, final String item) {
                            return parser.parsePriority(item) == ((Integer)dstObj).intValue();
                        }
                    };
                } else if (OP_GREATERTHAN.equals(op)) {
                    return new LogFilter(field + " " + op + " " + dstObj) {
                        @Override
                        public boolean filterLog(final LogParser parser, final String item) {
                            return parser.parsePriority(item) > ((Integer)dstObj).intValue();
                        }
                        
                    };
                } else if (OP_LESSTHEN.equals(op)) {
                    return new LogFilter(field + " " + op + " " + dstObj) {
                        @Override
                        public boolean filterLog(final LogParser parser, final String item) {
                            return parser.parsePriority(item) < ((Integer)dstObj).intValue();
                        }
                        
                    };
                }
            } else if (FIELD_TIME.equals(field)) {
                if (OP_EQUALS.equals(op)) {
                    
                } else if (OP_CONTAINS.equals(op)) {
                    
                } else if (OP_GREATERTHAN.equals(op)) {
                    
                } else if (OP_LESSTHEN.equals(op)) {
                    
                }
                
            } else if (FIELD_CONTENT.equals(field)) {
                if (OP_CONTAINS.equals(op)) {
                    return new LogFilter(field + " " + op + " " + dstObj) {
                        private final StringPattern mPat = new StringPattern((String)dstObj, false);
                        @Override
                        public boolean filterLog(final LogParser parser, final String item) {
                            return mPat.isContainedBy(parser.parseMessage(item)) >= 0;
                        }
                    };
                }
            }
            return null;
        }
        
    }
 
    public interface LogListener {
        public void onLogChanged();

        public void onSearchResult();
    }

    public interface StatusListener {
        public void onStatusChanged(int oldStatus, int newStatus);
    }

    protected int mStatus = stIdle;

    public synchronized int getStatus() {
        return mStatus;
    }

    public synchronized void addStatusListener(StatusListener slis) {
        if (slis != null) {
            mStatusListeners.add(slis);
        }
    }

    public synchronized void removeStatusListener(StatusListener slis) {
        mStatusListeners.remove(slis);
    }

    protected synchronized void setStatus(int st) {
       if (st != mStatus) {
            for (StatusListener li : mStatusListeners) {
                li.onStatusChanged(mStatus, st);
            }
            mStatus = st;
        }
    }
    

    long mNotifyTimeSpan = 300;
    protected void fetchLogs(InputStream is) throws IOException {
        BufferedReader din = new BufferedReader(new InputStreamReader(is));
        int line = 0;
        String str = din.readLine();
        long start_time = System.currentTimeMillis();
        while (str != null) {
            str = str.trim();
            if (!str.isEmpty()) {
                line++;
                long curtime = System.currentTimeMillis();
                if (is.available() == 0 || curtime - start_time > mNotifyTimeSpan) {
                    addLogItem(str, true);
                    start_time = curtime;
                } else {
                    addLogItem(str, false);
                }
            }
            str = din.readLine();
        }
        System.out.println(" log lines = " + line);
    }
 
/*
    public static final class LogItem {
        private final String[] texts;
        private Date   mTime;
        static final SimpleDateFormat mDfmt = new SimpleDateFormat("MMM dd HH:mm:ss.SSS");
        static final SimpleDateFormat mDfmts = new SimpleDateFormat("MMM dd HH:mm:ss");
        static private SimpleDateFormat mParser = mDfmt;
        private int mLevel = 7;

        Date getTime() {
            return mTime;
        }
        
        public String getText() {
            return texts[4];
        }
        public String getText(int i) {
            if (i >= 0 && i < texts.length) {
                return texts[i];
            }
            return null;
        }

       static final String[] seperator = { "    ", " ", " ", " " };
        public LogItem(final String str) {
            final String [] ret = new String[5];
            texts = ret;
            int idx = 0, nextidx;
            final int slen = str.length();
            for (int i = 0; i < 4; i++) {
                nextidx = str.indexOf(seperator[i], idx);
                if (nextidx <= 0) {
                    ret[0] = ret[1] = ret[2] = ret[3] = null;
                    ret[4] = str;
                    return;
                }
                ret[i] = str.substring(idx, nextidx);
                idx = nextidx + seperator[i].length();
                while (slen > idx && str.charAt(idx) == ' ')
                    idx++;
            }
            ret[4] = str.substring(idx);
            if (!ret[1].isEmpty()) {
                mLevel = ret[1].charAt(0) - '0';
                if (mLevel < 0 || mLevel > 7) {
                    mLevel = 6;
                }
            }

            try {
                mTime = mParser.parse(ret[0]);
            } catch (ParseException e) {
                try {
                    mParser = mDfmts;
                    mTime = mParser.parse(ret[0]);
                } catch (ParseException e1) {
                    mTime = null;
                }
            }
        }

        public LogItem(String[] txt) {
            texts = txt;
        }

        public int getTextCount() {
            if (texts != null) {
                return texts.length;
            }
            return 0;
        }

        public int getLevel() {
            return mLevel;
        }
    }
 */
    public static class LogView {
        private List<String> mFilteredItems;
        private LogListener mListener = null;
        private LogFilter mFilter = null;
        private StringPattern mSearchPattern = null;
        private LogParser mParser;

        private AtomicBoolean mLogChanged = new AtomicBoolean(false);
        private AtomicBoolean mPaused = new AtomicBoolean(false);

        public LogParser getLogParser() {
            return mParser;
        }
        public final StringPattern getSearchPattern() {
           return mSearchPattern;
        }
        public boolean isPaused() {
            return mPaused.get();
        }

        public void resume() {
            mPaused.set(false);
        }

        public void pause() {
            mPaused.set(true);
        }

        private void notifyListener() {
            if (mListener != null && !mPaused.get() && mLogChanged.get()) {
                mListener.onLogChanged();
                mLogChanged.set(false);
            }
        }
 
        public void writeLogs(OutputStream os) throws IOException  {
            BufferedWriter dw = new BufferedWriter(new OutputStreamWriter(os));
            synchronized (mFilteredItems) {
                final int s = mFilteredItems.size();
                int i = 0; 
                for (i = 0; i < s-1; i++ ) {
                    dw.write(mFilteredItems.get(i));
                    dw.write('\n');
                }
                dw.write(mFilteredItems.get(i));
            }
            dw.flush();
        }

        private LogView(LogListener listener, LogFilter filter, LogParser parser, List<String> source) {
            mListener = listener;
            mFilter = filter;
            mParser = parser;
            mFilteredItems = Collections.synchronizedList(new ArrayList<String>());
 
            if (source != null) {
                synchronized (source) {
                    for (String it : source) {
                        if (filter.filterLog(parser, it)) {
                            mFilteredItems.add(it);
                        }
                    }
                }
            }

            if (mFilteredItems.size() > 0) {
                mLogChanged.set(true);
                notifyListener();
            }
        }
        
        public boolean isSearchResults(final String logmsg) {
            if (mSearchPattern != null) {
                return mSearchPattern.isContainedBy(logmsg) >= 0;
            }
            return false;
        }
        public void add(final String item, boolean notifylistner) {
            if (mFilter == null || mFilter.filterLog(mParser, item)) {
                if (isSearchResults(item)) {
                    mSearchResults++;
                }
                mFilteredItems.add(item);
                mLogChanged.set(true);
                if (notifylistner) {
                    notifyListener();
                }
            }
        }

        public void clear() {
            if (mFilteredItems.size() > 0) {
                mSearchResults = -1;
                mSearchPattern = null;
                mFilteredItems.clear();
                mLogChanged.set(true);
                notifyListener();
            }
        }

        public int size() {
                return mFilteredItems.size();
        }

        public final String getLog(int index) {
            return mFilteredItems.get(index);
        }

        private int mSearchResults = -1;

        public int getSearchResults() {
            return mSearchResults;
        }

        public int getPrevSearchResult(int start) {
            if (mSearchResults <= 0) {
                return -1;
            }
            synchronized (mFilteredItems) {
                if (start < 0 || start >= mFilteredItems.size()) {
                    start = mFilteredItems.size() - 1;
                }
                for (int i = start; i >= 0; i--) {
                    if (isSearchResults(mFilteredItems.get(i))) {
                        return i;
                    }
                }
                for (int i = mFilteredItems.size() - 1; i > start; i--) {
                    if (isSearchResults(mFilteredItems.get(i))) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public int getNextSearchResult(int start) {
            if (mSearchResults <= 0) {
                return -1;
            }
            synchronized (mFilteredItems) {
                for (int i = start; i < mFilteredItems.size(); i++) {
                    if (isSearchResults(mFilteredItems.get(i))) {
                        return i;
                    }
                }

                for (int i = 0; i < start; i++) {
                    if (isSearchResults(mFilteredItems.get(i))) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public void search(String txt, boolean caseSenstive) {
            if (mFilteredItems == null || mFilteredItems.size() <= 0) {
                return;
            }
            mSearchPattern = new StringPattern(txt, caseSenstive);
            int results = 0;
            synchronized (mFilteredItems) {
                for (String it : mFilteredItems) {
                    if(isSearchResults(it)) {
                            results++;
                     }
                }
            }
            mSearchResults = results;
            mLogChanged.set(true);
            mListener.onSearchResult();
        }
    }

    public synchronized void removeLogView(LogView v) {
        mViews.remove(v);
        if (mViews.size() == 0) {
            disconnect();
        }
    }

    public synchronized LogView newLogView(LogListener listener, LogFilter filter, LogParser parser, LogView parentView) {
        LogView v = new LogView(listener, filter, parser, parentView == null ? null:parentView.mFilteredItems);
        mViews.add(v);
        return v;
    }

    public synchronized void  addLogItem(final String item, boolean notifylistener) {
            for (LogView v : mViews) {
                v.add(item, notifylistener);
            }
    }

    public synchronized void notifyViews() {
        for (LogView v : mViews) {
            v.notifyListener();
        }
    }
    public void disconnect() {

    }

   // List<LogItem> mItems = (List<LogItem>) Collections.synchronizedList(new ArrayList<LogItem>(
     //       10000));
    List<LogView> mViews = new ArrayList<LogView>(5);

    List<StatusListener> mStatusListeners = (List<StatusListener>) Collections
            .synchronizedList(new ArrayList<StatusListener>(5));

}

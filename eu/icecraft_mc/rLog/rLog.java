package eu.icecraft_mc.rLog;

import java.io.*;
import java.util.ArrayList;

public class rLog {

    // A buffered logging class.

    // usage: rLog.init(folder, filename, buffersize)

    // to log: rLog.log(String text)

    // when finished, rLog.uninit()

    private PrintWriter theWriter;

    private final ArrayList<String> buffer = new ArrayList<String>();
    public final int bfsize;

    public rLog(String folder, String filename, int buffersize) {

        bfsize = buffersize;
        checkExists(folder, filename);
        load(folder, filename);
    }

    public void log(String text) {
        buffer.add(text);
        if (buffer.size() >= bfsize) commit();
    }

    public void uninit() {
        commit();
        theWriter.close();
    }

    private void checkExists(String fo, String f) {
        try {
            File folder = new File(fo);
            File file = new File(fo, f);
            if (!folder.exists()) folder.mkdirs();
            if (!file.exists()) file.createNewFile();
        } catch (Exception e) {
            System.err.println("[rLog] Exception occurred whilst attempting to create log file.");
            e.printStackTrace();
        }
    }

    private void commit() {
        for (String s : buffer)
            theWriter.println(s);
        buffer.clear();
        theWriter.flush();
    }

    private void load(String fo, String fi) {
        try {
            theWriter = new PrintWriter(new FileWriter(new File(fo, fi), true));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

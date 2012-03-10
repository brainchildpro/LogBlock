package de.diddiz.util;

import java.io.*;
import java.net.URL;
import java.text.*;
import java.util.List;
import java.util.logging.Logger;

public class Utils {
    public static class ExtensionFilenameFilter implements FilenameFilter {
        private final String ext;

        public ExtensionFilenameFilter(final String ext) {
            this.ext = ext;
        }

        @Override
        public boolean accept(final File dir, final String name) {
            return name.toLowerCase().endsWith(this.ext);
        }
    }

    public static String newline = System.getProperty("line.separator");

    public static void download(final Logger log, final URL url, final File file) throws IOException {
        if (!file.getParentFile().exists()) file.getParentFile().mkdir();
        if (file.exists()) file.delete();
        file.createNewFile();
        final int size = url.openConnection().getContentLength();
        log.info("Downloading " + file.getName() + " (" + size / 1024 + "kb) ...");
        final InputStream in = url.openStream();
        final OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
        final byte[] buffer = new byte[1024];
        int len, downloaded = 0, msgs = 0;
        final long start = System.currentTimeMillis();
        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
            downloaded += len;
            if ((int) ((System.currentTimeMillis() - start) / 500) > msgs) {
                log.info((int) (downloaded / (double) size * 100d) + "%");
                msgs++;
            }
        }
        in.close();
        out.close();
        log.info("Download finished");
    }

    public static boolean isByte(final String str) {
        try {
            Byte.parseByte(str);
            return true;
        } catch (final NumberFormatException ex) {
        }
        return false;
    }

    public static boolean isInt(final String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch (final NumberFormatException ex) {
        }
        return false;
    }

    public static String join(final String[] s, final String delimiter) {
        if (s == null || s.length == 0) return "";
        final int len = s.length;
        final StringBuffer buffer = new StringBuffer(s[0]);
        for (int i = 1; i < len; i++)
            buffer.append(delimiter).append(s[i]);
        return buffer.toString();
    }

    public static String listing(final List<?> entries, final String delimiter, final String finalDelimiter) {
        final int len = entries.size();
        if (len == 0) return "";
        if (len == 1) return entries.get(0).toString();
        final StringBuilder builder = new StringBuilder(entries.get(0).toString());
        for (int i = 1; i < len - 1; i++)
            builder.append(delimiter + entries.get(i).toString());
        builder.append(finalDelimiter + entries.get(len - 1).toString());
        return builder.toString();
    }

    public static String listing(final String[] entries, final String delimiter, final String finalDelimiter) {
        final int len = entries.length;
        if (len == 0) return "";
        if (len == 1) return entries[0];
        final StringBuilder builder = new StringBuilder(entries[0]);
        for (int i = 1; i < len - 1; i++)
            builder.append(delimiter + entries[i]);
        builder.append(finalDelimiter + entries[len - 1]);
        return builder.toString();
    }

    public static int parseTimeSpec(final String[] spec) {
        if (spec == null || spec.length < 1 || spec.length > 2) return -1;
        if (spec.length == 1 && isInt(spec[0])) return Integer.valueOf(spec[0]);
        if (!spec[0].contains(":") && !spec[0].contains("."))
            if (spec.length == 2) {
                if (!isInt(spec[0])) return -1;
                int min = Integer.parseInt(spec[0]);
                if (spec[1].startsWith("h"))
                    min *= 60;
                else if (spec[1].startsWith("d")) min *= 1440;
                return min;
            } else if (spec.length == 1) {
                int days = 0, hours = 0, minutes = 0;
                int lastIndex = 0, currIndex = 1;
                while (currIndex <= spec[0].length()) {
                    while (currIndex <= spec[0].length() && isInt(spec[0].substring(lastIndex, currIndex)))
                        currIndex++;
                    if (currIndex - 1 != lastIndex) {
                        final String param = spec[0].substring(currIndex - 1, currIndex).toLowerCase();
                        if (param.equals("d"))
                            days = Integer.parseInt(spec[0].substring(lastIndex, currIndex - 1));
                        else if (param.equals("h"))
                            hours = Integer.parseInt(spec[0].substring(lastIndex, currIndex - 1));
                        else if (param.equals("m"))
                            minutes = Integer.parseInt(spec[0].substring(lastIndex, currIndex - 1));
                    }
                    lastIndex = currIndex;
                    currIndex++;
                }
                if (days == 0 && hours == 0 && minutes == 0) return -1;
                return minutes + hours * 60 + days * 1440;
            } else return -1;
        final String timestamp;
        if (spec.length == 1) {
            if (spec[0].contains(":"))
                timestamp = new SimpleDateFormat("dd.MM.yyyy").format(System.currentTimeMillis()) + " "
                        + spec[0];
            else timestamp = spec[0] + " 00:00:00";
        } else timestamp = spec[0] + " " + spec[1];
        try {
            return (int) ((System.currentTimeMillis() - new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").parse(
                    timestamp).getTime()) / 60000);
        } catch (final ParseException ex) {
            return -1;
        }
    }

    public static String readURL(final URL url) throws IOException {
        final StringBuilder content = new StringBuilder();
        final BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null)
            content.append(inputLine);
        in.close();
        return content.toString();
    }

    public static String spaces(final int count) {
        final StringBuilder filled = new StringBuilder(count);
        for (int i = 0; i < count; i++)
            filled.append(' ');
        return filled.toString();
    }
}

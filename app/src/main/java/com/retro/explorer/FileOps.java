package com.retro.explorer;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.MimeTypeMap;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** File-system helpers: type detection, copy/move/delete, sizes, search, text I/O. */
final class FileOps {
    static final int K_FOLDER = 0, K_TEXT = 1, K_CODE = 2, K_IMAGE = 3, K_AUDIO = 4, K_VIDEO = 5, K_ARCHIVE = 6,
            K_PDF = 7, K_APK = 8, K_DOC = 9, K_SHEET = 10, K_PPT = 11, K_FILE = 12, K_DRIVE = 13, K_BIN = 14,
            K_BINFULL = 15, K_STAR = 16, K_SD = 17;

    private static final Map<String, Integer> KIND = new HashMap<>();
    private static final Map<String, String> TYPE = new HashMap<>();

    private static void reg(int k, String type, String... exts) {
        for (String e : exts) { KIND.put(e, k); TYPE.put(e, type); }
    }

    static {
        reg(K_TEXT, "Text Document", "txt"); reg(K_TEXT, "Markdown File", "md"); reg(K_TEXT, "Log File", "log");
        reg(K_TEXT, "Configuration File", "ini", "cfg", "conf", "prop", "properties"); reg(K_TEXT, "CSV File", "csv");
        reg(K_TEXT, "Subtitle File", "srt"); reg(K_TEXT, "System Information File", "nfo");
        reg(K_CODE, "JSON File", "json"); reg(K_CODE, "JavaScript File", "js"); reg(K_CODE, "HTML Document", "html", "htm");
        reg(K_CODE, "Cascading Style Sheet", "css"); reg(K_CODE, "XML Document", "xml"); reg(K_CODE, "Python File", "py");
        reg(K_CODE, "Java Source File", "java"); reg(K_CODE, "Kotlin File", "kt"); reg(K_CODE, "C Source File", "c", "h");
        reg(K_CODE, "C++ Source File", "cpp", "hpp"); reg(K_CODE, "Shell Script", "sh"); reg(K_CODE, "Batch File", "bat");
        reg(K_CODE, "PHP File", "php"); reg(K_CODE, "YAML File", "yml", "yaml");
        reg(K_IMAGE, "JPEG image", "jpg", "jpeg"); reg(K_IMAGE, "PNG image", "png"); reg(K_IMAGE, "GIF image", "gif");
        reg(K_IMAGE, "Bitmap image", "bmp"); reg(K_IMAGE, "WEBP image", "webp"); reg(K_IMAGE, "HEIC image", "heic", "heif");
        reg(K_IMAGE, "SVG image", "svg"); reg(K_IMAGE, "Icon", "ico");
        reg(K_AUDIO, "MP3 Format Sound", "mp3"); reg(K_AUDIO, "Wave Sound", "wav"); reg(K_AUDIO, "OGG Audio", "ogg");
        reg(K_AUDIO, "MPEG-4 Audio", "m4a"); reg(K_AUDIO, "FLAC Audio", "flac"); reg(K_AUDIO, "AAC Audio", "aac");
        reg(K_AUDIO, "Opus Audio", "opus"); reg(K_AUDIO, "AMR Audio", "amr"); reg(K_AUDIO, "MIDI Sequence", "mid", "midi");
        reg(K_VIDEO, "MP4 Video", "mp4"); reg(K_VIDEO, "Matroska Video", "mkv"); reg(K_VIDEO, "WEBM Video", "webm");
        reg(K_VIDEO, "AVI Video", "avi"); reg(K_VIDEO, "QuickTime Movie", "mov"); reg(K_VIDEO, "3GP Video", "3gp");
        reg(K_VIDEO, "MPEG-4 Video", "m4v"); reg(K_VIDEO, "MPEG-TS Video", "ts");
        reg(K_ARCHIVE, "Compressed (zipped) Folder", "zip"); reg(K_ARCHIVE, "RAR Archive", "rar"); reg(K_ARCHIVE, "7-Zip Archive", "7z");
        reg(K_ARCHIVE, "TAR Archive", "tar"); reg(K_ARCHIVE, "GZ Archive", "gz", "tgz"); reg(K_ARCHIVE, "BZ2 Archive", "bz2"); reg(K_ARCHIVE, "XZ Archive", "xz");
        reg(K_PDF, "PDF Document", "pdf"); reg(K_APK, "Android Package", "apk", "xapk", "apks");
        reg(K_DOC, "Word Document", "doc", "docx"); reg(K_DOC, "OpenDocument Text", "odt"); reg(K_DOC, "Rich Text Format", "rtf");
        reg(K_SHEET, "Spreadsheet", "xls", "xlsx", "ods"); reg(K_PPT, "Presentation", "ppt", "pptx", "odp");
    }

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    static int kindOf(String name) {
        Integer k = KIND.get(ext(name));
        return k == null ? K_FILE : k;
    }

    static String typeName(String name, boolean dir) {
        if (dir) return "File folder";
        String e = ext(name);
        String t = TYPE.get(e);
        if (t != null) return t;
        return e.isEmpty() ? "File" : e.toUpperCase(Locale.ROOT) + " File";
    }

    static String mimeOf(String name) {
        String e = ext(name);
        if (e.equals("apk")) return "application/vnd.android.package-archive";
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(e);
        if (m != null) return m;
        int k = kindOf(name);
        if (k == K_TEXT || k == K_CODE) return "text/plain";
        return "*/*";
    }

    static String formatSize(long b) {
        if (b < 1024) return b + " bytes";
        String[] u = {"KB", "MB", "GB", "TB"};
        double v = b;
        int i = -1;
        do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
        String n = v < 10 ? String.format(Locale.US, "%.2f", v) : v < 100 ? String.format(Locale.US, "%.1f", v) : String.valueOf(Math.round(v));
        return n + " " + u[i];
    }

    static String formatDate(long t) {
        if (t <= 0) return "";
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(t));
    }

    /** Returns an error message, or null if the name is OK. */
    static String validName(String n) {
        if (n == null || n.trim().isEmpty()) return "Type a name.";
        for (char c : "\\/:*?\"<>|".toCharArray())
            if (n.indexOf(c) >= 0) return "A name can't contain any of these characters:  \\ / : * ? \" < > |";
        if (n.equals(".") || n.equals("..")) return "That name isn't allowed.";
        return null;
    }

    static String uniqueName(File dir, String name, boolean copy, boolean isDir) {
        String e = isDir ? "" : ext(name);
        String stem = e.isEmpty() ? name : name.substring(0, name.length() - e.length() - 1);
        String x = e.isEmpty() ? "" : "." + name.substring(name.length() - e.length());
        if (!new File(dir, name).exists()) return name;
        if (copy) {
            String c = stem + " - Copy" + x;
            if (!new File(dir, c).exists()) return c;
            for (int i = 2; ; i++) {
                c = stem + " - Copy (" + i + ")" + x;
                if (!new File(dir, c).exists()) return c;
            }
        }
        for (int i = 2; ; i++) {
            String c = stem + " (" + i + ")" + x;
            if (!new File(dir, c).exists()) return c;
        }
    }

    static boolean isInside(File child, File parent) {
        String c = child.getAbsolutePath(), p = parent.getAbsolutePath();
        if (c.equals(p)) return true;
        return c.startsWith(p.endsWith("/") ? p : p + "/");
    }

    static void copy(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs() && !dst.isDirectory()) throw new IOException("Can't create “" + dst.getName() + "”");
            File[] kids = src.listFiles();
            if (kids != null) for (File f : kids) copy(f, new File(dst, f.getName()));
        } else {
            InputStream in = null;
            OutputStream out = null;
            try {
                in = new FileInputStream(src);
                out = new FileOutputStream(dst);
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                closeQuietly(in);
                closeQuietly(out);
            }
            //noinspection ResultOfMethodCallIgnored
            dst.setLastModified(src.lastModified());
        }
    }

    static boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File c : kids) deleteRecursive(c);
        }
        return f.delete() || !f.exists();
    }

    static void move(File src, File dst) throws IOException {
        if (dst.exists()) throw new IOException("“" + dst.getName() + "” already exists.");
        if (src.renameTo(dst)) return;
        copy(src, dst);
        if (!deleteRecursive(src)) throw new IOException("Copied “" + src.getName() + "”, but couldn't remove the original.");
    }

    /** acc[0] = bytes, acc[1] = files, acc[2] = folders */
    static void dirSize(File f, long[] acc) {
        File[] kids = f.listFiles();
        if (kids == null) return;
        for (File c : kids) {
            if (c.isDirectory()) { acc[2]++; dirSize(c, acc); }
            else { acc[1]++; acc[0] += c.length(); }
        }
    }

    static List<File> search(File base, String q, boolean hidden, int max) {
        List<File> out = new ArrayList<>();
        ArrayDeque<File> dq = new ArrayDeque<>();
        dq.add(base);
        String ql = q.toLowerCase(Locale.ROOT);
        while (!dq.isEmpty() && out.size() < max) {
            File d = dq.poll();
            File[] kids = d.listFiles();
            if (kids == null) continue;
            for (File c : kids) {
                String n = c.getName();
                if (!hidden && n.startsWith(".")) continue;
                if (n.toLowerCase(Locale.ROOT).contains(ql)) out.add(c);
                if (c.isDirectory()) dq.add(c);
            }
        }
        return out;
    }

    static String readText(File f) throws IOException {
        if (f.length() > 8L * 1024 * 1024) throw new IOException("This file is too large to open in Notepad.");
        byte[] b = new byte[(int) f.length()];
        InputStream in = new FileInputStream(f);
        try {
            int off = 0, n;
            while (off < b.length && (n = in.read(b, off, b.length - off)) > 0) off += n;
        } finally {
            closeQuietly(in);
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    static void writeText(File f, String s) throws IOException {
        OutputStream o = new FileOutputStream(f);
        try {
            o.write(s.getBytes(StandardCharsets.UTF_8));
        } finally {
            closeQuietly(o);
        }
    }

    static Bitmap decodeSampled(File f, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            while (o.outWidth / (s * 2) >= max || o.outHeight / (s * 2) >= max) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    static void closeQuietly(Closeable c) {
        if (c != null) try { c.close(); } catch (IOException ignored) { }
    }
}

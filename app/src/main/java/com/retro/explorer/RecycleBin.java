package com.retro.explorer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** A Recycle Bin kept in a hidden folder on the storage root, with a JSON index of original locations. */
final class RecycleBin {
    static final class Entry {
        String id, name, orig;
        boolean isDir;
        long size, date;
    }

    final File dir;

    RecycleBin(File storageRoot) {
        dir = new File(storageRoot, ".ExplorerRecycleBin");
    }

    private File index() { return new File(dir, ".index.json"); }

    File fileOf(Entry e) { return new File(dir, e.id); }

    synchronized List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        try {
            if (!index().exists()) return out;
            JSONArray a = new JSONArray(FileOps.readText(index()));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.getString("id");
                e.name = o.getString("name");
                e.orig = o.getString("orig");
                e.isDir = o.optBoolean("dir");
                e.size = o.optLong("size");
                e.date = o.optLong("date");
                if (fileOf(e).exists()) out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void ensureDir() throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Can't create the Recycle Bin folder.");
        File nomedia = new File(dir, ".nomedia");
        if (!nomedia.exists()) {
            try { //noinspection ResultOfMethodCallIgnored
                nomedia.createNewFile();
            } catch (IOException ignored) { }
        }
    }

    private void save(List<Entry> l) throws Exception {
        ensureDir();
        JSONArray a = new JSONArray();
        for (Entry e : l) {
            JSONObject o = new JSONObject();
            o.put("id", e.id);
            o.put("name", e.name);
            o.put("orig", e.orig);
            o.put("dir", e.isDir);
            o.put("size", e.size);
            o.put("date", e.date);
            a.put(o);
        }
        FileOps.writeText(index(), a.toString());
    }

    synchronized void moveToBin(File f) throws Exception {
        ensureDir();
        List<Entry> l = list();
        Entry e = new Entry();
        e.id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1e6), 36);
        e.name = f.getName();
        e.orig = f.getAbsolutePath();
        e.isDir = f.isDirectory();
        if (e.isDir) {
            long[] acc = new long[3];
            FileOps.dirSize(f, acc);
            e.size = acc[0];
        } else {
            e.size = f.length();
        }
        e.date = System.currentTimeMillis();
        FileOps.move(f, fileOf(e));
        l.add(e);
        save(l);
    }

    synchronized File restore(Entry e) throws Exception {
        List<Entry> l = list();
        File parent = new File(e.orig).getParentFile();
        if (parent == null) throw new IOException("Original location is unknown.");
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Can't recreate " + parent.getPath());
        String n = FileOps.uniqueName(parent, e.name, false, e.isDir);
        File dst = new File(parent, n);
        FileOps.move(fileOf(e), dst);
        remove(l, e.id);
        save(l);
        return dst;
    }

    synchronized void delete(Entry e) throws Exception {
        FileOps.deleteRecursive(fileOf(e));
        List<Entry> l = list();
        remove(l, e.id);
        save(l);
    }

    synchronized void empty() throws Exception {
        for (Entry e : list()) FileOps.deleteRecursive(fileOf(e));
        save(new ArrayList<Entry>());
    }

    private static void remove(List<Entry> l, String id) {
        Iterator<Entry> it = l.iterator();
        while (it.hasNext()) if (it.next().id.equals(id)) it.remove();
    }
}

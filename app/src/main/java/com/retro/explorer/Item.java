package com.retro.explorer;

import java.io.File;

/** One entry shown in the file view (a real file, or a Recycle Bin entry). */
final class Item {
    final File file;
    final String name;
    final boolean isDir;
    final long size, mtime;
    final RecycleBin.Entry bin;

    Item(File f) {
        file = f;
        name = f.getName();
        isDir = f.isDirectory();
        size = isDir ? 0 : f.length();
        mtime = f.lastModified();
        bin = null;
    }

    Item(RecycleBin.Entry e, File f) {
        file = f;
        name = e.name;
        isDir = e.isDir;
        size = e.size;
        mtime = e.date;
        bin = e;
    }

    String path() { return file.getAbsolutePath(); }

    int kind() { return isDir ? FileOps.K_FOLDER : FileOps.kindOf(name); }
}

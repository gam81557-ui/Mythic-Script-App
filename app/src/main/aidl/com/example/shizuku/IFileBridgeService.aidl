package com.example.shizuku;

import android.os.ParcelFileDescriptor;

interface IFileBridgeService {
    void destroy() = 16777114;

    int getUid() = 1;

    boolean exists(String path) = 2;

    boolean isDirectory(String path) = 3;

    long getFileSize(String path) = 4;

    boolean mkdirs(String path) = 5;

    boolean deleteRecursive(String path) = 6;

    String[] listFiles(String path) = 7;

    ParcelFileDescriptor openRead(String path) = 8;

    ParcelFileDescriptor openWrite(String path, boolean overwrite) = 9;
}

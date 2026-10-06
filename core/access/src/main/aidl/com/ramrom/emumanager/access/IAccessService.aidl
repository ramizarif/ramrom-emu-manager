package com.ramrom.emumanager.access;

import android.os.ParcelFileDescriptor;

interface IAccessService {
    // Shizuku destroy transaction.
    void destroy() = 16777114;

    // Read a file.
    byte[] readFile(String path) = 1;

    // Write a file.
    void writeFile(String path, in byte[] bytes) = 2;

    // Delete a file.
    void deleteFile(String path) = 3;

    // List directory contents.
    String[] listDir(String path) = 4;

    // Execute a command with allow-list validation.
    String exec(in String[] argv) = 5;

    // Probe capabilities (run harmless tests).
    String[] probeCapabilities(String probeDir) = 6;

    // Install an APK streamed from [apk] into `pm install -S size`. The shell user can't read our
    // private cache, so the file crosses as a descriptor instead of a path. Returns pm's output.
    String installApk(in ParcelFileDescriptor apk, long size) = 7;
}

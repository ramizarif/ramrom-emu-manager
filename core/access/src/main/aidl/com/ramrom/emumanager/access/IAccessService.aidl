package com.ramrom.emumanager.access;

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
}

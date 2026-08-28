package com.rrsistemas.erauma.moment;

public final class PathSafe {
    private PathSafe() {}

    static String filename(String filename) {
        return filename.replace("\\", "/").substring(filename.replace("\\", "/").lastIndexOf('/') + 1);
    }
}

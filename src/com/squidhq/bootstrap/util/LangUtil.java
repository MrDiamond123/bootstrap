package com.squidhq.bootstrap.util;

public class LangUtil {

    public static void close(AutoCloseable... closeables) {
        for(AutoCloseable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch(Exception exception) {
                    // ignore
                }
            }
        }
    }

    public static void close(Iterable<? extends AutoCloseable> closeables) {
        for(AutoCloseable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch(Exception exception) {
                    // ignore
                }
            }
        }
    }

}

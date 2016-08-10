package com.squidhq.bootstrap.util;

import java.io.Closeable;

public class LangUtil {

    public static void close(Closeable... closeables) {
        for(Closeable closeable : closeables) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch(Exception exception) {
                    // ignore
                }
            }
        }
    }

    public static void close(Iterable<? extends Closeable> closeables) {
        for(Closeable closeable : closeables) {
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

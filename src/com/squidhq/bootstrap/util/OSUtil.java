package com.squidhq.bootstrap.util;

import java.io.File;

public class OSUtil {

    public static final String NAME = "minecraft";

    public static enum OS {
        WINDOWS,
        MACOS,
        SOLARIS,
        LINUX,
        UNKNOWN;
    }

    public static OS getOS() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return OS.WINDOWS;
        }
        if (osName.contains("mac")) {
            return OS.MACOS;
        }
        if (osName.contains("linux")) {
            return OS.LINUX;
        }
        if (osName.contains("unix")) {
            return OS.LINUX;
        }
        return OS.UNKNOWN;
    }

    public static File getWorkingDirectory() {
        String userHome = System.getProperty("user.home", ".");
        File result;
        switch(OSUtil.getOS()) {
            case WINDOWS:
                String applicationData = System.getenv("APPDATA");
                String folder = applicationData != null ? applicationData : userHome;
                result = new File(folder, "." + NAME + "/");
                break;
            case MACOS:
                result = new File(userHome, "Library/Application Support/" + NAME);
                break;
            case LINUX:
            case SOLARIS:
                result = new File(userHome, "." + NAME + "/");
                break;
            default:
                result = new File(userHome, "minecraft/");
                break;
        }
        return result;
    }

}

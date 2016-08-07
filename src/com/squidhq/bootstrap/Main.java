package com.squidhq.bootstrap;

import com.squidhq.bootstrap.util.LangUtil;
import com.squidhq.bootstrap.util.OSUtil;
import com.squidhq.bootstrap.util.PGPUtil;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SignatureException;

public class Main {

    public static final String ASSETCDN = "http://assetcdn.squidhq.com/r/";
    public static final String METACDN = "http://metacdn.squidhq.com/r/";
    public static final String METACDN_LAUNCHER = METACDN + "launcher.meta";

    public static final File workingDirectory = OSUtil.getWorkingDirectory();
    public static final File squidDirectory = new File(workingDirectory, "squidhq");

    public static final ProgressNotifier progress = new ProgressNotifier("SquidHQ");

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch(Exception exception) {
            exception.printStackTrace();
        }
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("http.agent", "SquidHQ Bootstrap/1.0");
        squidDirectory.mkdirs();
        progress.show();

        File metaFileNew = new File(squidDirectory, "launcher.meta.new");
        File metaFile = new File(squidDirectory, "launcher.meta");

        String packMeta;
        try {
            progress.set("Downloading meta...", 10);
            FileUtils.copyURLToFile(new URL(METACDN_LAUNCHER), metaFileNew, 5000, 5000);
            packMeta = FileUtils.readFileToString(metaFileNew, Charsets.UTF_8);
            Main.tryMeta(metaFileNew, metaFile, packMeta, args);
        } catch (Exception exception) {
            exception.printStackTrace();
            try {
                packMeta = FileUtils.readFileToString(metaFile, Charsets.UTF_8);
                Main.tryMeta(metaFile, metaFile, packMeta, args);
            } catch(Exception exception1) {
                exception1.printStackTrace();
            }
        }
    }

    public static void tryMeta(File metaFile, File copyTo, String packMeta, String[] args) throws Exception {
        File lzmaFile = null;
        File jarFile = null;

        String[] packMetaSplit = packMeta.split("\n", 2);
        String packSha1 = packMetaSplit[0];
        String packSignature = packMetaSplit[1];
        System.out.println("Launcher sha1: " + packSha1);
        System.out.println("Launcher signature:");
        System.out.println(packSignature);

        lzmaFile = new File(squidDirectory, packSha1 + ".lzma");
        jarFile = new File(squidDirectory, packSha1 + ".jar");
        if (jarFile.exists()) {
            System.out.println("Found jar: " + jarFile.getName());
        } else {
            File[] toCleanup = squidDirectory.listFiles();
            for (File cleanup : toCleanup) {
                if (!cleanup.getName().endsWith(".lzma") && !cleanup.getName().endsWith(".jar")) {
                    continue;
                }
                cleanup.delete();
                System.out.println("Cleaned up: " + cleanup.getName());
            }

            progress.set("Downloading launcher...", 25);
            final String packSha1URL = ASSETCDN + packSha1;
            FileUtils.copyURLToFile(new URL(packSha1URL), lzmaFile, 5000, 5000);
            System.out.println("Downloaded lzma: " + packSha1URL + " to: " + lzmaFile.getName());

            progress.set("Decompressing launcher...", 65);
            Main.deLZMA(lzmaFile, jarFile);
            System.out.println("Decompressed jar: " + jarFile.getName());
        }

        try {
            progress.set("Verifying launcher...", 85);
            boolean valid;
            try {
                valid = Main.pgpVerify(lzmaFile, packSignature);
            } catch (Exception exception) {
                throw exception;
            }
            if (!valid) {
                throw new Exception("Signature invalid");
            } else {
                System.out.println("Signature valid");
                if (metaFile != copyTo) {
                    FileUtils.copyFile(metaFile, copyTo);
                }
            }
        } finally {
            metaFile.delete();
        }

        progress.set("Running launcher...", 95);
        Main.runJar(jarFile, args);
    }

    public static boolean pgpVerify(File file, String signatureAsc) throws PGPException, SignatureException, IOException {
        PGPPublicKey publicKey = PGPUtil.readPublicKey(Main.class.getResourceAsStream("/squidhq.asc"));
        PGPSignatureList signatureKey = PGPUtil.readSignatureFile(new ByteArrayInputStream(signatureAsc.getBytes(Charsets.UTF_8)));
        PGPSignature signature = signatureKey.get(0);
        signature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            byte[] b = new byte[1024];
            int n;
            while (0 <= (n = inputStream.read(b))) {
                signature.update(b, 0, n);
            }
        } finally {
            LangUtil.close(inputStream);
        }

        return signature.verify();
    }

    public static void deLZMA(File from, File to) throws IOException {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new LZMACompressorInputStream(new FileInputStream(from));
            outputStream = new FileOutputStream(to);
            byte[] b = new byte[1024];
            int n;
            while (0 <= (n = inputStream.read(b))) {
                outputStream.write(b, 0, n);
            }
        } finally {
            LangUtil.close(inputStream, outputStream);
        }
    }

    public static void runJar(File jar, String[] args) {
        if (jar == null) {
            System.out.println("Failure finding metadata for jar");
            return;
        }
        if (!jar.exists()) {
            System.out.println("Failure retrieving jar: " + jar.getName());
            return;
        }
        System.out.println("Running jar: " + jar.getName());
        progress.disposeLater(1000L);
        try {
            URLClassLoader sysLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(sysLoader, new Object[]{jar.toURI().toURL()});

            Class<?> mainClass = sysLoader.loadClass("com.squidhq.launcher.Main");
            Method method = mainClass.getDeclaredMethod("main", String[].class);
            method.invoke(null, new Object[]{args});
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}

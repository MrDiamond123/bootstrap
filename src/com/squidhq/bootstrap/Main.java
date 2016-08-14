package com.squidhq.bootstrap;

import com.squidhq.bootstrap.util.LangUtil;
import com.squidhq.bootstrap.util.OSUtil;
import com.squidhq.bootstrap.util.PGPUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.commons.compress.utils.Charsets;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SignatureException;

public class Main {

    public static final String ASSETCDN = "http://assetcdn.squidhq.com/r/";
    public static final String METACDN = "http://metacdn.squidhq.com/r/";
    public static final String METACDN_LAUNCHER = METACDN + "launcher.meta";

    public static final File workingDirectory = OSUtil.getWorkingDirectory();
    public static final File squidDirectory = new File(workingDirectory, "squidhq");

    public static final ProgressNotifier progress;

    static {
        try {
            String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
            if (lookAndFeel.equals("javax.swing.plaf.metal.MetalLookAndFeel")) {
                for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                    if (!info.getClassName().equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) {
                        continue;
                    }
                    lookAndFeel = info.getClassName();
                    break;
                }
            }
            UIManager.setLookAndFeel(lookAndFeel);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        progress = new ProgressNotifier("SquidHQ");
    }

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.117 Safari/537.36");
        squidDirectory.mkdirs();
        progress.show();

        File metaFileNew = new File(squidDirectory, "launcher.meta.new");
        File metaFile = new File(squidDirectory, "launcher.meta");

        String packMeta;
        try {
            Main.download(new URL(METACDN_LAUNCHER), metaFileNew, "Downloading meta...", 5000);
            packMeta = FileUtils.readFileToString(metaFileNew, Charsets.UTF_8);
            Main.tryMeta(metaFileNew, metaFile, packMeta, args);
            return;
        } catch (Exception exception) {
            exception.printStackTrace();
            try {
                packMeta = FileUtils.readFileToString(metaFile, Charsets.UTF_8);
                Main.tryMeta(metaFile, metaFile, packMeta, args);
                return;
            } catch(Exception exception1) {
                exception1.printStackTrace();
            }
        }

        JOptionPane.showMessageDialog(null, "There was an unexpected error while starting SquidHQ! Try again later", "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
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
            final String packSha1URL = ASSETCDN + packSha1;
            Main.download(new URL(packSha1URL), lzmaFile, "Downloading launcher...", 5000);
            System.out.println("Downloaded lzma: " + packSha1URL + " to: " + lzmaFile.getName());

            InputStream inputStream = new FileInputStream(lzmaFile);
            try {
                if (!DigestUtils.sha1Hex(inputStream).equals(packSha1)) {
                    lzmaFile.delete();
                    throw new Exception("Sha1 mismatch");
                }
            } finally {
                LangUtil.close(inputStream);
            }

            Main.decompress(lzmaFile, jarFile, "Decompressing launcher...");
            System.out.println("Decompressed jar: " + jarFile.getName());
        }

        try {
            boolean valid;
            try {
                valid = Main.pgpVerify(lzmaFile, packSignature, "Verifying launcher...");
            } catch (Exception exception) {
                jarFile.delete();
                lzmaFile.delete();
                throw exception;
            }
            if (!valid) {
                jarFile.delete();
                lzmaFile.delete();
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

        File[] toCleanup = squidDirectory.listFiles();
        for (File cleanup : toCleanup) {
            if (!cleanup.getName().endsWith(".lzma") && !cleanup.getName().endsWith(".jar")
                || cleanup.equals(lzmaFile) || cleanup.equals(jarFile)) {
                continue;
            }
            cleanup.delete();
            System.out.println("Cleaned up: " + cleanup.getName());
        }

        progress.set("Running launcher...", 100);
        Main.runJar(jarFile, lzmaFile, args);
    }

    public static void download(URL url, File to, String progressTitle, int timeout) throws IOException {
        progress.set(progressTitle, 0);
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            inputStream = new BufferedInputStream(connection.getInputStream());
            outputStream = new FileOutputStream(to);
            long length = connection.getContentLength();
            byte[] b = new byte[1024];
            int n;
            int nTotal = 0;
            while (0 <= (n = inputStream.read(b))) {
                outputStream.write(b, 0, n);
                nTotal += n;
                progress.set(progressTitle, (int) Math.round(((double) nTotal / length) * 100));
            }
        } finally {
            LangUtil.close(inputStream, outputStream);
        }
    }

    public static void decompress(File from, File to, String progressTitle) throws IOException {
        progress.set(progressTitle, 0);
        InputStream inputStream = null;
        OutputStream outputStream = null;
        try {
            inputStream = new LZMACompressorInputStream(new FileInputStream(from));
            outputStream = new FileOutputStream(to);
            long length = from.length();
            byte[] b = new byte[1024];
            int n;
            int nTotal = 0;
            while (0 <= (n = inputStream.read(b))) {
                outputStream.write(b, 0, n);
                nTotal += n;
                progress.set(progressTitle, (int) Math.round(((double) nTotal / length) * 100));
            }
        } finally {
            LangUtil.close(inputStream, outputStream);
        }
    }

    public static boolean pgpVerify(File file, String signatureAsc, String progressTitle) throws PGPException, SignatureException, IOException {
        progress.set(progressTitle, 0);
        PGPPublicKey publicKey = PGPUtil.readPublicKey(Main.class.getResourceAsStream("/squidhq.asc"));
        PGPSignatureList signatureKey = PGPUtil.readSignatureFile(new ByteArrayInputStream(signatureAsc.getBytes(Charsets.UTF_8)));
        PGPSignature signature = signatureKey.get(0);
        signature.init(new BcPGPContentVerifierBuilderProvider(), publicKey);

        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            long length = file.length();
            byte[] b = new byte[1024];
            int n;
            int nTotal = 0;
            while (0 <= (n = inputStream.read(b))) {
                signature.update(b, 0, n);
                nTotal += n;
                progress.set(progressTitle, (int) Math.round(((double) nTotal / length) * 100));
            }
        } finally {
            LangUtil.close(inputStream);
        }

        return signature.verify();
    }

    public static void runJar(File jar, File lzma, String[] args) throws Exception {
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
        Method method;
        try {
            URLClassLoader sysLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(sysLoader, new Object[]{jar.toURI().toURL()});

            Class<?> mainClass = sysLoader.loadClass("com.squidhq.launcher.Main");
            method = mainClass.getDeclaredMethod("main", String[].class);
        } catch (Exception exception) {
            jar.delete();
            lzma.delete();
            throw exception;
        }
        try {
            method.invoke(null, new Object[]{args});
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}

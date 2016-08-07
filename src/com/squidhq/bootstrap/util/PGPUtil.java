package com.squidhq.bootstrap.util;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Iterator;

public class PGPUtil {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static PGPPublicKey readPublicKey(InputStream in) throws IOException, PGPException {
        in = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(in);
        PGPPublicKeyRingCollection pgpPub = new PGPPublicKeyRingCollection(in);
        PGPPublicKey key = null;
        Iterator<PGPPublicKeyRing> rIt = pgpPub.getKeyRings();

        while (key == null && rIt.hasNext()) {
            PGPPublicKeyRing kRing = rIt.next();
            Iterator<PGPPublicKey> kIt = kRing.getPublicKeys();
            while (key == null && kIt.hasNext()) {
                PGPPublicKey k = kIt.next();
                if (k.isEncryptionKey()) {
                    key = k;
                }
            }
        }

        if (key == null) {
            throw new IllegalArgumentException("Can't find encryption key in key ring.");
        }

        return key;
    }

    public static PGPSignatureList readSignatureFile(InputStream signatureIn) throws IOException {
        PGPObjectFactory objectFactory = new PGPObjectFactory(org.bouncycastle.openpgp.PGPUtil.getDecoderStream(signatureIn), new BcKeyFingerprintCalculator());
        PGPSignatureList sl = (PGPSignatureList) objectFactory.nextObject();
        signatureIn.close();
        return sl;
    }

}

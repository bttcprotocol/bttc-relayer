package org.bttc.relayer.utils;

import org.spongycastle.jcajce.provider.digest.Keccak;
import org.spongycastle.util.encoders.Hex;

public class SignUtils {

    private SignUtils() {

    }

    /**
     * get the sign of the function
     * @param method the method
     * @return the sign of the function
     */
    public static String getMethodSign(String method) {
        byte[] selector = new byte[4];
        System.arraycopy(sha3(method.getBytes()), 0, selector, 0, 4);
        return Hex.toHexString(selector);
    }

    public static byte[] sha3(byte[] input) {
        Keccak.Digest256 digest256 = new Keccak.Digest256();
        return digest256.digest(input);
    }
}

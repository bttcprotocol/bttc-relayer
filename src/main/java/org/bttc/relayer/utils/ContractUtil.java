package org.bttc.relayer.utils;

import org.bouncycastle.util.encoders.Hex;
import org.tron.common.parameter.CommonParameter;
import org.tron.common.utils.Base58;
import org.tron.common.utils.Sha256Hash;
import java.math.BigInteger;

/**
 * @author tron
 * @date 2021/9/16 11:35
 */
public class ContractUtil {

    private ContractUtil() {

    }
    private static final String SPLIT_0X = "0x";

    public static String getAddressFromHex(String address){
        return encode58Check(address);
    }

    public static String getAddressFromEthHex(String address){
        return encode58Check(decodeStr(address));
    }

    private static String encode58Check(String inputString) {
        byte[] input = Hex.decode(inputString);
        byte[] hash0 = Sha256Hash.hash(CommonParameter.getInstance().isECKeyCryptoEngine(), input);
        byte[] hash1 = Sha256Hash.hash(CommonParameter.getInstance().isECKeyCryptoEngine(), hash0);
        byte[] inputCheck = new byte[input.length + 4];
        System.arraycopy(input, 0, inputCheck, 0, input.length);
        System.arraycopy(hash1, 0, inputCheck, input.length, 4);
        return Base58.encode(inputCheck);
    }

    private static String decodeStr(String inputString) {
        if (inputString.startsWith(SPLIT_0X)) {
            inputString = inputString.substring(2);
        }
        return "41" + inputString;
    }

    public static String getNumberFromEthHex(String inputString){
        if (inputString.startsWith(SPLIT_0X)) {
            inputString = inputString.substring(2);
        }
        return new BigInteger(inputString, 16).toString();
    }
}

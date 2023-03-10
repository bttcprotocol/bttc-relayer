package org.bttc.relayer.utils;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bttc.relayer.constant.CommonConstant;
import org.tron.common.utils.Base58;
import org.tron.common.utils.ByteArray;

/**
 * @author tron
 */
public class TronUtils {

  private TronUtils() {
  }

  /**
   * 41 + address
   */
  private static final byte ADD_PRE_FIX_BYTE_MAINNET = (byte) 0x41;

  /**
   * chage byte array to base58
   */
  public static String encode58Check(byte[] input) {
    if (input == null || input.length == 0) {
      return "";
    }
    byte[] hash0 = Sha256Hash.hash(input);
    byte[] hash1 = Sha256Hash.hash(hash0);
    byte[] inputCheck = new byte[input.length + 4];
    System.arraycopy(input, 0, inputCheck, 0, input.length);
    System.arraycopy(hash1, 0, inputCheck, input.length, 4);
    return Base58.encode(inputCheck);
  }

  public static byte[] decodeFromBase58Check(String addressBase58) {
    if (StringUtils.isEmpty(addressBase58)) {
      return new byte[]{};
    }

    byte[] address;
    try {
      address = decode58Check(addressBase58);
      if (!addressValid(address)) {
        return new byte[]{};
      }
    } catch (Exception e) {
      return new byte[]{};
    }

    return address;
  }

  private static byte[] decode58Check(String input) {
    byte[] decodeCheck = Base58.decode(input);
    if (decodeCheck.length <= 4) {
      return new byte[]{};
    }
    byte[] decodeData = new byte[decodeCheck.length - 4];
    System.arraycopy(decodeCheck, 0, decodeData, 0, decodeData.length);
    byte[] hash0 = Sha256Hash.hash(decodeData);
    byte[] hash1 = Sha256Hash.hash(hash0);
    if (hash1[0] == decodeCheck[decodeData.length] &&
        hash1[1] == decodeCheck[decodeData.length + 1] &&
        hash1[2] == decodeCheck[decodeData.length + 2] &&
        hash1[3] == decodeCheck[decodeData.length + 3]) {
      return decodeData;
    }
    return new byte[]{};
  }

  public static boolean addressValid(byte[] address) {
    if (ArrayUtils.isEmpty(address)) {
      return false;
    }

    if (address.length != CommonConstant.ADDRESS_SIZE) {
      return false;
    }

    return address[0] == CommonConstant.ADD_PRE_FIX_BYTE_MAINNET;
  }

  public static byte[] convertToTronAddress(byte[] address) {
    byte[] newAddress = new byte[21];
    byte[] temp = new byte[]{ADD_PRE_FIX_BYTE_MAINNET};
    System.arraycopy(temp, 0, newAddress, 0, temp.length);

    if (address.length <= 20) {
      int start = 20 - address.length;
      System.arraycopy(address, 0, newAddress, temp.length + start, address.length);
    } else {
      int start = address.length - 20;
      System.arraycopy(address, start, newAddress, temp.length, 20);
    }
    return newAddress;
  }

  public static String convertTronAddressToEthAddress(String address) {
    if (StringUtils.isBlank(address)) {
      return null;
    }
    String decodedAddress = ByteArray.toHexString(TronUtils.decodeFromBase58Check(address));
    if (StringUtils.isBlank(decodedAddress)) {
      return null;
    }
    return "0x" + decodedAddress.substring(2).toLowerCase();
  }

  public static String convertEthAddressToTronAddress(String address) {
    if (StringUtils.isBlank(address)) {
      return null;
    }
    byte[] addressOrigin = ByteArray.fromHexString(address);

    return TronUtils.encode58Check(convertToTronAddress(addressOrigin));
  }
}


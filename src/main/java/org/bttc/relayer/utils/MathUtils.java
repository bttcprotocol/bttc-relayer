package org.bttc.relayer.utils;

import java.math.BigInteger;

/**
 *
 * @author tron
 */
public class MathUtils {

  private MathUtils() {
  }

  /**
   * Convert hexadecimal numbers to decimal numbers
   *
   * @param numberIn16Radix Hexadecimal number, start with 0x
   * @return converted to decimal number, long type
   */
  public static long convertTo10Radix(String numberIn16Radix) {
    if (numberIn16Radix.startsWith("0x")) {
      numberIn16Radix = numberIn16Radix.substring(2);
    }
    return new BigInteger(numberIn16Radix, 16).longValueExact();
  }

  /**
   * Convert hexadecimal numbers to decimal numbers
   *
   * @param numberIn16Radix Hexadecimal number, need to start with 0x
   * @return Converted to decimal number, string format
   */
  public static String convertTo10RadixInString(String numberIn16Radix) {
    if (numberIn16Radix.startsWith("0x")) {
      numberIn16Radix = numberIn16Radix.substring(2);
    }
    return new BigInteger(numberIn16Radix, 16).toString(10);
  }

  /**
   * Convert decimal number to Hexadecimal number
   *
   * @param numberIn10Radix decimal number
   * @return hexadecimal number, starting with 0x, in string format
   */
  public static String convertTo16RadixInString(String numberIn10Radix) {
    return "0x" + new BigInteger(numberIn10Radix, 10).toString(16);
  }

  /**
   * Convert ascii to hexadecimal
   * @param input ascii code
   * @return return hexadecimal String
   */
  public static String asciiToHexString(String input) {
    StringBuilder str = new StringBuilder();
    for(int i = 0; i < input.length(); i++) {
      int number = input.charAt(i);
      String hex = Integer.toHexString(number);
      str.append(hex);
    }
    return str.toString();
  }
}

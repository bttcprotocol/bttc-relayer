package org.bttc.relayer.utils;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.Ints;
import com.google.protobuf.ByteString;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.tron.common.utils.ByteArray;

/**
 * A Sha256Hash just wraps a byte[] so that equals and hashcode work correctly, allowing it to be *
 * used as keys in a map. It also checks that the length is correct and provides a bit more type *
 * safety.
 *
 * @author tron
 */

public class Sha256Hash implements Serializable, Comparable<Sha256Hash> {

  private static final long serialVersionUID = 1L;

  /**
   * bytes
   */
  public static final int LENGTH = 32;

  private final byte[] bytes;

  private long blockNum;

  public long getBlockNum() {
    return blockNum;
  }

  public Sha256Hash(byte[] rawHashBytes) {
    checkArgument(rawHashBytes.length == LENGTH);
    this.bytes = rawHashBytes;
  }

  /**
   * Returns a new SHA-256 MessageDigest instance. This is a convenience method which wraps the
   * checked exception that can never occur with a RuntimeException.
   *
   * @return a new SHA-256 MessageDigest instance
   */
  @SuppressWarnings("squid:S112")
  public static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // Can't happen.
      throw new RuntimeException(e);
    }
  }

  /**
   * Calculates the SHA-256 hash of the given bytes.
   *
   * @param input the bytes to hash
   * @return the hash (in big-endian order)
   */
  public static byte[] hash(byte[] input) {
    return hash(input, 0, input.length);
  }

  /**
   * Calculates the SHA-256 hash of the given byte range.
   *
   * @param input  the array containing the bytes to hash
   * @param offset the offset within the array of the bytes to hash
   * @param length the number of bytes to hash
   * @return the hash (in big-endian order)
   */
  public static byte[] hash(byte[] input, int offset, int length) {
    MessageDigest digest = newDigest();
    digest.update(input, offset, length);
    return digest.digest();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Sha256Hash)) {
      return false;
    }
    return Arrays.equals(bytes, ((Sha256Hash) o).bytes);
  }

  @Override
  public String toString() {
    return ByteArray.toHexString(bytes);
  }

  /**
   * Returns the last four bytes of the wrapped hash. This should be unique enough to be a suitable
   * hash code even for blocks, where the goal is to try and get the first bytes to be zeros (i.e.
   * the value as a big integer lower than the target value).
   */
  @Override
  public int hashCode() {
    // Use the last 4 bytes, not the first 4 which are often zeros in Bitcoin.
    return Ints.fromBytes(bytes[LENGTH - 4], bytes[LENGTH - 3], bytes[LENGTH - 2],
        bytes[LENGTH - 1]);
  }

  /**
   * Returns the internal byte array, without defensively copying. Therefore do NOT modify the
   * returned array.
   */
  public byte[] getBytes() {
    return bytes;
  }

  /**
   * For pb return ByteString.
   */
  public ByteString getByteString() {
    return ByteString.copyFrom(bytes);
  }

  @Override
  public int compareTo(final Sha256Hash other) {
    for (int i = LENGTH - 1; i >= 0; i--) {
      final int thisByte = this.bytes[i] & 0xff;
      final int otherByte = other.bytes[i] & 0xff;
      if (thisByte > otherByte) {
        return 1;
      }
      if (thisByte < otherByte) {
        return -1;
      }
    }
    return 0;
  }
}


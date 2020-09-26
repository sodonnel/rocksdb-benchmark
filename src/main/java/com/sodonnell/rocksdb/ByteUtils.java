package com.sodonnell.rocksdb;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Random;

public class ByteUtils {

  public static byte[] longToBytes(long l) {
    byte[] result = new byte[8];
    for (int i = 7; i >= 0; i--) {
      result[i] = (byte)(l & 0xFF);
      l >>= 8;
    }
    return result;
  }

  public static byte[] longToBytesWithPadding(long l, int padLength) {
    byte[] bytes = new byte[padLength + Long.BYTES];
    new Random().nextBytes(bytes);
    byte[] val = longToBytes(l);
    for (int i=0; i<Long.BYTES; i++) {
      bytes[i] = val[i];
    }
    return bytes;
  }

  public static long bytesToLong(final byte[] bytes, final int offset) {
    long result = 0;
    for (int i = offset; i < Long.BYTES + offset; i++) {
      result <<= Long.BYTES;
      result |= (bytes[i] & 0xFF);
    }
    return result;
  }

  public static byte[] dirBytes(long id, String dir) {
    byte[] longBytes = ByteUtils.longToBytes(id);
    byte[] stringBytes;
    try {
      stringBytes = dir.getBytes("UTF-16BE");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
    byte[] result = Arrays.copyOf(longBytes, longBytes.length + stringBytes.length);
    System.arraycopy(stringBytes, 0, result, longBytes.length, stringBytes.length);
    return result;
  }

}

package com.sodonnell.rocksdb.generate;

import com.sodonnell.rocksdb.ByteUtils;

public class GenerateDataFlatBufferLong extends GenerateDataWithFlatBuffer {

  public GenerateDataFlatBufferLong(String basePath, String tableName, int dirsPerLevel, int levels) {
    super(basePath, tableName, dirsPerLevel, levels);
  }

  @Override
  // Take the flatbuffer output, and write the long as the first
  // 8 bytes. This means we have the same data length as the flatbuffer,
  // but we will not use it - its all just padding except the first 8 bytes.
  public byte[] generateValue(long inodeID) {
    byte[] bytes = super.generateValue(inodeID);
    byte[] id = ByteUtils.longToBytes(inodeID);
    for (int i=0; i<id.length; i++) {
      bytes[i] = id[i];
    }
    return bytes;
  }
}

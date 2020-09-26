package com.sodonnell.rocksdb.generate;

import com.sodonnell.rocksdb.ByteUtils;

public class GenerateDataWithProtoLong extends GenerateDataWithProto {

  public GenerateDataWithProtoLong(String basePath, String tableName, int dirsPerLevel, int levels) {
    super(basePath, tableName, dirsPerLevel, levels);
  }

  @Override
  // Take the proto output, and write the long as the first
  // 8 bytes. This means we have the same data length as the proto,
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

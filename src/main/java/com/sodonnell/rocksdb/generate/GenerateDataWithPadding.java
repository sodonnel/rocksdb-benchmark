package com.sodonnell.rocksdb.generate;

import com.sodonnell.rocksdb.ByteUtils;

public class GenerateDataWithPadding extends GenerateData {

  int paddingLength = 0;

  public GenerateDataWithPadding(String basePath, String tableName, int dirsPerLevel, int levels, int paddingLength) {
    super(basePath, tableName, dirsPerLevel, levels);
    this.paddingLength = paddingLength;
  }

  @Override
  public byte[] generateValue(long inodeID) {
    return ByteUtils.longToBytesWithPadding(inodeID, paddingLength);
  }
}

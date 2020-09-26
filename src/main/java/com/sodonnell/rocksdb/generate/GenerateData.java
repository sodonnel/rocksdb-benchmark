package com.sodonnell.rocksdb.generate;

import com.sodonnell.rocksdb.ByteUtils;
import com.sodonnell.rocksdb.RocksDBTable;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;

public class GenerateData {

  private long counter = 0;
  public static String dirPrefix = "/abcdefghifklmno";
  protected WriteBatch rocksBatch;
  protected RocksDBTable rocksTable;
  protected int dirsPerLevel;
  protected int levels;

  public GenerateData(String basePath, String tableName, int dirsPerLevel, int levels) {
    this.rocksTable = new RocksDBTable(basePath+"/"+tableName, 8);
    rocksBatch = new WriteBatch();
    this.dirsPerLevel = dirsPerLevel;
    this.levels = levels;
  }

  public void generate()
      throws RocksDBException {
    gen(0, this.dirsPerLevel, this.levels, 1);
    commitBatch(true);
    rocksBatch.close();
    System.out.println("Final counter value is "+ counter);
  }

  // Override this in sub-classes to generate a different value
  public byte[] generateValue(long inodeID) {
    return ByteUtils.longToBytes(inodeID);
  }

  private void gen(long myId, int perLevel, int levels, int currentLevel)
      throws RocksDBException {
    if (currentLevel > levels) {
      return;
    }
    for (int j=0; j<perLevel; j++) {
      long nextId = ++counter;
      // TODO - Should be %2 in format for length 01, 02 etc.
      String dirName = String.format(dirPrefix+"%01d", j);
      rocksBatch.put(ByteUtils.dirBytes(myId, dirName), generateValue(counter));
      commitBatch(false);
      gen(nextId, perLevel, levels,currentLevel+1);
    }
  }

  private void commitBatch(boolean force) {
    if (force || rocksBatch.getDataSize() > 1024*1024*10) {
      System.out.println("Batch size is "+ rocksBatch.getDataSize() + " Writing it out");
      rocksTable.putBatch(rocksBatch);
      rocksBatch.clear();
    }
  }
}

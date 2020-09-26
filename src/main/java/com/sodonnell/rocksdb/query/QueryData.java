package com.sodonnell.rocksdb.query;

import com.sodonnell.rocksdb.ByteUtils;
import com.sodonnell.rocksdb.RocksDBTable;
import com.sodonnell.rocksdb.generate.GenerateData;
import org.rocksdb.RocksDBException;

import java.util.concurrent.ThreadLocalRandom;

public class QueryData {

  private RocksDBTable rocksTable;

  public QueryData(RocksDBTable table) {
    this.rocksTable = table;
  }

  public void close() throws Exception {
    if (rocksTable != null) {
      rocksTable.close();
    }
  }

  // Starting at the root, will walk the directory until no more entries are
  // found. Returns the number of entries found. The actual number of lookups
  // will be one greater as it must do a lookup to find "nothing".
  public int walkRandom(int min, int max) throws RocksDBException {
    String dirName = randomDirName(min, max);
    byte[] key = ByteUtils.dirBytes(0L, dirName);
    int steps = 0;
    while (true) {
      byte[] val = rocksTable.find(key);
      if (val == null) {
      //  System.out.println("Next is null");
        break;
      }
      long next = findNextId(val);
     // System.out.println("Found "+next);
      key = ByteUtils.dirBytes(next, randomDirName(min, max));
      steps ++;
    }
    return steps;
  }

  protected long findNextId(byte[] buf) {
 //   java.nio.ByteBuffer buff = java.nio.ByteBuffer.wrap(buf);
    return ByteUtils.bytesToLong(buf, 0);
  }

  private String randomDirName(int min, int max) {
    int randomNum = ThreadLocalRandom.current().nextInt(min, max);
    // TODO - This should be %2 for length of 2.
    return GenerateData.dirPrefix+randomNum; //String.format(dirPrefix+"%01d", randomNum);
  }

  private String padInt(int i) {
    if (i<=9) {
      return "0"+(i);
    } else {
      return Integer.toString(i);
    }
  }

}

package com.sodonnell.rocksdb;

import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class RocksDBTable {
  File dbDir;
  RocksDB db;
  LRUCache blockCache;
  Statistics statistics;

  Logger log = LoggerFactory.getLogger(RocksDBTable.class);

  public RocksDBTable(String rocksDBDir, int cacheMB) {
    initialize(rocksDBDir, cacheMB);
  }

  void initialize(String path, int cacheMB) {
    RocksDB.loadLibrary();
    final Options options = new Options();
    BlockBasedTableConfig tableOptions = new BlockBasedTableConfig();
    blockCache = new LRUCache(cacheMB*SizeUnit.MB);
    tableOptions.setBlockCache(blockCache);

    statistics = new Statistics();

    options.setCreateIfMissing(true)
        .setTableFormatConfig(tableOptions)
        .setStatistics(statistics);
        //.setCompressionType(CompressionType.NO_COMPRESSION);


    dbDir = new File(path);
    try {
      Files.createDirectories(dbDir.getParentFile().toPath());
      Files.createDirectories(dbDir.getAbsoluteFile().toPath());
      db = RocksDB.open(options, dbDir.getAbsolutePath());
    } catch(IOException | RocksDBException ex) {
      log.error("Error initializing RocksDB, check configurations and permissions", ex);
    }
    log.info("RocksDB initialized and ready to use");
  }

  //public void printStats() {
  //  statistics.getTickerCount(TickerType)
  //  statistics.getHistogramString();
  //  new RocksDB.CountAndSize();
  //}

  public void put(String key, String value) {
    try {
      db.put(key.getBytes(), value.getBytes());
    } catch (RocksDBException e) {
      log.error("Error saving entry in RocksDB", e);
    }
  }

  public void putBatch(WriteBatch batch) {
    try {
      db.write(new WriteOptions(), batch);
    } catch (RocksDBException e) {
      log.error("Error saving batch in RocksDB, cause: {}, message: {}", e.getCause(), e.getMessage());
    }
  }

  public byte[] find(byte[] key) throws RocksDBException {
    try {
      return db.get(key);
    } catch (RocksDBException e) {
      log.error("Error retrieving the entry in RocksDB from key: {}, cause: {}, message: {}", key, e.getCause(), e.getMessage());
      throw e;
    }
  }

  public void close() {
    db.close();
  }

}

package com.sodonnell.rocksdb.query;

import com.sodonnell.rocksdb.RocksDBTable;
import com.sodonnell.rocksdb.generate.DataGenerator;

public class Query {

  private int dirsPerLevel;
  private int levels;
  private int rocksCacheMB;
  private String basePath;

  public Query(String basePath, int dirsPerLevel, int levels, int cache) {
    this.basePath = basePath;
    this.dirsPerLevel = dirsPerLevel;
    this.levels = levels;
    this.rocksCacheMB = cache;
  }

  public void queryForType(DataGenerator.DB_TYPE type) throws Exception {
    System.out.println("Querying data for "+type);
    QueryData query = getQueryObject(type);

    while(true) {
      int res = query.walkRandom(0, dirsPerLevel);
      if (res != levels) {
        System.out.println("Expected to walk "+levels+" levels but only walked "+res);
        break;
      }
    }
  }

  public QueryData getQueryObject(DataGenerator.DB_TYPE type) throws Exception {
    RocksDBTable t = getRocksDBTableForType(type);
    if (type.equals(DataGenerator.DB_TYPE.LONG)
        || type.equals(DataGenerator.DB_TYPE.PADDING_50)
        || type.equals(DataGenerator.DB_TYPE.PADDING_100)
        || type.equals(DataGenerator.DB_TYPE.PADDING_150)
        || type.equals(DataGenerator.DB_TYPE.PADDING_200)
        || type.equals(DataGenerator.DB_TYPE.PADDING_250)) {
      return new QueryData(t);
    } else if (type.equals(DataGenerator.DB_TYPE.FLAT_BUFFER)) {
      return new QueryDataFlatBuffer(t);
    } else if (type.equals(DataGenerator.DB_TYPE.FLAT_BUFFER_LONG)) {
      return new QueryData(t);
    } else if (type.equals(DataGenerator.DB_TYPE.PROTO)) {
      return new QueryDataProto(t);
    } else if (type.equals(DataGenerator.DB_TYPE.PROTO_LONG)) {
      return new QueryData(t);
    } else {
      throw new Exception("unknown db type");
    }
  }

  public RocksDBTable getRocksDBTableForType(DataGenerator.DB_TYPE type) {
    return new RocksDBTable(basePath+"/"+type.toString(), rocksCacheMB);
  }

  public static void main(String[] args) {
    if (args.length < 5) {
      System.out.println("Usage: Query /base/path/of/rocks/dbs dirs_per_level levels <DB_TYPE> cacheMB");
    }
    String base = args[0];
    int dirsPerLevel = Integer.parseInt(args[1]);
    int levels = Integer.parseInt(args[2]);
    DataGenerator.DB_TYPE table = DataGenerator.DB_TYPE.valueOf(args[3]);
    int cache = Integer.parseInt(args[4]);

    Query q = new Query(base, dirsPerLevel, levels, cache);
    try {
      q.queryForType(table);
    } catch (Exception e) {
      System.out.println("Exception! "+ e.getMessage() + " " + e.getCause());
    }
  }



}

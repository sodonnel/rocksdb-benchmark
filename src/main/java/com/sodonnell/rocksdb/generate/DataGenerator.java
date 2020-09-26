package com.sodonnell.rocksdb.generate;

public class DataGenerator {

  public enum DB_TYPE {
    LONG,
    FLAT_BUFFER,
    FLAT_BUFFER_LONG,
    PROTO,
    PROTO_LONG,
    PADDING_50,
    PADDING_100,
    PADDING_150,
    PADDING_200,
    PADDING_250
  }

  private int dirsPerLevel;
  private int levels;
  private String basePath;

  public DataGenerator(String basePath, int dirsPerLevel, int levels) {
    this.basePath = basePath;
    this.dirsPerLevel = dirsPerLevel;
    this.levels = levels;
  }

  public void generateForType(DB_TYPE type) throws Exception {
    System.out.println("Generating data for "+type);
    GenerateData gen = getGenerator(type);
    gen.generate();
  }

  public void generateAll() throws Exception {
    for (DB_TYPE t : DB_TYPE.values()) {
      generateForType(t);
    }
  }

  public GenerateData getGenerator(DB_TYPE type) throws Exception {
    if (type.equals(DB_TYPE.LONG)) {
      return new GenerateData(basePath, type.toString(), dirsPerLevel, levels);
    } else if (type.equals(DB_TYPE.FLAT_BUFFER)) {
      return new GenerateDataWithFlatBuffer(basePath, type.toString(), dirsPerLevel, levels);
    } else if (type.equals(DB_TYPE.FLAT_BUFFER_LONG)) {
      return new GenerateDataFlatBufferLong(basePath, type.toString(), dirsPerLevel, levels);
    } else if (type.equals(DB_TYPE.PROTO)) {
      return new GenerateDataWithProto(basePath, type.toString(), dirsPerLevel, levels);
    } else if (type.equals(DB_TYPE.PROTO_LONG)) {
      return new GenerateDataWithProtoLong(basePath, type.toString(), dirsPerLevel, levels);
    } else if (type.equals(DB_TYPE.PADDING_50)) {
      return new GenerateDataWithPadding(basePath, type.toString(), dirsPerLevel, levels, 42);
    } else if (type.equals(DB_TYPE.PADDING_100)) {
      return new GenerateDataWithPadding(basePath, type.toString(), dirsPerLevel, levels, 92);
    } else if (type.equals(DB_TYPE.PADDING_150)) {
      return new GenerateDataWithPadding(basePath, type.toString(), dirsPerLevel, levels, 142);
    } else if (type.equals(DB_TYPE.PADDING_200)) {
      return new GenerateDataWithPadding(basePath, type.toString(), dirsPerLevel, levels, 192);
    } else if (type.equals(DB_TYPE.PADDING_250)) {
      return new GenerateDataWithPadding(basePath, type.toString(), dirsPerLevel, levels, 242);
    } else {
      throw new Exception("unknown db type");
    }
  }

  public static void main(String[] args) {
    if (args.length < 3) {
      System.out.println("Usage: DataGenerator /path/of/rocks/dbs dirs_per_level levels <DB_TYPE>");
    }
    String base = args[0];
    int dirsPerLevel = Integer.parseInt(args[1]);
    int levels = Integer.parseInt(args[2]);

    DB_TYPE generateOnly = null;
    if (args.length >= 4) {
      generateOnly = DataGenerator.DB_TYPE.valueOf(args[3]);
    }

    DataGenerator d = new DataGenerator(base, dirsPerLevel, levels);
    try {
      if (generateOnly != null) {
        d.generateForType(generateOnly);
      } else {
        d.generateAll();
      }
    } catch (Exception e) {
      System.out.println("Exception! "+ e.getMessage() + " " + e.getCause());
    }
  }

}

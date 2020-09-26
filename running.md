# Generate Data

The first step is to generate the test data. Build the project with `mvn package` and then run:

    java com.sodonnell.rocksdb.generate.DataGenerator /path/to/rocksdb dirs_per_level levels <Table to generate>

The "Table to Generate" is optional. The allowed options are controlled by the enum in DataGenerator.DB_TYPE:


```
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
```

In the benchmarks I passed 5 for `dirs_per_level` and 10 for `levels`. This will generate about 12M entries in each Rocks DB and should complete in about 10 minutes.


# Query Data

To query the generated data, by traversing a random series of directories and sub-directories, run:

    java com.sodonnell.rocksdb.query.Query /path/to/rocksdb dirs_per_level levels Table_to_Query

You should pass the same value for `dirs_per_level` and `levels` as used in the Generate step. Running the above does not provide any output, but provided the table will be queried for random entries forever. Based on the value passed for `levels`, the query tool expects that may rocksDB looks to be performed per query, and if there are not, it will throw an exception. The purpose of this tool, is to validate the tables are being queried OK, and to allow flame charts to be captured.

# Benchmarking

There are two JMH benchmark classes:

1. `com.sodonnell.rocksdb.benchmark.BenchmarkSerialize` - it can be used without generating any data, and just compares Flatbuffers and Proto operations.
1. `com.sodonnell.rocksdb.benchmark.BenchmarkDirectoryWalk` - is used to query the previously generated tables.

To run them, simply run the class, and also pass the classname as a parameter:

    java com.sodonnell.rocksdb.benchmark.BenchmarkSerialize BenchmarkSerialize

If you don't pass the classname as a parameter, all JMH annotated classes will be run.

Note that for `BenchmarkDirectoryWalk` the path to the RocksDB base directory is hardcoded as `/tmp/rocksdb` and the tables which are queries are hardcoded as:

    @Param({"FLAT_BUFFER_LONG", "FLAT_BUFFER", "PROTO", "PROTO_LONG"})

Edit these values in the class and recompile if they need to be changed.

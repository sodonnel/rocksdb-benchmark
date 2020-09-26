package com.sodonnell.rocksdb.benchmark;

import com.sodonnell.rocksdb.generate.DataGenerator;
import com.sodonnell.rocksdb.query.Query;
import com.sodonnell.rocksdb.query.QueryData;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class BenchmarkDirectoryWalk {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
   /// @Param({"LONG", "PADDING_50", "PADDING_100", "PADDING_150", "PADDING_200", "PADDING_250"})
    @Param({"FLAT_BUFFER_LONG", "FLAT_BUFFER", "PROTO", "PROTO_LONG"})
    public String tableName;

    int cacheSize = 4096;
    public QueryData queryData;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
      if (queryData != null) {
        queryData.close();
      }
      System.out.println("Getting a new query object");
      Query q = new Query("/tmp/rocksdb", 5, 10, cacheSize);
      queryData = q.getQueryObject(DataGenerator.DB_TYPE.valueOf(tableName));
    }
  }


  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 60, time = 2000, timeUnit = MILLISECONDS)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 20, time = 2000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void walkRandomDirectory(Blackhole blackhole, BenchmarkState state) throws Exception {
    int steps = state.queryData.walkRandom(0, 5);
    if (steps != 10) {
      throw new Exception("Expected 10 steps but only got "+steps);
    }
    blackhole.consume(1);
  }
}

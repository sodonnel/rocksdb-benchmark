# Benchmarking a RocksDB Use Case

In [Ozone](https://hadoop.apache.org/ozone/), we have a use case for RocksDB, where we want to store a filesystem directory hierarchy in a RocksDB table. RocksDB is a key value store, so we store a directory name along with its parent iNode ID as the key, and the value is a serialised object holding details about the directory / object. For example:

```
0/dir1 -> { inodeID: 1, parentID: 0, ctime, mtime, name, owner, group, permissions, [ACLs] }
0/dir2 -> { inodeID: 2, parentID: 0, ctime, mtime, name, owner, group, permissions, [ACLs] }
...
1/dir1 -> { inodeID: 3, parentID: 1, ctime, mtime, name, owner, group, permissions, [ACLs] }
1/dir2 -> { inodeID: 4, parentID: 1, ctime, mtime, name, owner, group, permissions, [ACLs] }
```

The above shows two directories at root - `/dir1`, with `ID=1` and `/dir2` with `ID=2`. Then `dir1`, has 2 subdirectories `/dir1/dir1`, `/dir1/dir2` and so on.

To walk a given directory tree, eg `/dir1/dir2/dir3`, you start at the root and look for `0/dir1`, then from the value, get the iNodeID, and look for `ID/dir2`, then `ID/dir3`. This requires 3 rocksDB lookups. This means that for very deep directory hierarchies, many rocksDB lookups will be required.

For this exercise, the write overhead is not much of a concern, as the data is likely to be written and then read many times.

This repo contains the code used to perform the benchmarks, with the results outlined in this document.

## Benchmarking Hardware

The hardware used to run all these tests is a 32 core Xenon box, with 256MB RAM and spinning disk:

```
$ lscpu
Architecture:          x86_64
CPU op-mode(s):        32-bit, 64-bit
Byte Order:            Little Endian
CPU(s):                32
On-line CPU(s) list:   0-31
Thread(s) per core:    2
Core(s) per socket:    8
Socket(s):             2
NUMA node(s):          2
Vendor ID:             GenuineIntel
CPU family:            6
Model:                 63
Model name:            Intel(R) Xeon(R) CPU E5-2630 v3 @ 2.40GHz
Stepping:              2
CPU MHz:               1204.101
CPU max MHz:           3200.0000
CPU min MHz:           1200.0000
```

## Test Object Definition

The schema of the object we are storing, defined as a Protobuf schema is:

```
enum AclScope {
  ACCESS  = 0;
  DEFAULT = 1;
}

enum AclType {
  USER = 0;
  GROUP = 1;
  MASK = 2;
  OTHER = 3;
}

message Acl {
  required string name = 1;
  required AclType type = 2;
  required AclScope scope = 3;
  required int32 permissions = 4;

}

message DirectoryInfo {
  required uint64 objectId = 1;
  required uint64 updateId = 2;
  required uint64 parentId = 3;
  required uint64 creationTime = 4;
  required uint64 modificationTime = 5;
  required string name = 6;
  required string owner = 7;
  required string group = 8;
  required int32 permission = 9;
  repeated Acl acls = 10;
  }
```

There is a [similar schema for Flatbuffer](src/flatbuffers/schema.fbs). For each directory created, the name will be "/abcdefghifklmno", user as "sodonnell", group is "hadoop-read-write". The current system time is set as the ctime and mtime. Two ACLs are also created for each directory - with a username of "otheruser".


## Benchmarking Serialisation

RocksDB simply stores bytes for the key and value, so the data needs to be serialised somehow. The key is simple. It is an 8 byte long, with the remaining bytes representing a string for the directory name. You don't actually need to store the leading slash shown above, but here it is stored.

The value is more complex, and has potential to change over time. We have a mixture of longs, a String and a list of ACLs, which are effectively unix like permissions.

Rather than invent our own serialisation scheme, it makes sense to use something like [Flatbuffers](https://google.github.io/flatbuffers/) or [Protobuffers](https://developers.google.com/protocol-buffers). Ozone (and HDFS) make use of Protobuffers extensively. Flatbuffers are documented as being much faster so, seems like a good idea to test which is faster.

Both these technologies read data from a byte array, but the reason Flatbuffers [claim to be faster](https://google.github.io/flatbuffers/flatbuffers_benchmarks.html), is because it does not need to parse the full byte array into an object to access the fields. When you call a getter on the object, the byte array is accessed directly to retrieve the value. Protobuf, parses the byte array into a new object to provide access to it.

This leads to some interesting benchmarks to consider:

1. Time to access a single field from the encoded byte array. Does the data type matter?
1. Time to access all fields from the encoded byte array.
1. Accessing the same field over and over.

Running some tests using JMH, we see:

```
Benchmark                                             Mode  Cnt         Score         Error  Units
BenchmarkSerialize.accessSingleLongFlat              thrpt    8  43528096.725 ± 8881946.994  ops/s
BenchmarkSerialize.accessSingleLongProto             thrpt    8   1957758.265 ±  492931.100  ops/s

BenchmarkSerialize.accessSingleStringFlat            thrpt    8  13476114.876 ± 3227921.249  ops/s
BenchmarkSerialize.accessSingleStringProto           thrpt    8   1590686.599 ±  236364.496  ops/s

BenchmarkSerialize.accessAllFieldsFlat               thrpt    8   1948135.425 ±  231217.283  ops/s
BenchmarkSerialize.accessAllFieldsProto              thrpt    8   1046136.184 ±  206602.503  ops/s

BenchmarkSerialize.accessSingleLongManyTimesFlat     thrpt    8   3651160.787 ±  805372.759  ops/s
BenchmarkSerialize.accessSingleLongManyTimesProto    thrpt    8   1620143.916 ±  431538.834  ops/s

BenchmarkSerialize.accessSingleStringManyTimesFlat   thrpt    8    777953.384 ±  110672.032  ops/s
BenchmarkSerialize.accessSingleStringManyTimesProto  thrpt    8   1471848.162 ±  212832.198  ops/s
```

Both technologies offer impressive performance. Accessing a single long value, Flatbuffer is about 22 times faster than Proto. However when accessing a single String it is only about 8 times faster. So the type of data certainly makes a difference.

When comparing the throughput accessing all fields in the object, which is a more realistic test, Flafbuffers are still about 1.8 times faster.

As we understand that Flatbuffers do not have to parse the entire byte array into an object to provide access, it makes sense that accessing a single field would be faster, and the performance gap narrows as the number of fields are accessed. We can see this when accessing a single long 20 times in a loop. The performance gap drops from 22 times faster to only 2.25 times faster.

Accessing a single string over and over, Protobuf is about twice as fast. This makes sense, as Protobuf will convert the bytes to a string once, but Flatbuffers will need to do it over and over again. It seems that converting bytes to a string is more expensive that bytes to a long.

A typical use case, is to take the serialised bytes, and convert them into a rich Java object. Therefore Proto must go from bytes to a intermediate object to final object, which is 3 copies of the data. Flatbuffers will wrap the bytes in a slim accessor object and hence should save some memory and GC overhead and require only 2 copies. If you are creating short lived objects, with both technologies, the richer object could simple wrap the Flatbuffer / Proto object and proxy method calls accordingly.

### Serialised Size

In my testing, and also noted on the [Flatbuffer benchmarks](https://google.github.io/flatbuffers/flatbuffers_benchmarks.html), the size of serialised Flatbuffers are significantly larger than Proto, which eliminates much of the memory saved by avoiding copies. The serialised flatbuffer is 240 bytes, while proto is 128. Some of the difference in my test is due to variable length encoding of longs with Proto (which Flatbuffer does not seem to support), as most of my IDs are small enough to fit in an Integer. Even taking that into account, Proto is still much smaller.


## A Directory Hierarchy DataSet

In order to test random reads from RocksDB for this use case, we need a dataset is query. One can be generated such that we have a directory hierarchy which has N directories per level and is L levels deep. Starting at root we have level 1 which is:

```
/dir1
/dir2
...
/dirN
```

Then in each of the directories, we create 5 sub-directories to create level 2 and repeat up to level 10:

```
/dir1/dir1
/dir1/dir2
...
/dir1/dirN
...
/dirN/dir1
...
/dirN/dirN
```

This generates about 12M entries. You can then walk the directory tree by picking a random integer between 1 and N at each level until no result is found, which gives 11 rocksDBs lookups per traverse - 10 returning a value and one returning null. This should also scatter reads all over the table, making this a worst case test. Normal application usage would tend to cluster around certain directory paths at a given time.

The key is in the format:

```
<8 byte long></abcdefghifklmno><string integer from 0 - N-1>

For example:

12345/abcdefghifklmno0
12345/abcdefghifklmno1
etc
```

Java encodes a string using 2 bytes, so that gives us

```
8 + (17 * 2) + (1 * 2) = 44 bytes
```

Then we have different data values to test how each performs:

1. A single 8 byte long for the ID of the directory object so we can walk the parent/child relationship.

2. The same long, padded with random bytes up to 50, 100, 150, 200 and 250 bytes, to make the rocksDB larger on disk.

3. Directory meta-data encoded as a Flatbuffer, which is about 240 bytes.

4. The same as above, but with the directory ID overwriting the first 8 bytes - this makes the reset effectively padding, but less random than before.

5. Directory meta-data encoded as Protobuf, which is about 128 bytes.

6. The same as above, but with the directory ID overwriting the first 8 bytes

The on disk size of each of these looks like:

```
89M 	mydbs/LONG
652M	mydbs/PADDING_50
1.4G	mydbs/PADDING_100
2.1G	mydbs/PADDING_150
2.6G	mydbs/PADDING_200
3.2G	mydbs/PADDING_250
527M    mydbs/FLAT_BUFFER
554M	mydbs/FLAT_BUFFER_LONG
271M	mydbs/PROTO
278M	mydbs/PROTO_LONG
```

What is notable, is the tables padded with random bytes are much larger on disk, probably as the random bytes do not compress well. Repeated values in the other tables, will compress much better. When RocksDB loads a block from disk into the cache, I believe it needs to decompress the block, so the in-memory size of the tables will be similar in some cases.

## Benchmarking Data Set Queries

Running a benchmark to query the LONG and PADDING tables, selecting random entries, shows the larger the data size the fewer the ops per second. For the larger tables, there is a significant ramp up time, as RocksDB loads the blocks into its cache before it reaches peak performance. The tests were run with about 120 seconds of warmup time, before 20, 2 second intervals of measurement. A RocksDB cache size of 4GB was used:

```
Benchmark                                   (tableName)   Mode  Cnt      Score      Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory         LONG  thrpt   20  26601.670 ±  576.702  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory   PADDING_50  thrpt   20  24035.957 ±  656.720  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory  PADDING_100  thrpt   20  20128.386 ±  511.258  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory  PADDING_150  thrpt   20  18736.288 ± 1456.529  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory  PADDING_200  thrpt   20  18540.870 ±  593.655  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory  PADDING_250  thrpt   20  17698.622 ±  549.815  ops/s
```

In these tests, the RSS size of the process was less than the 4GB allocated to the RocksDB cache, suggesting most of table ended up cached. We can clearly see that with increasing data size, the performance gets worse, which is not surprising.

### Flatbuffer vs Protobuf Throughput

Again testing with a 4GB RocksDB cache, we can see the relative performance of Flatbuffers vs Protobuf. Note that in this test, only a single value is accessed from the deserialised object, namely the inode / objectID. As we saw previously, this is a use case which strongly favours Flatbuffers. However, we know the Flatbuffer message size is about 240 bytes, vs 128 for Proto, which means the RocksDB retrieval overhead for Proto is less. Each test was run 3 times:

```
Benchmark                                        (tableName)   Mode  Cnt      Score      Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory       FLAT_BUFFER  thrpt   20  19892.719 ± 1173.500  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory             PROTO  thrpt   20  17421.094 ± 1265.995  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory       FLAT_BUFFER  thrpt   20  19440.061 ± 1360.162  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory             PROTO  thrpt   20  18998.089 ±  655.703  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory       FLAT_BUFFER  thrpt   20  22582.332 ±  586.133  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory             PROTO  thrpt   20  19220.069 ±  911.395  ops/s
```

Flatbuffers are consistently faster, but not by a big margin.

### Serialisation Overhead

Next we look at querying the Flatbuffer / Proto data, but accessing only the first 8 bytes as a long. This lets us see the RockDB throughput and the impact of deserilization.

```
Benchmark                                        (tableName)   Mode  Cnt      Score      Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER_LONG  thrpt   20  20311.750 ±  466.508  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO_LONG  thrpt   20  23562.960 ±  571.923  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER_LONG  thrpt   20  20629.418 ±  534.751  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO_LONG  thrpt   20  21676.414 ±  842.962  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER_LONG  thrpt   20  21039.711 ±  278.339  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO_LONG  thrpt   20  22635.102 ± 1195.000  ops/s
```

Comparing to the PROTO_LONG table, Flatbuffers are slower. Remember this is effectively retrieving raw bytes, and the Flatbuffers are bigger (240 vs 128 bytes). However, if you compare the PROTO LONG time against plain PROTO, you can see the overhead deserialising the Protobuf message adds. The difference for Flatbuffers is almost insignificant.

### Undersized Cache

In the previous tests, the RSS size of a process during the Flatbuffer run was 3.7GB. For Proto it was 2.3G, so the additional size of Flatbuffers has a memory overhead.

Repeating the same tests as above with a 2GB cache size, illustrates the importance of the working set fitting in the RocksDB cache:

```
Benchmark                                        (tableName)   Mode  Cnt      Score      Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER_LONG  thrpt   20  16606.838 ±  680.306  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory       FLAT_BUFFER  thrpt   20  16740.601 ± 1069.631  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory             PROTO  thrpt   20  19093.482 ±  752.140  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO_LONG  thrpt   20  23679.026 ±  326.160  ops/s
```

Here the performance of Flatbuffers is much worse due to the undersized RocksDB cache.

### GC Pressure

Adding the JMH options "-prof gc" we can see some counters related to memory allocations and GC in the JVM:

```
Benchmark                                                                    (tableName)   Mode  Cnt      Score      Error   Units
BenchmarkDirectoryWalk.walkRandomDirectory                                   FLAT_BUFFER  thrpt   20  19165.652 ±  502.527   ops/s
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate                    FLAT_BUFFER  thrpt   20     95.905 ±    2.513  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate.norm               FLAT_BUFFER  thrpt   20   6560.011 ±    0.001    B/op
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Eden_Space           FLAT_BUFFER  thrpt   20     96.244 ±   29.590  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Eden_Space.norm      FLAT_BUFFER  thrpt   20   6600.165 ± 2039.112    B/op
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Survivor_Space       FLAT_BUFFER  thrpt   20      0.004 ±    0.009  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Survivor_Space.norm  FLAT_BUFFER  thrpt   20      0.293 ±    0.586    B/op
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.count                         FLAT_BUFFER  thrpt   20     18.000             counts
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.time                          FLAT_BUFFER  thrpt   20     50.000                 ms
BenchmarkDirectoryWalk.walkRandomDirectory                                         PROTO  thrpt   20  17565.324 ±  815.648   ops/s
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate                          PROTO  thrpt   20    146.640 ±    6.810  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate.norm                     PROTO  thrpt   20  10944.012 ±    0.001    B/op
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Eden_Space                 PROTO  thrpt   20    146.695 ±   21.073  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Eden_Space.norm            PROTO  thrpt   20  10941.913 ± 1431.321    B/op
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Survivor_Space             PROTO  thrpt   20      0.032 ±    0.012  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.churn.PS_Survivor_Space.norm        PROTO  thrpt   20      2.446 ±    0.962    B/op
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.count                               PROTO  thrpt   20     54.000             counts
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.time                                PROTO  thrpt   20    136.000                 ms
```

The Protobuf option appears to be allocating about 146MB/s vs 95MB/s for Flatbuffers.

### Threads

Adjusting the concurrent threads per test, we can see how RocksDB scales as the concurrency increases.

With 4 threads:

```
Benchmark                                   (tableName)   Mode  Cnt      Score      Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  74076.533 ± 3895.812  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  66671.294 ± 1794.112  ops/s
```

With 8 threads:

```
Benchmark                                   (tableName)   Mode  Cnt       Score      Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  147274.923 ± 5651.788  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  132434.407 ± 1110.897  ops/s
```

With 12 threads:

```
Benchmark                                   (tableName)   Mode  Cnt       Score       Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  163044.490 ±  7334.344  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  152542.667 ± 10891.050  ops/s
```

Performance scales quite nicely up to 8 threads, but going to 12 does not add much more throughput. I repeated the test, giving the JVM 4GB of heap, but it did not improve things further. I did not investigate any possible multi-threaded improvements.

Keeping in mind, each operation here is actually 11 RocksDB accesses, the performance is approaching 1.5M calls per second to RocksDB on 8 threads!

### All fields

In previous tests, only a single field from the deserialised message was accessed. Flatbuffers were also generally faster. If we change the code to query all the fields, the throughput of both is similar. The numbers for this test were somewhat unstable so I ran the test 6 times:

```
Benchmark                                   (tableName)   Mode  Cnt      Score     Error  Units
BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  18043.146 ± 1048.627  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  18090.331 ±  406.580  ops/s

BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  17319.577 ± 490.428  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  16605.935 ± 391.834  ops/s

BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  17063.865 ± 1013.156  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  16832.735 ±  616.714  ops/s

BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  20314.930 ± 391.421  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  17479.318 ± 561.380  ops/s

BenchmarkDirectoryWalk.walkRandomDirectory  FLAT_BUFFER  thrpt   20  16027.535 ± 1284.672  ops/s
BenchmarkDirectoryWalk.walkRandomDirectory        PROTO  thrpt   20  17882.832 ±  603.958  ops/s

BenchmarkDirectoryWalk.walkRandomDirectory                      FLAT_BUFFER  thrpt   20  17767.053 ±  397.899   ops/s
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate       FLAT_BUFFER  thrpt   20    165.886 ±    3.714  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate.norm  FLAT_BUFFER  thrpt   20  12240.012 ±    0.001    B/op
BenchmarkDirectoryWalk.walkRandomDirectory                            PROTO  thrpt   20  16182.486 ±  423.608   ops/s
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate             PROTO  thrpt   20    181.508 ±    4.753  MB/sec
BenchmarkDirectoryWalk.walkRandomDirectory:·gc.alloc.rate.norm        PROTO  thrpt   20  14704.013 ±    0.001    B/op
```

Flatbuffers seem to be slightly faster and also allocate less memory in the JVM overall.

### Flame Charts

After starting a program to traverse the table over and over, and letting the RocksDB cache warm up, some flame charts were captured. The idea here is to see the time spent querying RockDBs against data deserialisation.

First we have Flatbuffers when accessing a single field in the message:

![Flatbuffer Single Field](flame_charts/flatbuffer_flame_single_field.svg?raw=true "Flatbuffer Single Field")


Here we see 94% of samples in `RocksDBTable.find(...)` and only 0.45% in `findNextId(...)`. This indicates the cost of deserialisation is very small.

Next, the same test using Protobuf, accessing a single field:

![Proto Single Field](flame_charts/proto_flame_single_field.svg?raw=true "Proto Single Field")

Here, the time in `RockDBTable.find(...)` falls to 84% and in 8.86% in `findNextId(...)`, indicating the cost of deserialisation is much higher with Protobuf.

When Flatbuffers are used and all fields in the test object are accessed, the following flame chart is produced:

![Flatbuffer All Fields](flame_charts/flatbuffer_flame_all_fields.svg?raw=true "Flatbuffer All Fields")

The time in `RockDBTable.find(...)` falls to 85.86% and in 7.74% in `findNextId(...)`, showing a higher cost of deserialisation when accessing all the fields.

Finally, we have Protobuf, accessing all fields:

![Proto All Fields](flame_charts/proto_flame_all_fields.svg?raw=true "Proto All Fields")

The time in `RockDBTable.find(...)` falls to 78.86% and in 13.18% in `findNextId(...)`. Even though protobuf has a large initial deserialisation overhead, there is still a significant overhead in accessing all the fields in the object and even in this use case it performs worse than Flatbuffers.

## Conclusion

Each "walkRandomDirectory" test accesses RocksDB 11 times. On 10 of the calls it will get a value and follow the pointer to the next value. On the 11th turn it will find null, completing one traverse.

On a single thread, provided the working set fits in the Rocks block cache, 18 - 20k calls per second are possible. Which is 198 - 220k RocksDB lookups per second.

The performance scales quite linearly to 8 threads, achieving 1.45 - 1.61M RocksDB accesses per second.

Depending on locking in the application, RocksDB is unlikely to be a bottleneck.

We also see the importance of keeping the serialised data as small as possible. The Protobuf message size requires less cache space, and less overhead when reading from RocksDB, but pays a price with higher deserialisation costs. In most tests, Flatbuffers were faster despite their larger size. Even for Protobuf, size matters, as a smaller message will be faster to parse.

In this "filesystem directory" use case, there are probably fields which don't need to be accessed in all use cases. For example, to check for the existence of a directory, ignoring permissions, owner etc, only the iNodeID needs to be accessed. Even permission checking will not need to check the ctime and mtime. In this test, all entries had 2 ACLs, and in real world usage, most directories will have no ACLs, making the serialised object smaller.


## Further Improvements

In the test, each ACL holds a byte each for the Type and Scope and 2 bytes for the permissions. This could be packed into a single byte saving 3 bytes per ACL.

In the DirectoryInfo object, we also use 8 bytes for the parentID, but this ID is also stored in the RocksDB key. Same for the target directory name, which is potentially the longest piece of information stored. If we removed those from the RocksDB value, we would save 44 bytes per entry.

These two changes could give a saving of 50 bytes per entry in the test, or about 20% of the flatbuffer size.

A further enhancement, could use a "strings table" to store the user and group strings, replacing them with an integer. The set of users and groups should be relatively small, and so this could make a significant space saving, but at the cost of several additional lookups per entry (possibly against a Java Map).


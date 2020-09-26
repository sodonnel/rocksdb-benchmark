package com.sodonnell.rocksdb.benchmark;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import com.sodonnell.rocksdb.flatbuffer.Acl;
import com.sodonnell.rocksdb.flatbuffer.AclScope;
import com.sodonnell.rocksdb.flatbuffer.AclType;
import com.sodonnell.rocksdb.flatbuffer.DirectoryInfo;
import com.sodonnell.rocksdb.proto.DirectoryInfoProtos;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.rocksdb.RocksDBException;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class BenchmarkSerialize {

  @State(Scope.Benchmark)
  public static class BenchmarkState {
    private long inodeID = 1234567890L;
    private long parentID = 9987654321L;
    private String directoryName = "thisIsTheDirectoryName";

    public byte[] protobytes;
    public byte[] flatbytes;

    public byte[] flatbufferObj() {
      FlatBufferBuilder builder = new FlatBufferBuilder(128);
      int nameOffset = builder.createString(directoryName);
      int ownerOffset = builder.createString("sodonnell");
      int groupOffset = builder.createString("hadoop-read-write");
      int aclUserOffset = builder.createString("otheruser");

      int[] aclOffsets = new int[2];
      aclOffsets[0] = Acl.createAcl(builder, aclUserOffset, AclType.USER, AclScope.DEFAULT, (short)7);
      aclOffsets[1] = Acl.createAcl(builder, aclUserOffset, AclType.USER, AclScope.ACCESS, (short)7);
      int aclsOffset = DirectoryInfo.createAclsVector(builder, aclOffsets);

      DirectoryInfo.startDirectoryInfo(builder);
      DirectoryInfo.addCreationTime(builder, System.currentTimeMillis());
      DirectoryInfo.addModificationTime(builder, System.currentTimeMillis());
      DirectoryInfo.addObjectId(builder, 1234567890L);
      DirectoryInfo.addParentId(builder, 9987654321L);
      DirectoryInfo.addUpdateId(builder, System.currentTimeMillis());
      DirectoryInfo.addName(builder, nameOffset);
      DirectoryInfo.addOwner(builder, ownerOffset);
      DirectoryInfo.addGroup(builder, groupOffset);
      DirectoryInfo.addPermissions(builder, (short)755);
      DirectoryInfo.addAcls(builder, aclsOffset);

      int dirInfo = DirectoryInfo.endDirectoryInfo(builder);
      builder.finish(dirInfo);
      byte[] bytes = builder.sizedByteArray();
      System.out.println("Size of flat buffers "+bytes.length);
      return bytes;
    }

    public byte[] protoObj() {
      DirectoryInfoProtos.DirectoryInfo.Builder builder = DirectoryInfoProtos.DirectoryInfo.newBuilder();
      builder.setCreationTime(System.currentTimeMillis());
      builder.setModificationTime(System.currentTimeMillis());
      builder.setUpdateId(System.currentTimeMillis());
      builder.setObjectId(inodeID);
      builder.setParentId(parentID);
      builder.setName(directoryName);
      builder.setOwner("sodonnell");
      builder.setGroup("hadoop-read-write");
      builder.setPermission(777);

      builder.addAcls(
          DirectoryInfoProtos.Acl.newBuilder()
              .setName("otheruser")
              .setType(DirectoryInfoProtos.AclType.USER)
              .setScope(DirectoryInfoProtos.AclScope.ACCESS)
              .setPermissions(7).build()
      );

      builder.addAcls(
          DirectoryInfoProtos.Acl.newBuilder()
              .setName("otheruser")
              .setType(DirectoryInfoProtos.AclType.USER)
              .setScope(DirectoryInfoProtos.AclScope.DEFAULT)
              .setPermissions(7).build()
      );
      byte[] bytes = builder.build().toByteArray();
      System.out.println("size of proto bytes "+bytes.length);
      return bytes;
    }

    @Setup(Level.Trial)
    public void setUp() {
      flatbytes = flatbufferObj();
      protobytes = protoObj();
    }
  }


  public static void main(String[] args) throws Exception {
    org.openjdk.jmh.Main.main(args);
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleLongProto(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws InvalidProtocolBufferException {
    long val = DirectoryInfoProtos.DirectoryInfo.parseFrom(state.protobytes).getObjectId();
    blackhole.consume(val);
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleLongFlat(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws RocksDBException {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(state.flatbytes);
    DirectoryInfo dir = DirectoryInfo.getRootAsDirectoryInfo(buf);
    blackhole.consume(dir.objectId());
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleStringProto(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws InvalidProtocolBufferException {
    String val = DirectoryInfoProtos.DirectoryInfo.parseFrom(state.protobytes).getName();
    blackhole.consume(val);
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleStringFlat(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws RocksDBException {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(state.flatbytes);
    DirectoryInfo dir = DirectoryInfo.getRootAsDirectoryInfo(buf);
    blackhole.consume(dir.name());
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessAllFieldsProto(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws InvalidProtocolBufferException {
    DirectoryInfoProtos.DirectoryInfo dir = DirectoryInfoProtos.DirectoryInfo.parseFrom(state.protobytes);
    blackhole.consume(dir.getObjectId());
    blackhole.consume(dir.getParentId());
    blackhole.consume(dir.getUpdateId());
    blackhole.consume(dir.getCreationTime());
    blackhole.consume(dir.getModificationTime());
    blackhole.consume(dir.getName());
    blackhole.consume(dir.getOwner());
    blackhole.consume(dir.getGroup());
    blackhole.consume(dir.getPermission());
    for (DirectoryInfoProtos.Acl a : dir.getAclsList()) {
      blackhole.consume(a.getName());
      blackhole.consume(a.getType());
      blackhole.consume(a.getScope());
      blackhole.consume(a.getPermissions());
    }
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessAllFieldsFlat(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws RocksDBException {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(state.flatbytes);
    DirectoryInfo dir = DirectoryInfo.getRootAsDirectoryInfo(buf);
    blackhole.consume(dir.objectId());
    blackhole.consume(dir.parentId());
    blackhole.consume(dir.updateId());
    blackhole.consume(dir.creationTime());
    blackhole.consume(dir.modificationTime());
    blackhole.consume(dir.name());
    blackhole.consume(dir.owner());
    blackhole.consume(dir.group());
    blackhole.consume(dir.permissions());
    for (int i=0; i<dir.aclsLength(); i++) {
      Acl a = dir.acls(i);
      blackhole.consume(a.name());
      blackhole.consume(a.type());
      blackhole.consume(a.scope());
      blackhole.consume(a.permissions());
    }
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleLongManyTimesProto(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws InvalidProtocolBufferException {
    DirectoryInfoProtos.DirectoryInfo dir = DirectoryInfoProtos.DirectoryInfo.parseFrom(state.protobytes);
    for (int i=0; i<20; i++) {
      blackhole.consume(dir.getObjectId());
    }
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleLongManyTimesFlat(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws RocksDBException {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(state.flatbytes);
    DirectoryInfo dir = DirectoryInfo.getRootAsDirectoryInfo(buf);
    for (int i=0; i<20; i++) {
      blackhole.consume(dir.objectId());
    }
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleStringManyTimesProto(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws InvalidProtocolBufferException {
    DirectoryInfoProtos.DirectoryInfo dir = DirectoryInfoProtos.DirectoryInfo.parseFrom(state.protobytes);
    for (int i=0; i<20; i++) {
      blackhole.consume(dir.getName());
    }
  }

  @Benchmark
  @Threads(1)
  @Warmup(iterations = 8)
  @Fork(value = 1, warmups = 0)
  @Measurement(iterations = 8, time = 1000, timeUnit = MILLISECONDS)
  @BenchmarkMode(Mode.Throughput)
  public void accessSingleStringManyTimesFlat(Blackhole blackhole, BenchmarkSerialize.BenchmarkState state) throws RocksDBException {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(state.flatbytes);
    DirectoryInfo dir = DirectoryInfo.getRootAsDirectoryInfo(buf);
    for (int i=0; i<20; i++) {
      blackhole.consume(dir.name());
    }
  }

}

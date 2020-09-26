package com.sodonnell.rocksdb.query;

import com.google.protobuf.InvalidProtocolBufferException;
import com.sodonnell.rocksdb.RocksDBTable;
import com.sodonnell.rocksdb.proto.DirectoryInfoProtos;

public class QueryDataProto extends QueryData {

  public QueryDataProto(RocksDBTable table) {
    super(table);
  }

  @Override
  protected long findNextId(byte[] bytes) {
    try {
      DirectoryInfoProtos.DirectoryInfo dir = DirectoryInfoProtos.DirectoryInfo.parseFrom(bytes);

      /* Uncomment to read all fields
      dir.getParentId();
      dir.getUpdateId();
      dir.getCreationTime();
      dir.getModificationTime();
      dir.getName();
      dir.getOwner();
      dir.getGroup();
      dir.getPermission();
      for (DirectoryInfoProtos.Acl a : dir.getAclsList()) {
        a.getName();
        a.getType();
        a.getScope();
        a.getPermissions();
      }
       */

      return dir.getObjectId();
    } catch (InvalidProtocolBufferException e) {
      System.out.println("Exception parsing proto " + e);
      throw new RuntimeException(e);
    }
  }

}

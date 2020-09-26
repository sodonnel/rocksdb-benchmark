package com.sodonnell.rocksdb.query;

import com.sodonnell.rocksdb.RocksDBTable;
import com.sodonnell.rocksdb.flatbuffer.Acl;
import com.sodonnell.rocksdb.flatbuffer.DirectoryInfo;

public class QueryDataFlatBuffer extends QueryData {

  public QueryDataFlatBuffer(RocksDBTable table) {
    super(table);
  }

  @Override
  protected long findNextId(byte[] bytes) {
    java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
    DirectoryInfo dir = DirectoryInfo.getRootAsDirectoryInfo(buf);

    /* Uncomment to read all fields
    dir.parentId();
    dir.updateId();
    dir.creationTime();
    dir.modificationTime();
    dir.name();
    dir.owner();
    dir.group();
    dir.permissions();
    for (int i=0; i<dir.aclsLength(); i++) {
      Acl a = dir.acls(i);
      a.name();
      a.type();
      a.scope();
      a.permissions();
    }
    */


    return dir.objectId();
  }

}

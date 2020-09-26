package com.sodonnell.rocksdb.generate;

import com.sodonnell.rocksdb.proto.DirectoryInfoProtos;

public class GenerateDataWithProto extends GenerateData {

  public GenerateDataWithProto(String basePath, String tableName, int dirsPerLevel, int levels) {
    super(basePath, tableName, dirsPerLevel, levels);
  }

  @Override
  public byte[] generateValue(long inodeID) {
    DirectoryInfoProtos.DirectoryInfo.Builder builder = DirectoryInfoProtos.DirectoryInfo.newBuilder();
    builder.setCreationTime(System.currentTimeMillis());
    builder.setModificationTime(System.currentTimeMillis());
    builder.setUpdateId(System.currentTimeMillis());
    builder.setObjectId(inodeID);
    builder.setParentId(inodeID);
    builder.setName(dirPrefix);
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
    return builder.build().toByteArray();
  }

}

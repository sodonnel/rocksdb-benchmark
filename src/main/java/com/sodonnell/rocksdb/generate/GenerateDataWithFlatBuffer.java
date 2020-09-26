package com.sodonnell.rocksdb.generate;

import com.google.flatbuffers.FlatBufferBuilder;
import com.sodonnell.rocksdb.flatbuffer.Acl;
import com.sodonnell.rocksdb.flatbuffer.AclScope;
import com.sodonnell.rocksdb.flatbuffer.AclType;
import com.sodonnell.rocksdb.flatbuffer.DirectoryInfo;

public class GenerateDataWithFlatBuffer extends GenerateData {

  public GenerateDataWithFlatBuffer(String basePath, String tableName, int dirsPerLevel, int levels) {
    super(basePath, tableName, dirsPerLevel, levels);
  }

  @Override
  public byte[] generateValue(long inodeID) {
    FlatBufferBuilder builder = new FlatBufferBuilder(250);
    int nameOffset = builder.createString(dirPrefix);
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
    DirectoryInfo.addObjectId(builder, inodeID);
    DirectoryInfo.addParentId(builder, inodeID);
    DirectoryInfo.addUpdateId(builder, System.currentTimeMillis());
    DirectoryInfo.addName(builder, nameOffset);
    DirectoryInfo.addOwner(builder, ownerOffset);
    DirectoryInfo.addGroup(builder, groupOffset);
    DirectoryInfo.addPermissions(builder, (short)755);
    DirectoryInfo.addAcls(builder, aclsOffset);

    int dirInfo = DirectoryInfo.endDirectoryInfo(builder);
    builder.finish(dirInfo);
    return builder.sizedByteArray();
  }

}

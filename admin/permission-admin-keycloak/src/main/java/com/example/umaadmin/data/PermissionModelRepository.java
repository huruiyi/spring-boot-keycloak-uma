package com.example.umaadmin.data;

import com.example.umaadmin.model.PermissionModel;

public interface PermissionModelRepository {

  PermissionModel get();

  void save(PermissionModel model);
}

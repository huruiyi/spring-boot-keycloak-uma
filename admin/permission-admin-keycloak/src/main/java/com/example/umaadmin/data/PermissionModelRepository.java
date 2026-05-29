package com.example.umaadmin.data;

import com.example.umaadmin.model.PermissionModel;
import com.example.umaadmin.model.RealmRoleModel;
import com.example.umaadmin.model.UserModel;

public interface PermissionModelRepository {

  PermissionModel get();

  void save(PermissionModel model);

  void saveRole(RealmRoleModel role);

  void deleteRole(String name);

  void saveUser(UserModel user);

  void deleteUser(String username);
}

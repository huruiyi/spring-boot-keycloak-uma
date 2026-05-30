# 待办事项

## 权限管理后台后续优化

- [ ] 审计日志：记录操作人、时间、来源 IP、修改前后内容，覆盖用户、Realm Role、UMA Resource、Policy、Permission、系统接口权限和模型同步。
- [ ] 操作确认和影响提示：删除 Resource、Policy、Permission 前展示影响范围，例如被哪些 Permission、Endpoint 或策略引用。
- [ ] 后台自身权限控制：为权限管理后台增加只读、用户维护、策略维护、Keycloak 同步等管理权限分级。
- [ ] 测试补强：为 9200 Keycloak repository 增加 MockServer 或集成测试，覆盖 role/user/client policy、Permission 多 Policy、decision strategy 和不同 Keycloak 返回结构。

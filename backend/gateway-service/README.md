# Gateway 微服务拆分储备

该模块保留第十四周已经实现的 Spring Cloud Gateway 路由、请求 ID 和 Redis Token Bucket 代码，方便项目以后从模块化单体拆成多个服务。

## 当前默认架构

- 本地开发：Vue `5173` → Business `8080`。
- Compose 部署：浏览器 → Nginx `80` → Business `8080`。
- Gateway 不在默认请求链中，Compose 默认也不会启动它。
- JWT 最终验签、角色权限和业务精确限流始终由 Business 负责。

## 为什么仍然保留

当前只有一个 Java 业务应用，多加 Gateway 只会增加部署和排错成本。代码保留是为了以后出现独立扩容、独立发布或团队边界时，能够复用已经测试过的请求编号、路由和令牌桶实现。

## 如何验证储备模块

可以独立运行测试：

```powershell
cd backend\gateway-service
mvn test
```

也可以显式启用 Compose profile：

```powershell
docker compose --profile microservices --env-file .\deploy\.env.deploy `
  -f .\deploy\docker-compose.full.yml up -d gateway
```

启动 Gateway 不代表前端已经切换到它。真正拆分前还必须定义服务边界和数据所有权，增加明确路由，并同步修改 Nginx、健康检查、监控与部署配置。完整步骤见 `docs/项目部署教程.md` 的“后期拆分微服务时如何复用 Gateway”。

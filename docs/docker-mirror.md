# Docker Mirror

本地开发机已在 Docker Desktop daemon 配置中加入免费 Docker Hub 镜像源：

```json
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
```

配置文件位置：

```text
C:\Users\Ren\.docker\daemon.json
```

修改后通过以下命令重启并验证：

```powershell
docker desktop restart
docker info --format '{{json .RegistryConfig.Mirrors}}'
```

期望输出包含：

```json
["https://docker.1ms.run/","https://docker.xuanyuan.me/"]
```

## Current Status

轩辕镜像免费版连通性已验证，`docker pull docker.xuanyuan.me/hello-world` 成功。

轩辕免费版拉取项目基础设施镜像时返回 `429 Too Many Requests`，因此本地已将毫秒镜像 `https://docker.1ms.run` 放在第一优先级，轩辕镜像保留为备用：

- `postgres:16-alpine`
- `minio/minio:RELEASE.2025-09-07T16-13-09Z`
- `opensearchproject/opensearch:2.17.1`

这是公共免费节点限流，不是 Compose 配置错误。切换/恢复镜像源后可直接重试：

```powershell
docker compose up -d postgres minio opensearch
```

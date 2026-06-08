# Voyager Docs 阿里云轻量服务器 Docker 部署步骤

本文档适用于：一台全新的 Linux 服务器，只有公网 IP，没有域名。第一版先使用 HTTP 访问：

```text
http://服务器公网IP
```

当前镜像已经发布到 GitHub Container Registry：

```text
ghcr.io/rainlvan/voyager-docs-backend:latest
ghcr.io/rainlvan/voyager-docs-frontend:latest
ghcr.io/rainlvan/voyager-docs-worker:latest
```

## 1. 阿里云控制台放行端口

在阿里云轻量应用服务器控制台里，打开防火墙/安全组，放行：

```text
TCP 80
```

当前没有域名，所以暂时不需要放行 443。

## 2. SSH 登录服务器

在本机终端连接服务器：

```bash
ssh root@你的服务器公网IP
```

如果不是 root 用户，就用你服务器实际的用户名。

## 3. 安装 Docker

在服务器执行：

```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
docker compose version
```

如果最后一行能显示 Docker Compose 版本，就说明安装成功。

## 4. 配置 Docker 镜像加速

你之前提供的毫秒镜像加速地址可以这样配置：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
JSON
sudo systemctl restart docker
```

## 5. 配置 OpenSearch 系统参数

OpenSearch 需要调大 `vm.max_map_count`：

```bash
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-voyager-opensearch.conf
sudo sysctl --system
```

## 6. 下载部署文件

创建部署目录并拉取项目：

```bash
sudo mkdir -p /opt/voyager
sudo chown "$USER:$USER" /opt/voyager
cd /opt/voyager
git clone https://github.com/Rainlvan/voyager-docs.git repo
cd repo
```

如果服务器没有 git，先安装：

```bash
sudo apt-get update
sudo apt-get install -y git
```

## 7. 创建生产环境配置

复制示例配置：

```bash
cp .env.production.example .env
```

编辑 `.env`：

```bash
nano .env
```

至少修改下面这些值：

```env
GHCR_OWNER=rainlvan
IMAGE_TAG=latest

POSTGRES_PASSWORD=换成一个长随机密码
MINIO_ROOT_PASSWORD=换成一个长随机密码
OPENSEARCH_INITIAL_ADMIN_PASSWORD=换成一个强密码
VOYAGER_SECURITY_JWT_SECRET=换成一个长随机字符串
VOYAGER_CRYPTO_SECRET=换成一个长随机字符串
```

可以用下面命令生成随机字符串：

```bash
openssl rand -hex 32
```

初始账号保持：

```env
VOYAGER_BOOTSTRAP_ADMIN_USERNAME=admin
VOYAGER_BOOTSTRAP_ADMIN_PASSWORD=12345678
VOYAGER_BOOTSTRAP_EMPLOYEE_USERNAME=employee
VOYAGER_BOOTSTRAP_EMPLOYEE_PASSWORD=12345678
```

注意：`.env` 里是生产密钥，不能上传到 GitHub，也不要发给别人。

## 8. 启动系统

拉取镜像：

```bash
docker compose --env-file .env -f docker-compose.prod.yml pull
```

启动服务：

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

查看状态：

```bash
docker compose --env-file .env -f docker-compose.prod.yml ps
```

正常情况下会看到这些容器：

```text
voyager-frontend
voyager-backend
voyager-worker
voyager-postgres
voyager-minio
voyager-opensearch
```

## 9. 浏览器访问

打开：

```text
http://你的服务器公网IP
```

初始登录：

```text
管理员：admin / 12345678
普通员工：employee / 12345678
```

首次登录后建议立刻做三件事：

1. 用管理员账号登录。
2. 修改管理员密码。
3. 在“系统设置”里填写阿里云百炼 API Key。

## 10. 基础验证

管理员侧：

1. 打开系统设置。
2. 填写百炼 API Key。
3. 点击测试配置。
4. 查看任务队列，确认 Worker 在线。

普通员工侧：

1. 登录 `employee / 12345678`。
2. 上传 TXT、PDF、图片各一个。
3. 等待文档状态变为“可检索”。
4. 测试 AI 搜索。
5. 测试对话找文档。
6. 测试文档下载。

## 11. 查看日志

查看全部容器日志：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f
```

只看后端：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f backend
```

只看 Worker：

```bash
docker compose --env-file .env -f docker-compose.prod.yml logs -f worker
```

## 12. 更新系统

以后本地修改代码并推送 GitHub 后，GitHub Actions 会重新发布镜像。

服务器更新命令：

```bash
cd /opt/voyager/repo
git pull
docker compose --env-file .env -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

## 13. 重置测试环境

如果只是测试部署，想清空所有数据重新开始，可以执行：

```bash
docker compose --env-file .env -f docker-compose.prod.yml down -v
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

这会删除 PostgreSQL、MinIO、OpenSearch 和备份数据卷。正式使用后不要随便执行。

## 14. 重要安全提醒

- 当前只有公网 IP，所以先使用 HTTP。正式给员工使用时，建议绑定域名并开启 HTTPS。
- `.env` 不要提交到 GitHub。
- 部署后立即修改管理员密码。
- 百炼 API Key 只在管理员系统设置里填写，不要写进代码或文档。
- 服务器只需要开放 80 端口，数据库、MinIO、OpenSearch 不要开放到公网。

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

## 3. 推荐方式：一键部署脚本

服务器执行下面命令：

```bash
curl -fsSL https://raw.githubusercontent.com/Rainlvan/voyager-docs/main/deploy/install.sh -o voyager-install.sh
chmod +x voyager-install.sh
./voyager-install.sh
```

脚本会自动完成：

1. 检查并安装基础工具。
2. 检测 Docker 和 Docker Compose 是否已安装。
3. 询问是否使用现有 Docker，或自动安装 Docker。
4. 询问是否配置 Docker 镜像源 `https://docker.1ms.run`。
5. 配置 OpenSearch 需要的系统参数。
6. 拉取 GitHub 仓库到 `/opt/voyager/repo`。
7. 自动生成 `/opt/voyager/repo/.env`，里面包含随机数据库密码、MinIO 密码、JWT 密钥和加密密钥。
8. 拉取 GHCR 镜像并启动系统。

脚本执行过程中会出现两个选择：

```text
Use the existing Docker installation? [Y/n]
Configure Docker registry mirror (https://docker.1ms.run)? [Y/n]
```

如果服务器已经安装好 Docker，就选择 `Y`。

如果服务器已经有稳定镜像源，第二个可以选择 `n`；如果没有，就选择 `Y`。

脚本执行完成后访问：

```text
http://你的服务器公网IP
```

初始账号：

```text
管理员：admin / 12345678
普通员工：employee / 12345678
```

首次登录后建议立刻做三件事：

1. 用管理员账号登录。
2. 修改管理员密码。
3. 在“系统设置”里填写阿里云百炼 API Key。

下面是手动部署步骤。如果已经使用一键脚本部署成功，可以不用继续执行。

## 4. 手动安装 Docker

在服务器执行：

```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
docker compose version
```

如果最后一行能显示 Docker Compose 版本，就说明安装成功。

## 5. 手动配置 Docker 镜像加速

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

## 6. 手动配置 OpenSearch 系统参数

OpenSearch 需要调大 `vm.max_map_count`：

```bash
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-voyager-opensearch.conf
sudo sysctl --system
```

## 7. 手动下载部署文件

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

## 8. 手动创建生产环境配置

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

## 9. 手动启动系统

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

## 10. 浏览器访问

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

## 11. 基础验证

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

## 12. 查看日志

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

## 13. OpenSearch 启动失败处理

如果启动时看到类似下面的错误：

```text
dependency failed to start: container voyager-opensearch is unhealthy
```

先查看 OpenSearch 日志：

```bash
cd /opt/voyager/repo
docker logs voyager-opensearch --tail=200
```

如果日志里出现：

```text
Password ... failed validation
```

说明旧脚本生成的 OpenSearch 初始密码不符合规则。更新仓库后重新执行一键脚本会自动修复 `.env` 里的这个值：

```bash
cd /opt/voyager/repo
git pull
cd ~
./voyager-install.sh
```

如果日志里出现：

```text
ERROR: setting [plugins.security.disabled] already set
```

说明使用的是包含重复 OpenSearch 安全配置的旧 compose 文件。更新仓库后重新启动即可：

```bash
cd /opt/voyager/repo
git pull
sudo docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build --force-recreate opensearch
sudo docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

轻量服务器上最常见原因是 OpenSearch 内存压力。当前生产配置默认使用较轻的参数：

```env
OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m
OPENSEARCH_MEM_LIMIT=1536m
```

如果你的 `.env` 是旧脚本生成的，可以补上这两行：

```bash
cd /opt/voyager/repo
grep -q '^OPENSEARCH_JAVA_OPTS=' .env || echo 'OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m' >> .env
grep -q '^OPENSEARCH_MEM_LIMIT=' .env || echo 'OPENSEARCH_MEM_LIMIT=1536m' >> .env
```

然后重新启动：

```bash
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build opensearch
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
docker compose --env-file .env -f docker-compose.prod.yml ps
```

如果仍然失败，检查服务器内存：

```bash
free -h
docker logs voyager-opensearch --tail=200
```

如果服务器内存只有 2GB，建议开启系统 swap 或升级到 4GB 以上内存再运行完整 RAG 服务。

## 14. 更新系统

以后本地修改代码并推送 GitHub 后，GitHub Actions 会重新发布镜像。

服务器更新命令：

```bash
cd /opt/voyager/repo
git pull
docker compose --env-file .env -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

## 15. 重置测试环境

如果只是测试部署，想清空所有数据重新开始，可以执行：

```bash
docker compose --env-file .env -f docker-compose.prod.yml down -v
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

这会删除 PostgreSQL、MinIO、OpenSearch 和备份数据卷。正式使用后不要随便执行。

## 16. 重要安全提醒

- 当前只有公网 IP，所以先使用 HTTP。正式给员工使用时，建议绑定域名并开启 HTTPS。
- `.env` 不要提交到 GitHub。
- 部署后立即修改管理员密码。
- 百炼 API Key 只在管理员系统设置里填写，不要写进代码或文档。
- 服务器只需要开放 80 端口，数据库、MinIO、OpenSearch 不要开放到公网。

# Voyager Docs

Voyager Docs 是一个企业文档存储与 AI 检索平台，支持文档上传、解析、OCR、向量索引、AI 搜索、对话找文档、账号管理、审计日志和全量备份恢复。

## Architecture

- `backend`: Spring Boot 3 API 服务。
- `frontend`: Vue 3 + Vite 前端。
- `worker`: Python 3.12 文档解析、OCR、向量化和 OpenSearch 索引 Worker。
- `docker-compose.yml`: 本地开发基础设施。
- `docker-compose.prod.yml`: 生产 Docker 部署。

## Local Development

启动本地基础设施：

```powershell
docker compose up -d postgres minio opensearch opensearch-dashboards
```

启动后端：

```powershell
cd backend
mvn spring-boot:run
```

启动 Worker：

```powershell
cd worker
python -m venv .venv312
.\.venv312\Scripts\Activate.ps1
pip install -r requirements.txt -r requirements-ocr.txt
python -m voyager_worker.main
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

默认地址：

- Frontend: http://localhost:5173
- Backend: http://localhost:18080
- MinIO Console: http://127.0.0.1:9001
- OpenSearch: http://127.0.0.1:19200

## Default Login

空数据库首次启动时会初始化两个账号：

- 管理员：`admin / 12345678`
- 普通员工：`employee / 12345678`

账号可通过环境变量覆盖：

- `VOYAGER_BOOTSTRAP_ADMIN_USERNAME`
- `VOYAGER_BOOTSTRAP_ADMIN_PASSWORD`
- `VOYAGER_BOOTSTRAP_EMPLOYEE_USERNAME`
- `VOYAGER_BOOTSTRAP_EMPLOYEE_PASSWORD`

首次进入系统后，请在管理员页面修改密码并配置百炼 API Key。

## Production Deployment

生产部署使用 Docker Compose 和 GitHub Container Registry 公开镜像。公网只暴露前端 Nginx 的 `80` 端口，数据库、MinIO、OpenSearch、后端和 Worker 都在 Docker 内网。

部署说明见 [docs/deploy-production-ip.md](docs/deploy-production-ip.md)。

## Data And Secrets

不要提交以下内容：

- `.env` 或任何真实环境变量文件。
- 本地 Docker 数据卷。
- 文档、测试夹具、截图、日志、备份包。
- 百炼 API Key、数据库密码、JWT/Crypto 密钥。

`.env.example` 和 `.env.production.example` 只保留占位符。

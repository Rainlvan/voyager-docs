# Voyager Worker

Python Worker 负责从 PostgreSQL 领取 `ingestion_jobs`，下载 MinIO 原始文件，提取文本/OCR/图片页面，调用百炼向量模型，并写入 OpenSearch。

## Setup

建议使用 Python 3.12：

```powershell
cd worker
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m voyager_worker.main
```

如果管理员还没有配置百炼 API Key，Worker 会使用确定性本地伪向量，便于本地开发验证任务链路。

## OCR

本机当前 Python 3.13 可运行 Worker 的基础解析和索引链路。`rapidocr-onnxruntime` 暂不支持 Python 3.13，如需本地 OCR，请使用 Python 3.12 虚拟环境后额外安装：

```powershell
pip install -r requirements-ocr.txt
```

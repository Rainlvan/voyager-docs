# Voyager Docs: Docker deployment on a public IP server

This guide deploys Voyager Docs to a blank Linux server with only a public IP address.
The first production version uses HTTP on port 80. Add HTTPS later when a domain is available.

## 1. Prepare GitHub Container Registry

1. Push this repository to GitHub.
2. Make sure the default branch is `main`.
3. Open GitHub Actions and run or wait for `Publish Docker Images`.
4. Open each package in GitHub Packages and set visibility to `Public`.
5. Note your lowercase GitHub owner name. It will be used as `GHCR_OWNER`.

Images published by the workflow:

```bash
ghcr.io/<github-owner>/voyager-docs-backend:latest
ghcr.io/<github-owner>/voyager-docs-frontend:latest
ghcr.io/<github-owner>/voyager-docs-worker:latest
```

## 2. Prepare the server

Open port `80` in the Alibaba Cloud Lightweight Application Server firewall/security group.

Install Docker:

```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
docker compose version
```

Optional Docker Hub mirror for China mainland network conditions:

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
JSON
sudo systemctl restart docker
```

OpenSearch needs a higher mmap limit:

```bash
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-voyager-opensearch.conf
sudo sysctl --system
```

## 3. Create deployment directory

Clone the repository, or copy at least `docker-compose.prod.yml` into `/opt/voyager`.
Cloning is usually simplest:

```bash
sudo mkdir -p /opt/voyager
sudo chown "$USER:$USER" /opt/voyager
cd /opt/voyager
git clone <your-repo-url> repo
cd repo
```

Create the production environment file:

```bash
cp .env.production.example .env
nano .env
```

Set these values carefully:

```bash
GHCR_OWNER=<your-github-owner-lowercase>
POSTGRES_PASSWORD=<long-random-password>
MINIO_ROOT_PASSWORD=<long-random-password>
OPENSEARCH_INITIAL_ADMIN_PASSWORD=<strong-password>
VOYAGER_SECURITY_JWT_SECRET=<long-random-secret>
VOYAGER_CRYPTO_SECRET=<long-random-secret>
```

You can generate secrets with:

```bash
openssl rand -hex 32
```

The initial accounts for a blank database are:

```text
admin / 12345678
employee / 12345678
```

Change the administrator password immediately after first login.

## 4. Start Voyager Docs

Pull public GHCR images and start without building on the server:

```bash
docker compose --env-file .env -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
docker compose --env-file .env -f docker-compose.prod.yml ps
```

The production server should use `pull` and `--no-build`. If local `docker compose build`
hits Docker Hub mirror rate limits, let GitHub Actions build the images and deploy the
published GHCR images instead.

Visit:

```text
http://<server-public-ip>
```

Only port `80` is exposed publicly. PostgreSQL, MinIO, OpenSearch, Backend, and Worker stay inside the Docker network.

## 5. First production checks

1. Login as `admin / 12345678`.
2. Change the admin password in account management.
3. Open system settings and enter the Bailian API Key.
4. Login as `employee / 12345678`.
5. Upload one TXT/PDF/image document and wait until it becomes searchable.
6. Test AI search, chat citations, and document download.
7. Run one manual backup from the admin system settings page.

## 6. Reset a test deployment

This removes all production data volumes for this compose project.
Use it only before real data exists, or after taking an external backup.

```bash
docker compose --env-file .env -f docker-compose.prod.yml down -v
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

After reset, Flyway creates a fresh schema and the bootstrap users are recreated.
No local development documents, API Keys, chat sessions, audit events, or backups are included in the Docker images.

## 7. Update images later

After pushing new code to `main` and waiting for GitHub Actions:

```bash
cd /opt/voyager/repo
git pull
docker compose --env-file .env -f docker-compose.prod.yml pull
docker compose --env-file .env -f docker-compose.prod.yml up -d --no-build
```

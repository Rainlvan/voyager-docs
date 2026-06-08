package com.voyager.docs.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "voyager")
public class AppProperties {
    private final Security security = new Security();
    private final Crypto crypto = new Crypto();
    private final Minio minio = new Minio();
    private final OpenSearch opensearch = new OpenSearch();
    private final Backup backup = new Backup();

    public Security getSecurity() {
        return security;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    public Minio getMinio() {
        return minio;
    }

    public OpenSearch getOpensearch() {
        return opensearch;
    }

    public Backup getBackup() {
        return backup;
    }

    public static class Security {
        @NotBlank
        private String jwtSecret;

        @Positive
        private long tokenTtlMinutes = 720;

        @Positive
        private int loginMaxFailures = 5;

        @Positive
        private long loginWindowMinutes = 10;

        @Positive
        private long loginLockMinutes = 15;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(long tokenTtlMinutes) {
            this.tokenTtlMinutes = tokenTtlMinutes;
        }

        public int getLoginMaxFailures() {
            return loginMaxFailures;
        }

        public void setLoginMaxFailures(int loginMaxFailures) {
            this.loginMaxFailures = loginMaxFailures;
        }

        public long getLoginWindowMinutes() {
            return loginWindowMinutes;
        }

        public void setLoginWindowMinutes(long loginWindowMinutes) {
            this.loginWindowMinutes = loginWindowMinutes;
        }

        public long getLoginLockMinutes() {
            return loginLockMinutes;
        }

        public void setLoginLockMinutes(long loginLockMinutes) {
            this.loginLockMinutes = loginLockMinutes;
        }
    }

    public static class Crypto {
        @NotBlank
        private String secret;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Minio {
        @NotBlank
        private String endpoint;
        @NotBlank
        private String accessKey;
        @NotBlank
        private String secretKey;
        @NotBlank
        private String bucket;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class OpenSearch {
        @NotBlank
        private String endpoint;
        @NotBlank
        private String documentIndex;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getDocumentIndex() {
            return documentIndex;
        }

        public void setDocumentIndex(String documentIndex) {
            this.documentIndex = documentIndex;
        }
    }

    public static class Backup {
        @NotBlank
        private String directory = "./data/backups";

        @NotBlank
        private String pgDumpCommand = "docker exec voyager-postgres pg_dump -U voyager -d voyager -Fc";

        @NotBlank
        private String pgRestoreCommand = "docker exec -i voyager-postgres pg_restore -U voyager -d voyager --clean --if-exists --no-owner";

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public String getPgDumpCommand() {
            return pgDumpCommand;
        }

        public void setPgDumpCommand(String pgDumpCommand) {
            this.pgDumpCommand = pgDumpCommand;
        }

        public String getPgRestoreCommand() {
            return pgRestoreCommand;
        }

        public void setPgRestoreCommand(String pgRestoreCommand) {
            this.pgRestoreCommand = pgRestoreCommand;
        }
    }
}

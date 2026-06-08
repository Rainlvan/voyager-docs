package com.voyager.docs.service;

import com.voyager.docs.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.DownloadObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    private final MinioClient minioClient;
    private final AppProperties properties;

    public StorageService(MinioClient minioClient, AppProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @PostConstruct
    void ensureBucket() throws Exception {
        String bucket = bucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public void put(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to save object to storage", exception);
        }
    }

    public InputStream get(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read object from storage", exception);
        }
    }

    public List<StoredObject> listObjects() {
        try {
            List<StoredObject> objects = new ArrayList<>();
            Iterable<Result<Item>> results = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket())
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bucket())
                        .object(item.objectName())
                        .build());
                objects.add(new StoredObject(item.objectName(), stat.contentType(), item.size()));
            }
            return objects;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to list objects from storage", exception);
        }
    }

    public void downloadToFile(String objectKey, Path target) {
        try {
            minioClient.downloadObject(DownloadObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .filename(target.toString())
                    .overwrite(true)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to download object from storage", exception);
        }
    }

    public void putFile(String objectKey, Path source, String contentType) {
        try {
            minioClient.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .filename(source.toString())
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to restore object to storage", exception);
        }
    }

    public void clearBucket() {
        for (StoredObject object : listObjects()) {
            remove(object.objectKey());
        }
    }

    public void remove(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to remove object from storage", exception);
        }
    }

    public String bucket() {
        return properties.getMinio().getBucket();
    }

    public record StoredObject(String objectKey, String contentType, long size) {
    }
}

package com.voyager.docs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "ai_settings")
public class AiSetting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String provider = "dashscope";

    @Column(nullable = false, length = 80)
    private String region = "cn-beijing";

    @Column(name = "api_key_ciphertext")
    private String apiKeyCiphertext;

    @Column(name = "chat_model", nullable = false, length = 120)
    private String chatModel;

    @Column(name = "text_embedding_model", nullable = false, length = 120)
    private String textEmbeddingModel;

    @Column(name = "text_embedding_dimension", nullable = false)
    private int textEmbeddingDimension;

    @Column(name = "multimodal_embedding_model", nullable = false, length = 120)
    private String multimodalEmbeddingModel;

    @Column(name = "multimodal_embedding_dimension", nullable = false)
    private int multimodalEmbeddingDimension;

    @Column(name = "rerank_model", nullable = false, length = 120)
    private String rerankModel;

    @Column(name = "multimodal_rerank_model", nullable = false, length = 120)
    private String multimodalRerankModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_invocation_mode", nullable = false, length = 30)
    private EmbeddingInvocationMode embeddingInvocationMode = EmbeddingInvocationMode.REALTIME;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private AppUser updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    public void setApiKeyCiphertext(String apiKeyCiphertext) {
        this.apiKeyCiphertext = apiKeyCiphertext;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getTextEmbeddingModel() {
        return textEmbeddingModel;
    }

    public void setTextEmbeddingModel(String textEmbeddingModel) {
        this.textEmbeddingModel = textEmbeddingModel;
    }

    public int getTextEmbeddingDimension() {
        return textEmbeddingDimension;
    }

    public void setTextEmbeddingDimension(int textEmbeddingDimension) {
        this.textEmbeddingDimension = textEmbeddingDimension;
    }

    public String getMultimodalEmbeddingModel() {
        return multimodalEmbeddingModel;
    }

    public void setMultimodalEmbeddingModel(String multimodalEmbeddingModel) {
        this.multimodalEmbeddingModel = multimodalEmbeddingModel;
    }

    public int getMultimodalEmbeddingDimension() {
        return multimodalEmbeddingDimension;
    }

    public void setMultimodalEmbeddingDimension(int multimodalEmbeddingDimension) {
        this.multimodalEmbeddingDimension = multimodalEmbeddingDimension;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public void setRerankModel(String rerankModel) {
        this.rerankModel = rerankModel;
    }

    public String getMultimodalRerankModel() {
        return multimodalRerankModel;
    }

    public void setMultimodalRerankModel(String multimodalRerankModel) {
        this.multimodalRerankModel = multimodalRerankModel;
    }

    public EmbeddingInvocationMode getEmbeddingInvocationMode() {
        return embeddingInvocationMode;
    }

    public void setEmbeddingInvocationMode(EmbeddingInvocationMode embeddingInvocationMode) {
        this.embeddingInvocationMode = embeddingInvocationMode;
    }

    public AppUser getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(AppUser updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

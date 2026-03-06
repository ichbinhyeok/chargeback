package com.example.demo.dispute.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seo_events")
public class SeoEventEntity {

    @Id
    private UUID id;

    @Column(name = "event_name", nullable = false, length = 64)
    private String eventName;

    @Column(name = "platform_slug", length = 32)
    private String platformSlug;

    @Column(name = "guide_slug", length = 128)
    private String guideSlug;

    @Column(name = "page_path", nullable = false, length = 255)
    private String pagePath;

    @Column(name = "source_channel", length = 32)
    private String sourceChannel;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "referrer", length = 512)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public String getPlatformSlug() {
        return platformSlug;
    }

    public void setPlatformSlug(String platformSlug) {
        this.platformSlug = platformSlug;
    }

    public String getGuideSlug() {
        return guideSlug;
    }

    public void setGuideSlug(String guideSlug) {
        this.guideSlug = guideSlug;
    }

    public String getPagePath() {
        return pagePath;
    }

    public void setPagePath(String pagePath) {
        this.pagePath = pagePath;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReferrer() {
        return referrer;
    }

    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

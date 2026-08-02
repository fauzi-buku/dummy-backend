package com.bukuwarung.dummybackend.domain.entities;

import com.bukuwarung.dummybackend.domain.entities.enums.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.UUID;

@Table(
    name = "idempotency_keys",
    uniqueConstraints = @UniqueConstraint(columnNames = {"idempotency_key", "scope"}))
@Entity
public class IdempotencyKey extends BaseEntity {

  @Column(name = "idempotency_key", nullable = false)
  @NotBlank
  private String idempotencyKey;

  @Column(name = "scope", nullable = false)
  @NotBlank
  private String scope;

  @Column(name = "request_fingerprint", nullable = false)
  @NotBlank
  private String requestFingerprint;

  @Column(name = "status", nullable = false)
  @Enumerated(EnumType.STRING)
  @NotNull
  private IdempotencyStatus status;

  @Column(name = "lease_expires_at", nullable = false)
  @NotNull
  private ZonedDateTime leaseExpiresAt;

  @Column(name = "resource_id")
  private UUID resourceId;

  @Column(name = "response_status")
  private Integer responseStatus;

  @Column(name = "response_body", columnDefinition = "TEXT")
  private String responseBody;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getRequestFingerprint() {
    return requestFingerprint;
  }

  public void setRequestFingerprint(String requestFingerprint) {
    this.requestFingerprint = requestFingerprint;
  }

  public IdempotencyStatus getStatus() {
    return status;
  }

  public void setStatus(IdempotencyStatus status) {
    this.status = status;
  }

  public ZonedDateTime getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public void setLeaseExpiresAt(ZonedDateTime leaseExpiresAt) {
    this.leaseExpiresAt = leaseExpiresAt;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public void setResourceId(UUID resourceId) {
    this.resourceId = resourceId;
  }

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public void setResponseStatus(Integer responseStatus) {
    this.responseStatus = responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public Long getVersion() {
    return version;
  }
}

package es.convalidafp.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Cuenta de administrador. Son las únicas cuentas de la aplicación: simular no requiere cuenta. La
 * contraseña se guarda solo como hash.
 */
@Entity
@Table(name = "admin_user")
public class AdminUser {

  /** Único rol que existe. */
  public static final String ROLE_ADMIN = "ADMIN";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "username", nullable = false, updatable = false, length = 100)
  private String username;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(name = "role", nullable = false, length = 16)
  private String role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected AdminUser() {}

  public AdminUser(String username, String passwordHash, Instant createdAt) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.role = ROLE_ADMIN;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getRole() {
    return role;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

package by.bsuir.warehouse.common.model;

import java.time.LocalDateTime;

public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String username;
    private String passwordHash;   // SHA-256, 64 hex-символа
    private Role role;
    private String fullName;
    private String phone;
    private String email;
    private LocalDateTime createdAt;
    private boolean active;
    private RegistrationStatus registrationStatus;

    public User() {}

    public User(int id, String username, Role role, String fullName, boolean active) {
        super(id);
        this.username = username;
        this.role     = role;
        this.fullName = fullName;
        this.active   = active;
    }

    // ---- getters / setters ----

    public String getUsername()              { return username; }
    public void setUsername(String v)        { this.username = v; }

    public String getPasswordHash()          { return passwordHash; }
    public void setPasswordHash(String v)    { this.passwordHash = v; }

    public Role getRole()                    { return role; }
    public void setRole(Role role)           { this.role = role; }

    public String getFullName()              { return fullName; }
    public void setFullName(String v)        { this.fullName = v; }

    public String getPhone()                 { return phone; }
    public void setPhone(String v)           { this.phone = v; }

    public String getEmail()                 { return email; }
    public void setEmail(String v)           { this.email = v; }

    public LocalDateTime getCreatedAt()      { return createdAt; }
    public void setCreatedAt(LocalDateTime v){ this.createdAt = v; }

    public boolean isActive()               { return active; }
    public void setActive(boolean v)        { this.active = v; }

    public RegistrationStatus getRegistrationStatus() { return registrationStatus; }
    public void setRegistrationStatus(RegistrationStatus v) { this.registrationStatus = v; }

    /** Удобный метод для проверки роли */
    public boolean hasRole(String roleName) {
        return role != null && roleName.equals(role.getRoleName());
    }

    @Override
    public String toString() {
        return fullName != null ? fullName : username;
    }
}
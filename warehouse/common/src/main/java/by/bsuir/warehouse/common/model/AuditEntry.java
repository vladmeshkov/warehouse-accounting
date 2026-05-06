package by.bsuir.warehouse.common.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class AuditEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int userId;
    private String userFullName;
    private LocalDateTime timestamp;
    private String action;
    private String details;

    public AuditEntry() {}

    public AuditEntry(int id, int userId, LocalDateTime timestamp, String userFullName, String action, String details) {
        this.id = id;
        this.userId = userId;
        this.timestamp = timestamp;
        this.userFullName = userFullName;
        this.action = action;
        this.details = details;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUserFullName() { return userFullName; }
    public void setUserFullName(String userFullName) { this.userFullName = userFullName; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
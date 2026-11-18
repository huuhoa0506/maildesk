package nhh.reminder;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "mail_send_jobs", indexes = {
        @Index(name = "idx_mail_send_jobs_reminding_status", columnList = "reminding_id,status")
})
public class MailSendJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reminding_id", nullable = false)
    private long remindingId;

    @Column(nullable = false)
    private String status = "queued";

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected MailSendJobEntity() {
    }

    public MailSendJobEntity(long remindingId, int totalCount) {
        this.remindingId = remindingId;
        this.totalCount = totalCount;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public long getRemindingId() { return remindingId; }
    public String getStatus() { return status; }
    public int getTotalCount() { return totalCount; }

    public void start() {
        if ("queued".equals(status)) {
            status = "running";
            startedAt = LocalDateTime.now();
        }
    }

    public void complete() {
        if (!"completed".equals(status)) {
            status = "completed";
            completedAt = LocalDateTime.now();
        }
    }
}

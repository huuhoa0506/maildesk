package nhh.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "mail_send_job_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mail_job_student", columnNames = { "job_id", "student_id" })
}, indexes = {
        @Index(name = "idx_mail_job_items_status_id", columnList = "status,id"),
        @Index(name = "idx_mail_job_items_job_status", columnList = "job_id,status")
})
public class MailSendJobItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private long jobId;

    @Column(name = "student_id", nullable = false)
    private long agentId;

    @Column(nullable = false)
    private String status = "queued";

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected MailSendJobItemEntity() {
    }

    public MailSendJobItemEntity(long jobId, long agentId) {
        this.jobId = jobId;
        this.agentId = agentId;
    }

    public Long getId() { return id; }
    public long getJobId() { return jobId; }
    public long getAgentId() { return agentId; }
    public String getStatus() { return status; }

    public void complete(String status, String errorMessage) {
        this.status = status;
        this.errorMessage = errorMessage;
    }
}

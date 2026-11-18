package nhh.reminder;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "students")
public class AgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminding_id", nullable = false)
    private RemindingEntity reminding;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "row_data", nullable = false, columnDefinition = "TEXT")
    private String rowData;

    @Column(name = "records_data", columnDefinition = "TEXT")
    private String recordsData;

    @Column(name = "record_count", nullable = false, columnDefinition = "integer default 1")
    private int recordCount = 1;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected AgentEntity() {
    }

    public AgentEntity(RemindingEntity reminding, String name, String email, String rowData,
            String recordsData, int recordCount) {
        this.reminding = reminding;
        this.name = name;
        this.email = email;
        this.rowData = rowData;
        this.recordsData = recordsData;
        this.recordCount = recordCount;
    }

    @PrePersist
    void setImportedAt() {
        if (importedAt == null) {
            importedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public RemindingEntity getReminding() { return reminding; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRowData() { return rowData; }
    public String getRecordsData() { return recordsData; }
    public int getRecordCount() { return recordCount; }
    public String getStatus() { return status; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getErrorMessage() { return errorMessage; }

    public void markSending() {
        status = "sending";
        errorMessage = null;
    }

    public void markSent() {
        status = "sent";
        sentAt = LocalDateTime.now();
        errorMessage = null;
    }

    public void markPending() {
        status = "pending";
        errorMessage = null;
    }

    public void markFailed(String message) {
        status = "failed";
        errorMessage = message;
    }
}

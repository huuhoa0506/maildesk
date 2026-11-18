package nhh.reminder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "remindings")
public class RemindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private SchoolEntity school;

    @Column(nullable = false)
    private String name;

    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(name = "sender_name_snapshot", nullable = false)
    private String senderNameSnapshot;

    @Column(name = "sender_email_snapshot", nullable = false)
    private String senderEmailSnapshot;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_path")
    private String attachmentPath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "reminding", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AgentEntity> agents = new ArrayList<>();

    protected RemindingEntity() {
    }

    public RemindingEntity(SchoolEntity school, String name, String subjectTemplate, String bodyTemplate) {
        this.school = school;
        this.name = name;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
        this.senderNameSnapshot = school.getSenderName();
        this.senderEmailSnapshot = school.getSenderEmail();
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public SchoolEntity getSchool() { return school; }
    public String getName() { return name; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public String getBodyTemplate() { return bodyTemplate; }
    public String getSenderNameSnapshot() { return senderNameSnapshot; }
    public String getSenderEmailSnapshot() { return senderEmailSnapshot; }
    public String getAttachmentName() { return attachmentName; }
    public String getAttachmentPath() { return attachmentPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void updateTemplates(String subjectTemplate, String bodyTemplate) {
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
    }

    public void setAttachment(String attachmentName, String attachmentPath) {
        this.attachmentName = attachmentName;
        this.attachmentPath = attachmentPath;
    }

    public void addAgent(AgentEntity agent) {
        agents.add(agent);
    }
}

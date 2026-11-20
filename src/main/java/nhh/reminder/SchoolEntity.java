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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "schools")
public class SchoolEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "sender_name", nullable = false)
    private String senderName;

    @Column(name = "sender_email", nullable = false)
    private String senderEmail;

    @Column(name = "cc_email")
    private String ccEmail;

    @Column(name = "smtp_host", nullable = false)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "smtp_username", nullable = false)
    private String smtpUsername;

    @Column(name = "encrypted_password")
    private String encryptedPassword;

    @Column(name = "smtp_starttls", nullable = false)
    private boolean smtpStarttls = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<RemindingEntity> remindings = new ArrayList<>();

    protected SchoolEntity() {
    }

    public SchoolEntity(String name, String senderName, String senderEmail, String ccEmail, String smtpHost,
            int smtpPort, String smtpUsername, String encryptedPassword, boolean smtpStarttls) {
        this.name = name;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.ccEmail = ccEmail;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        this.encryptedPassword = encryptedPassword;
        this.smtpStarttls = smtpStarttls;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSenderName() { return senderName; }
    public String getSenderEmail() { return senderEmail; }
    public String getCcEmail() { return ccEmail; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public boolean isSmtpStarttls() { return smtpStarttls; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void update(String name, String senderName, String senderEmail, String ccEmail, String smtpHost,
            int smtpPort, String smtpUsername, String encryptedPassword, boolean smtpStarttls) {
        this.name = name;
        this.senderName = senderName;
        this.senderEmail = senderEmail;
        this.ccEmail = ccEmail;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUsername = smtpUsername;
        if (encryptedPassword != null && !encryptedPassword.isBlank()) {
            this.encryptedPassword = encryptedPassword;
        }
        this.smtpStarttls = smtpStarttls;
    }
}

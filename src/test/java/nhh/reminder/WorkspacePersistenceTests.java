package nhh.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.mail.delivery-mode=log")
@Transactional
class WorkspacePersistenceTests {

    @Autowired
    private WorkspaceService workspace;

    @Autowired
    private MailDeliveryService mailDeliveryService;

    @Autowired
    private MailSendQueueService mailSendQueueService;

    @Autowired
    private AgentRepository agentRepository;

    @Test
    void logDeliveryModeRendersEmailWithoutAnSmtpSender() {
        WorkspaceService.Reminding reminding = new WorkspaceService.Reminding(
                1L, 1L, "Test", "Hello {{Name}}", "Course {{CourseName}}", "School", "school@example.test",
                null, null, "2026-11-18T00:00:00", 1, 0, 1, 0);
        WorkspaceService.Agent agent = new WorkspaceService.Agent(
                1L, 1L, "Agent", "agent@example.test",
                List.of(new WorkspaceService.AgentRecord(java.util.Map.of("Name", "Student", "CourseName", "Course"))),
                1, false, "pending", "2026-11-18T00:00:00", null, null);

        assertTrue(mailDeliveryService.isLogMode());
        assertDoesNotThrow(() -> mailDeliveryService.send(
                null, testSchool(null), reminding, agent, java.util.Map.of("Name", "Student", "CourseName", "Course")));

        WorkspaceService.Agent agentWithRecords = new WorkspaceService.Agent(
                2L, 1L, "Agent", "agent@example.test",
                List.of(new WorkspaceService.AgentRecord(java.util.Map.ofEntries(
                        java.util.Map.entry("StudentId", "123"),
                        java.util.Map.entry("Name", "Student"),
                        java.util.Map.entry("CourseName", "Course"),
                        java.util.Map.entry("Intake Date", "01/01/2026"),
                        java.util.Map.entry("Status", "Current"),
                        java.util.Map.entry("AgentName", "Agent"),
                        java.util.Map.entry("Std_Mobile", "0400000000"),
                        java.util.Map.entry("Duedate", "01/02/2026"),
                        java.util.Map.entry("TuitionFee Amount", "100"),
                        java.util.Map.entry("MiscFee Amount", "0"),
                        java.util.Map.entry("Total Paid Amt", "0"),
                        java.util.Map.entry("Total Due Amt", "100")))),
                1, true, "pending", "2026-11-18T00:00:00", null, null);
        assertDoesNotThrow(() -> mailDeliveryService.send(
                null, testSchool(null), reminding, agentWithRecords, agentWithRecords.records().get(0).values()));
    }

    @Test
    void smtpDeliveryTargetsTheSelectedAgentsEmailAddress() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        MailDeliveryService smtpDelivery = new MailDeliveryService(
                new EncryptionService(), new TemplateVariables(), new AgentWorkbookService(), "smtp");
        WorkspaceService.Reminding reminding = new WorkspaceService.Reminding(
                1L, 1L, "Test", "Test subject", "Test body", "School", "school@example.test",
                null, null, "2026-11-18T00:00:00", 1, 0, 1, 0);
        WorkspaceService.Agent agent = new WorkspaceService.Agent(
                1L, 1L, "Agent", "recipient-two@example.test",
                List.of(new WorkspaceService.AgentRecord(Map.of())), 1, false,
                "pending", "2026-11-18T00:00:00", null, null);

        smtpDelivery.send(sender, testSchool("copy@example.test"), reminding, agent, Map.of());

        assertEquals("recipient-two@example.test",
                ((jakarta.mail.internet.InternetAddress) message.getRecipients(Message.RecipientType.TO)[0])
                        .getAddress());
        assertEquals("copy@example.test",
                ((jakarta.mail.internet.InternetAddress) message.getRecipients(Message.RecipientType.CC)[0])
                        .getAddress());
        verify(sender).send(message);
    }

    @Test
    void persistsSchoolRemindingAndAgentsThroughJpaRepositories() throws Exception {
        WorkspaceService.School school = workspace.saveSchool(
                new WorkspaceService.SchoolInput(null, "JPA Test School", "Test School",
                        "jpa-test@example.edu", "copy@example.edu", "smtp.example.edu", 587,
                        "jpa-test@example.edu", true),
                null);
        assertEquals("copy@example.edu", workspace.school(school.id()).orElseThrow().ccEmail());

        WorkspaceService.Reminding reminding = workspace.createReminding(school.id(), "Monthly Notice",
                "Hello {{Họ tên}}", "Class {{Lớp}}", List.of(
                        new ExcelImportService.ImportedAgent("Agent thử nghiệm", "agent@example.edu",
                                "{\"Họ tên\":\"Học sinh thử nghiệm\",\"Lớp\":\"10A1\"}",
                                "[{\"Họ tên\":\"Học sinh thử nghiệm\",\"Lớp\":\"10A1\"}]", 1,
                                List.of(java.util.Map.of("Họ tên", "Học sinh thử nghiệm", "Lớp", "10A1")))));

        assertEquals(1, workspace.remindings(school.id()).size());
        assertEquals(1, reminding.totalCount());
        assertEquals("10A1", workspace.rowValues(workspace.agents(reminding.id()).get(0)).get("Lớp"));
        assertEquals("pending", agentRepository.findDeliveryStatusesByRemindingId(reminding.id())
                .get(0).getStatus());

        WorkspaceService.Reminding updated = workspace.updateTemplates(reminding.id(),
                "Thông báo {{Họ t&ecirc;n}}", "Chào {{HỌ TÊN}}, lớp {{lop}}");

        assertEquals("Thông báo {{Họ tên}}", updated.subjectTemplate());
        assertEquals("Chào {{Họ tên}}, lớp {{Lớp}}", updated.bodyTemplate());
    }

    @Test
    void queuesSelectedAgentsAndPreventsOverlappingJobsForOneReminding() throws Exception {
        WorkspaceService.School school = workspace.saveSchool(
                new WorkspaceService.SchoolInput(null, "Queue Test School", "Queue Test",
                        "queue-test@example.edu", null, "smtp.example.edu", 587,
                        "queue-test@example.edu", true),
                null);
        WorkspaceService.Reminding reminding = workspace.createReminding(school.id(), "Queued Notice",
                "Hello {{Họ tên}}", "Class {{Lớp}}", List.of(
                        new ExcelImportService.ImportedAgent("Agent One", "one@example.edu",
                                "{\"Họ tên\":\"Student One\",\"Lớp\":\"10A1\"}",
                                "[{\"Họ tên\":\"Student One\",\"Lớp\":\"10A1\"}]", 1,
                                List.of(java.util.Map.of("Họ tên", "Student One", "Lớp", "10A1"))),
                        new ExcelImportService.ImportedAgent("Agent Two", "two@example.edu",
                                "{\"Họ tên\":\"Student Two\",\"Lớp\":\"10A2\"}",
                                "[{\"Họ tên\":\"Student Two\",\"Lớp\":\"10A2\"}]", 1,
                                List.of(java.util.Map.of("Họ tên", "Student Two", "Lớp", "10A2")))));
        List<Long> studentIds = workspace.agents(reminding.id()).stream()
                .map(WorkspaceService.Agent::id)
                .toList();

        MailSendQueueService.JobProgress queued = mailSendQueueService.enqueue(reminding.id(), studentIds);

        assertEquals(2, queued.total());
        assertEquals("queued", queued.status());
        assertEquals(0, queued.processed());
        assertThrows(IllegalStateException.class,
                () -> mailSendQueueService.enqueue(reminding.id(), studentIds));

        MailSendQueueService.ClaimedItem first = mailSendQueueService.claimNext().orElseThrow();
        mailSendQueueService.finish(first.itemId(), true, null);
        MailSendQueueService.ClaimedItem second = mailSendQueueService.claimNext().orElseThrow();
        mailSendQueueService.finish(second.itemId(), false, "SMTP test error");

        MailSendQueueService.JobProgress complete = mailSendQueueService.progress(queued.id()).orElseThrow();
        assertEquals("completed", complete.status());
        assertEquals(2, complete.processed());
        assertEquals(1, complete.succeeded());
        assertEquals(1, complete.failed());
    }

    private WorkspaceService.School testSchool(String ccEmail) {
        return new WorkspaceService.School(1L, "Test School", "School", "school@example.test", ccEmail,
                "smtp.example.test", 587, "smtp-user", null, true);
    }
}

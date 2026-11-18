package nhh.reminder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;

@Controller
public class MailDashboardController {

    private static final String DRAFT_SESSION_KEY = "remindingDraft";

    private final WorkspaceService workspace;
    private final ExcelImportService excelImportService;
    private final EncryptionService encryptionService;
    private final MailDeliveryService mailDeliveryService;
    private final MailSendQueueService mailSendQueueService;
    private final TemplateVariables templateVariables;
    private final AgentRepository agentRepository;

    public MailDashboardController(WorkspaceService workspace, ExcelImportService excelImportService,
            EncryptionService encryptionService, MailDeliveryService mailDeliveryService,
            MailSendQueueService mailSendQueueService,
            TemplateVariables templateVariables, AgentRepository agentRepository) {
        this.workspace = workspace;
        this.excelImportService = excelImportService;
        this.encryptionService = encryptionService;
        this.mailDeliveryService = mailDeliveryService;
        this.mailSendQueueService = mailSendQueueService;
        this.templateVariables = templateVariables;
        this.agentRepository = agentRepository;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) Long schoolId, Model model) {
        List<WorkspaceService.School> schools = workspace.schools();
        WorkspaceService.School selectedSchool = schools.stream()
                .filter(school -> school.id() == (schoolId == null ? -1 : schoolId))
                .findFirst()
                .orElse(schools.isEmpty() ? null : schools.get(0));
        model.addAttribute("schools", schools);
        model.addAttribute("selectedSchool", selectedSchool);
        List<WorkspaceService.Reminding> remindings = selectedSchool == null ? List.of() : workspace.remindings(selectedSchool.id());
        model.addAttribute("remindings", remindings);
        model.addAttribute("pendingTotal", remindings.stream().mapToInt(WorkspaceService.Reminding::pendingCount).sum());
        model.addAttribute("sentTotal", remindings.stream().mapToInt(WorkspaceService.Reminding::sentCount).sum());
        model.addAttribute("failedTotal", remindings.stream().mapToInt(WorkspaceService.Reminding::failedCount).sum());
        return "dashboard";
    }

    @GetMapping("/schools")
    public String schools(Model model) {
        model.addAttribute("schools", workspace.schools());
        model.addAttribute("mailLogMode", mailDeliveryService.isLogMode());
        return "schools";
    }

    @PostMapping("/schools")
    public String saveSchool(
            @RequestParam(required = false) Long id,
            @RequestParam String name,
            @RequestParam String senderName,
            @RequestParam String senderEmail,
            @RequestParam String smtpHost,
            @RequestParam int smtpPort,
            @RequestParam String smtpUsername,
            @RequestParam(required = false) String appPassword,
            @RequestParam(defaultValue = "false") boolean smtpStarttls,
            RedirectAttributes redirect) {
        try {
            if (name.isBlank() || senderName.isBlank() || !senderEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new IllegalArgumentException("Vui lòng nhập tên trường, tên người gửi và email hợp lệ.");
            }
            if (smtpHost.isBlank() || smtpPort < 1 || smtpPort > 65535 || smtpUsername.isBlank()) {
                throw new IllegalArgumentException("Vui lòng nhập SMTP host, port (1–65535) và username hợp lệ.");
            }
            String encryptedPassword = appPassword == null || appPassword.isBlank()
                    ? null
                    : encryptionService.encrypt(appPassword);
            WorkspaceService.School school = workspace.saveSchool(new WorkspaceService.SchoolInput(
                    id, name, senderName, senderEmail, smtpHost, smtpPort, smtpUsername, smtpStarttls), encryptedPassword);
            redirect.addFlashAttribute("success", "Đã lưu cấu hình " + school.name() + ".");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/schools";
    }

    @PostMapping("/schools/{id}/test")
    public String testSmtp(@PathVariable long id, RedirectAttributes redirect) {
        WorkspaceService.School school = workspace.school(id).orElse(null);
        if (school == null) {
            redirect.addFlashAttribute("error", "Không tìm thấy trường.");
        } else if (mailDeliveryService.isLogMode()) {
            redirect.addFlashAttribute("success", "Đang bật chế độ log nên không thực hiện kết nối SMTP.");
        } else {
            try {
                mailDeliveryService.testConnection(school);
                redirect.addFlashAttribute("success", "Kết nối SMTP của " + school.name() + " thành công.");
            } catch (Exception exception) {
                redirect.addFlashAttribute("error", "Không kết nối được SMTP: " + safeMessage(exception));
            }
        }
        return "redirect:/schools";
    }

    @GetMapping("/remindings/new")
    public String newReminding(@RequestParam long schoolId, Model model) {
        WorkspaceService.School school = workspace.school(schoolId).orElse(null);
        if (school == null) {
            return "redirect:/schools";
        }
        model.addAttribute("schools", workspace.schools());
        model.addAttribute("school", school);
        return "reminding-upload";
    }

    @PostMapping("/remindings/preview")
    public String previewReminding(
            @RequestParam long schoolId,
            @RequestParam String name,
            @RequestParam("roster") MultipartFile roster,
            HttpSession session,
            Model model,
            RedirectAttributes redirect) {
        try {
            WorkspaceService.School school = workspace.school(schoolId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trường."));
            if (name.isBlank()) {
                throw new IllegalArgumentException("Vui lòng nhập tên Reminding.");
            }
            ExcelImportService.ImportResult imported = excelImportService.read(roster);
            RemindingDraft draft = new RemindingDraft(
                    school.id(), name.trim(), imported.agents(), imported.headers(), imported.warnings());
            session.setAttribute(DRAFT_SESSION_KEY, draft);
            return showComposer(draft, model);
        } catch (IllegalArgumentException | IOException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/remindings/new?schoolId=" + schoolId;
        }
    }

    @GetMapping("/remindings/compose")
    public String resumeCompose(HttpSession session, Model model, RedirectAttributes redirect) {
        Object value = session.getAttribute(DRAFT_SESSION_KEY);
        if (!(value instanceof RemindingDraft draft)) {
            redirect.addFlashAttribute("error", "Phiên nhập Excel đã hết hạn. Vui lòng nhập lại file.");
            return "redirect:/";
        }
        return showComposer(draft, model);
    }

    private String showComposer(RemindingDraft draft, Model model) {
        WorkspaceService.School school = workspace.school(draft.schoolId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trường."));
        model.addAttribute("schools", workspace.schools());
        model.addAttribute("school", school);
        model.addAttribute("draft", draft);
        model.addAttribute("sampleValues", draft.agents().get(0).records().get(0));
        model.addAttribute("sampleAgent", draft.agents().get(0));
        model.addAttribute("recordCount", draft.agents().stream()
                .mapToInt(ExcelImportService.ImportedAgent::recordCount).sum());
        return "reminding-compose";
    }

    @PostMapping("/remindings")
    public String createReminding(
            @RequestParam String subject,
            @RequestParam String body,
            HttpSession session,
            RedirectAttributes redirect) {
        Object value = session.getAttribute(DRAFT_SESSION_KEY);
        if (!(value instanceof RemindingDraft draft)) {
            redirect.addFlashAttribute("error", "Phiên nhập Excel đã hết hạn. Vui lòng nhập lại file.");
            return "redirect:/";
        }
        try {
            if (subject.isBlank() || body.isBlank()) {
                throw new IllegalArgumentException("Tiêu đề và nội dung email không được để trống.");
            }
            String canonicalSubject = canonicalizeTemplate(subject, draft);
            String canonicalBody = canonicalizeTemplate(body, draft);
            WorkspaceService.Reminding reminding = workspace.createReminding(
                    draft.schoolId(), draft.name(), canonicalSubject, canonicalBody, draft.agents());
            session.removeAttribute(DRAFT_SESSION_KEY);
            redirect.addFlashAttribute("success", "Đã tạo Reminding với " + draft.agents().size()
                    + " Agent và " + draft.agents().stream().mapToInt(ExcelImportService.ImportedAgent::recordCount).sum()
                    + " AgentRecord.");
            if (!draft.warnings().isEmpty()) {
                redirect.addFlashAttribute("importWarnings", draft.warnings());
            }
            return "redirect:/remindings/" + reminding.id();
        } catch (IllegalArgumentException | IOException exception) {
            redirect.addFlashAttribute("subject", subject);
            redirect.addFlashAttribute("body", body);
            redirect.addFlashAttribute("error", exception.getMessage());
            return "redirect:/remindings/compose";
        }
    }

    @GetMapping("/remindings/{id}")
    public String remindingDetail(@PathVariable long id, Model model, RedirectAttributes redirect) {
        Optional<WorkspaceService.Reminding> result = workspace.reminding(id);
        if (result.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy Reminding.");
            return "redirect:/";
        }
        WorkspaceService.Reminding reminding = result.get();
        WorkspaceService.School school = workspace.school(reminding.schoolId()).orElseThrow();
        List<WorkspaceService.Agent> agents = workspace.agents(id);
        List<AgentView> agentViews = agents.stream()
                .map(AgentView::new)
                .toList();
        model.addAttribute("schools", workspace.schools());
        model.addAttribute("selectedSchool", school);
        model.addAttribute("school", school);
        model.addAttribute("reminding", reminding);
        model.addAttribute("agentViews", agentViews);
        model.addAttribute("remindings", workspace.remindings(school.id()));
        model.addAttribute("mailLogMode", mailDeliveryService.isLogMode());
        model.addAttribute("mailJob", mailSendQueueService.latestProgress(id).orElse(null));
        return "reminding-detail";
    }

    @GetMapping("/remindings/{id}/send-progress")
    public ResponseEntity<SendProgressResponse> sendProgress(@PathVariable long id) {
        if (workspace.reminding(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return mailSendQueueService.latestProgress(id)
                .map(progress -> {
                    List<AgentDeliveryStatus> agents = progress.active()
                            ? List.of()
                            : agentRepository.findDeliveryStatusesByRemindingId(id).stream()
                                    .map(agent -> new AgentDeliveryStatus(
                                            agent.getId(), agent.getStatus(), agent.getErrorMessage()))
                                    .toList();
                    return ResponseEntity.ok(new SendProgressResponse(
                            progress.id(), progress.remindingId(), progress.status(), progress.total(),
                            progress.processed(), progress.succeeded(), progress.failed(), agents));
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/remindings/{id}/edit")
    public String editRemindingTemplate(@PathVariable long id, Model model, RedirectAttributes redirect) {
        Optional<WorkspaceService.Reminding> result = workspace.reminding(id);
        if (result.isEmpty()) {
            redirect.addFlashAttribute("error", "Không tìm thấy Reminding.");
            return "redirect:/";
        }
        WorkspaceService.Reminding reminding = result.get();
        List<WorkspaceService.Agent> agents = workspace.agents(id);
        if (agents.isEmpty()) {
            redirect.addFlashAttribute("error", "Reminding chưa có Agent để xem trước.");
            return "redirect:/remindings/" + id;
        }
        WorkspaceService.School school = workspace.school(reminding.schoolId()).orElseThrow();
        List<String> columns = new ArrayList<>(workspace.rowValues(agents.get(0)).keySet());
        model.addAttribute("schools", workspace.schools());
        model.addAttribute("school", school);
        model.addAttribute("reminding", reminding);
        model.addAttribute("columns", columns);
        model.addAttribute("sampleValues", workspace.rowValues(agents.get(0)));
        model.addAttribute("sampleAgent", agents.get(0));
        return "reminding-edit";
    }

    @PostMapping("/remindings/{id}/template")
    public String updateRemindingTemplate(
            @PathVariable long id,
            @RequestParam String subject,
            @RequestParam String body,
            RedirectAttributes redirect) {
        try {
            if (subject.isBlank() || body.isBlank()) {
                throw new IllegalArgumentException("Tiêu đề và nội dung email không được để trống.");
            }
            workspace.updateTemplates(id, subject, body);
            redirect.addFlashAttribute("success", "Đã cập nhật nội dung Reminding. Thay đổi áp dụng cho email chưa gửi và gửi lại.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            redirect.addFlashAttribute("subject", subject);
            redirect.addFlashAttribute("body", body);
            return "redirect:/remindings/" + id + "/edit";
        }
        return "redirect:/remindings/" + id;
    }

    @PostMapping("/remindings/{id}/send")
    public String sendEmails(
            @PathVariable long id,
            @RequestParam(name = "agentIds", required = false) List<Long> agentIds,
            RedirectAttributes redirect) {
        WorkspaceService.Reminding reminding = workspace.reminding(id).orElse(null);
        if (reminding == null) {
            redirect.addFlashAttribute("error", "Không tìm thấy Reminding.");
            return "redirect:/";
        }
        if (agentIds == null || agentIds.isEmpty()) {
            redirect.addFlashAttribute("error", "Hãy chọn ít nhất một Agent để gửi.");
            return "redirect:/remindings/" + id;
        }
        try {
            MailSendQueueService.JobProgress job = mailSendQueueService.enqueue(id, agentIds);
            redirect.addFlashAttribute("success", "Đã đưa " + job.total()
                    + " email vào hàng đợi. Tác vụ tiếp tục chạy nền; tiến độ sẽ tự cập nhật.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/remindings/" + id;
    }

    @GetMapping({"/templates/agents.xlsx", "/templates/students.xlsx"})
    public ResponseEntity<byte[]> sampleWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Students");
            Row header = sheet.createRow(0);
            List<String> headers = List.of("StudentId", "Name", "CourseName", "Intake Date", "Status",
                    "AgentName", "Std_Mobile", "Duedate", "TuitionFee Amount", "MiscFee Amount",
                    "Total Paid Amt", "Total Due Amt", "Agent Email");
            for (int i = 0; i < headers.size(); i++) {
                header.createCell(i).setCellValue(headers.get(i));
                sheet.setColumnWidth(i, 24 * 256);
            }
            addSampleRow(sheet, 1, "2012", "Chanaporn KAWINPHAISON", "Diploma of Marketing and Communication",
                    "04/01/2027", "Enrolled", "APIE 2025 Pty Ltd", "0433512302", "21/12/2026",
                    "1500", "50", "0", "1550", "agent-one@example.test");
            addSampleRow(sheet, 2, "1635", "Yupha NGAMLERT", "Advanced Diploma of Leadership and Management",
                    "05/10/2026", "Current Student", "At Sydney Education And Consulting", "0415592465",
                    "21/12/2026", "1250", "50", "0", "1300", "agent-one@example.test");
            addSampleRow(sheet, 3, "1654", "Jarawee JAINGAM", "Advanced Diploma of Leadership and Management",
                    "07/04/2025", "Current Student", "At Sydney Education And Consulting", "0452496165",
                    "21/12/2026", "1250", "50", "0", "1300", "agent-two@example.test");
            workbook.write(output);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("agent-records-sample.xlsx").build().toString())
                    .body(output.toByteArray());
        }
    }

    private void addSampleRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private String canonicalizeTemplate(String template, RemindingDraft draft) {
        String canonicalTemplate = templateVariables.canonicalize(template, draft.headers());
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}").matcher(template);
        while (matcher.find()) {
            String column = templateVariables.findColumn(
                    templateVariables.cleanName(matcher.group(1)), draft.headers()).orElseThrow();
            long emptyValues = draft.agents().stream()
                    .filter(agent -> agent.records().get(0).getOrDefault(column, "").isBlank())
                    .count();
            if (emptyValues > 0) {
                throw new IllegalArgumentException("Cột \"" + column + "\" đang trống ở " + emptyValues
                        + " Agent. Hãy bổ sung dữ liệu hoặc bỏ biến này khỏi template trước khi tạo Reminding.");
            }
        }
        return canonicalTemplate;
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "Lỗi không xác định.";
        }
        return message.length() > 250 ? message.substring(0, 250) : message;
    }

    public record RemindingDraft(long schoolId, String name, List<ExcelImportService.ImportedAgent> agents,
            List<String> headers, List<String> warnings) {
    }

    public record AgentView(WorkspaceService.Agent agent) {
        public String initials() {
            String[] words = agent.name().trim().split("\\s+");
            if (words.length == 1) {
                return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
            }
            return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase();
        }
    }

    public record SendProgressResponse(
            long id, long remindingId, String status, int total, int processed, int succeeded, int failed,
            List<AgentDeliveryStatus> agents) {
    }

    public record AgentDeliveryStatus(long id, String status, String errorMessage) {
    }
}

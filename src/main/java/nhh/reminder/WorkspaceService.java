package nhh.reminder;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class WorkspaceService {

    private final SchoolRepository schoolRepository;
    private final RemindingRepository remindingRepository;
    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    private final TemplateVariables templateVariables;

    public WorkspaceService(
            SchoolRepository schoolRepository,
            RemindingRepository remindingRepository,
            AgentRepository agentRepository,
            ObjectMapper objectMapper,
            TemplateVariables templateVariables) {
        this.schoolRepository = schoolRepository;
        this.remindingRepository = remindingRepository;
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.templateVariables = templateVariables;
    }

    @Transactional(readOnly = true)
    public List<School> schools() {
        return schoolRepository.findAll(Sort.by(Sort.Direction.ASC, "name")).stream()
                .map(this::toSchool)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<School> school(long id) {
        return schoolRepository.findById(id).map(this::toSchool);
    }

    @Transactional
    public School saveSchool(SchoolInput input, String encryptedPassword) {
        SchoolEntity entity;
        if (input.id() == null) {
            entity = new SchoolEntity(input.name().trim(), input.senderName().trim(), input.senderEmail().trim(),
                    input.smtpHost().trim(), input.smtpPort(), input.smtpUsername().trim(), encryptedPassword,
                    input.smtpStarttls());
        } else {
            entity = schoolRepository.findById(input.id())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trường cần cập nhật."));
            entity.update(input.name().trim(), input.senderName().trim(), input.senderEmail().trim(),
                    input.smtpHost().trim(), input.smtpPort(), input.smtpUsername().trim(), encryptedPassword,
                    input.smtpStarttls());
        }
        return toSchool(schoolRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<Reminding> remindings(long schoolId) {
        return remindingRepository.findAllBySchool_IdOrderByCreatedAtDesc(schoolId).stream()
                .map(this::toReminding)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Reminding> reminding(long id) {
        return remindingRepository.findById(id).map(this::toReminding);
    }

    @Transactional(readOnly = true)
    public Optional<Reminding> remindingForSchool(long id, long schoolId) {
        return remindingRepository.findByIdAndSchool_Id(id, schoolId).map(this::toReminding);
    }

    @Transactional
    public Reminding updateTemplates(long remindingId, String subject, String body) {
        RemindingEntity reminding = remindingRepository.findById(remindingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Reminding cần cập nhật."));
        List<AgentEntity> agents = agentRepository.findAllByReminding_IdOrderById(remindingId);
        if (agents.isEmpty()) {
            throw new IllegalArgumentException("Reminding chưa có Agent để xác thực biến template.");
        }
        Map<String, String> firstRow = readRowData(agents.get(0).getRowData());
        String canonicalSubject = templateVariables.canonicalize(subject, firstRow.keySet());
        String canonicalBody = templateVariables.canonicalize(body, firstRow.keySet());
        validateTemplate(canonicalSubject, firstRow.keySet(), agents);
        validateTemplate(canonicalBody, firstRow.keySet(), agents);
        reminding.updateTemplates(canonicalSubject.trim(), canonicalBody);
        return toReminding(remindingRepository.save(reminding));
    }

    private void validateTemplate(String template, java.util.Set<String> columns, List<AgentEntity> agents) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}").matcher(template);
        while (matcher.find()) {
            String variable = templateVariables.cleanName(matcher.group(1));
            String column = templateVariables.findColumn(variable, columns)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy cột Excel tương ứng với biến {{" + variable + "}}."));
            long emptyValues = agents.stream()
                    .filter(agent -> readRowData(agent.getRowData()).getOrDefault(column, "").isBlank())
                    .count();
            if (emptyValues > 0) {
                throw new IllegalArgumentException("Cột \"" + column + "\" đang trống ở " + emptyValues
                        + " Agent. Hãy bổ sung dữ liệu hoặc bỏ biến này khỏi template.");
            }
        }
    }

    @Transactional
    public Reminding createReminding(
            long schoolId, String name, String subject, String body, List<ExcelImportService.ImportedAgent> agents)
            throws IOException {
        SchoolEntity school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy trường."));
        RemindingEntity reminding = remindingRepository.saveAndFlush(
                new RemindingEntity(school, name.trim(), subject.trim(), body));

        List<AgentEntity> agentEntities = agents.stream()
                .map(agent -> new AgentEntity(reminding, agent.name(), agent.email(), agent.rowData(),
                        agent.recordsData(), agent.recordCount()))
                .toList();
        agentRepository.saveAll(agentEntities);
        return toReminding(remindingRepository.save(reminding));
    }

    @Transactional(readOnly = true)
    public List<Agent> agents(long remindingId) {
        return agentRepository.findAllByReminding_IdOrderById(remindingId).stream()
                .map(this::toAgent)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Agent> agent(long agentId, long remindingId) {
        return agentRepository.findByIdAndReminding_Id(agentId, remindingId).map(this::toAgent);
    }

    public Map<String, String> rowValues(Agent agent) {
        return agent.records().isEmpty() ? Map.of() : agent.records().get(0).values();
    }

    private Map<String, String> readRowData(String rowData) {
        try {
            return objectMapper.readValue(rowData, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Dữ liệu Excel của Agent không đọc được.", exception);
        }
    }

    @Transactional
    public boolean markSending(long agentId) {
        return agentRepository.markSending(agentId) == 1;
    }

    @Transactional
    public void markSent(long agentId) {
        AgentEntity agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Agent cần cập nhật trạng thái."));
        agent.markSent();
    }

    @Transactional
    public void markFailed(long agentId, String error) {
        AgentEntity agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Agent cần cập nhật trạng thái."));
        String safeError = error == null ? "Lỗi gửi email không xác định."
                : error.substring(0, Math.min(error.length(), 500));
        agent.markFailed(safeError);
    }

    private School toSchool(SchoolEntity entity) {
        return new School(entity.getId(), entity.getName(), entity.getSenderName(), entity.getSenderEmail(),
                entity.getSmtpHost(), entity.getSmtpPort(), entity.getSmtpUsername(), entity.getEncryptedPassword(),
                entity.isSmtpStarttls());
    }

    private Reminding toReminding(RemindingEntity entity) {
        long id = entity.getId();
        return new Reminding(id, entity.getSchool().getId(), entity.getName(), entity.getSubjectTemplate(),
                entity.getBodyTemplate(), entity.getSenderNameSnapshot(), entity.getSenderEmailSnapshot(),
                entity.getAttachmentName(), entity.getAttachmentPath(), entity.getCreatedAt().toString(),
                Math.toIntExact(agentRepository.countByReminding_Id(id)),
                Math.toIntExact(agentRepository.countByReminding_IdAndStatus(id, "sent")),
                Math.toIntExact(agentRepository.countByReminding_IdAndStatusIn(id, List.of("pending", "sending"))),
                Math.toIntExact(agentRepository.countByReminding_IdAndStatus(id, "failed")));
    }

    private Agent toAgent(AgentEntity entity) {
        List<AgentRecord> records = readRecords(entity.getRecordsData(), entity.getRowData());
        int recordCount = entity.getRecordCount() <= 0 ? records.size() : entity.getRecordCount();
        return new Agent(entity.getId(), entity.getReminding().getId(), entity.getName(), entity.getEmail(),
                records, recordCount, entity.getRecordsData() != null && !entity.getRecordsData().isBlank(),
                entity.getStatus(),
                entity.getImportedAt() == null ? null : entity.getImportedAt().toString(),
                entity.getSentAt() == null ? null : entity.getSentAt().toString(), entity.getErrorMessage());
    }

    private List<AgentRecord> readRecords(String recordsData, String legacyRowData) {
        if (recordsData == null || recordsData.isBlank()) {
            return List.of(new AgentRecord(readRowData(legacyRowData)));
        }
        try {
            List<Map<String, String>> values = objectMapper.readValue(
                    recordsData, new TypeReference<List<Map<String, String>>>() {});
            return values.stream().map(AgentRecord::new).toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Danh sách AgentRecord không đọc được.", exception);
        }
    }

    public record School(
            long id, String name, String senderName, String senderEmail, String smtpHost, int smtpPort,
            String smtpUsername, String encryptedPassword, boolean smtpStarttls) {
        public String initials() {
            String[] words = name.trim().split("\\s+");
            if (words.length == 1) {
                return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
            }
            return (words[0].substring(0, 1) + words[words.length - 1].substring(0, 1)).toUpperCase();
        }

        public boolean hasPassword() {
            return encryptedPassword != null && !encryptedPassword.isBlank();
        }
    }

    public record SchoolInput(Long id, String name, String senderName, String senderEmail, String smtpHost,
            int smtpPort, String smtpUsername, boolean smtpStarttls) {
    }

    public record Reminding(
            long id, long schoolId, String name, String subjectTemplate, String bodyTemplate,
            String senderName, String senderEmail, String attachmentName, String attachmentPath, String createdAt,
            int totalCount, int sentCount, int pendingCount, int failedCount) {
    }

    public record Agent(long id, long remindingId, String name, String email, List<AgentRecord> records,
            int recordCount, boolean generatedWorkbook, String status, String importedAt, String sentAt,
            String errorMessage) {
    }

    public record AgentRecord(Map<String, String> values) {
        public AgentRecord {
            values = Map.copyOf(values);
        }
    }
}

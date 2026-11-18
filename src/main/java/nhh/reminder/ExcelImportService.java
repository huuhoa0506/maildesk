package nhh.reminder;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ExcelImportService {

    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    private final ObjectMapper objectMapper;
    private final AgentWorkbookService agentWorkbookService;

    public ExcelImportService(ObjectMapper objectMapper, AgentWorkbookService agentWorkbookService) {
        this.objectMapper = objectMapper;
        this.agentWorkbookService = agentWorkbookService;
    }

    public ImportResult read(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn file Excel danh sách Agent.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("MVP hiện hỗ trợ file Excel .xlsx.");
        }

        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("File Excel không có sheet dữ liệu.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("Sheet đầu tiên không có dữ liệu.");
            }

            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("vi-VN"));
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("Không tìm thấy dòng tiêu đề cột trong Excel.");
            }
            List<String> headers = readHeaders(headerRow, formatter);
            java.util.Set<String> normalizedHeaders = new java.util.HashSet<>();
            for (String header : headers) {
                if (header.isBlank()) {
                    continue;
                }
                if (!normalizedHeaders.add(normalize(header))) {
                    throw new IllegalArgumentException("Excel có tên cột trùng nhau: " + header);
                }
            }
            int emailColumn = findColumn(headers, List.of("agent email", "agentemail"));
            if (emailColumn < 0) {
                throw new IllegalArgumentException("File Excel cần có cột Agent Email để nhóm các bản ghi theo người nhận.");
            }
            List<String> missingColumns = agentWorkbookService.findMissingInputColumns(headers);
            if (!missingColumns.isEmpty()) {
                throw new IllegalArgumentException("File Excel thiếu các cột cần có trong mẫu đầu ra: "
                        + String.join(", ", missingColumns));
            }
            Optional<Integer> agentNameColumn = Optional.ofNullable(findColumn(headers, List.of("agent name", "agentname")))
                    .filter(index -> index >= 0);
            Map<String, MutableAgent> agentsByEmail = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();

            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlank(row, headers.size(), formatter)) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    if (!headers.get(column).isBlank()) {
                        values.put(headers.get(column), formatter.formatCellValue(row.getCell(column)).trim());
                    }
                }
                String email = values.getOrDefault(headers.get(emailColumn), "").trim();
                if (email.isBlank()) {
                    warnings.add("Dòng " + (rowIndex + 1) + ": thiếu Agent Email.");
                    continue;
                }
                if (!email.matches(EMAIL_PATTERN)) {
                    warnings.add("Dòng " + (rowIndex + 1)
                            + ": Agent Email không hợp lệ; email cần có dạng name@example.com.");
                    continue;
                }
                String name = agentNameColumn.map(index -> values.getOrDefault(headers.get(index), "").trim())
                        .filter(value -> !value.isBlank())
                        .orElse(email);
                values.remove(headers.get(emailColumn));
                MutableAgent agent = agentsByEmail.computeIfAbsent(email.toLowerCase(Locale.ROOT),
                        ignored -> new MutableAgent(name, email));
                agent.records.add(Map.copyOf(values));
            }

            if (agentsByEmail.isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy Agent hợp lệ có email. " + String.join(" ", warnings));
            }
            List<String> templateHeaders = headers.stream()
                    .filter(header -> !normalize(header).equals("agent email") && !normalize(header).equals("agentemail"))
                    .toList();
            List<ImportedAgent> agents = agentsByEmail.values().stream()
                    .map(agent -> new ImportedAgent(agent.name, agent.email, serialize(agent.records.get(0)),
                            serialize(agent.records), agent.records.size(), List.copyOf(agent.records)))
                    .toList();
            return new ImportResult(templateHeaders, agents, List.copyOf(warnings));
        }
    }

    private List<String> readHeaders(Row row, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        for (int index = 0; index < row.getLastCellNum(); index++) {
            headers.add(formatter.formatCellValue(row.getCell(index)).trim());
        }
        return headers;
    }

    private int findColumn(List<String> headers, List<String> acceptedNames) {
        for (int index = 0; index < headers.size(); index++) {
            if (acceptedNames.stream().map(this::normalize).anyMatch(normalize(headers.get(index))::equals)) {
                return index;
            }
        }
        return -1;
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        return normalized;
    }

    private boolean isBlank(Row row, int columns, DataFormatter formatter) {
        for (int column = 0; column < columns; column++) {
            if (!formatter.formatCellValue(row.getCell(column)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String serialize(Object values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Không thể lưu dữ liệu Agent từ Excel.", exception);
        }
    }

    private static final class MutableAgent {
        private final String name;
        private final String email;
        private final List<Map<String, String>> records = new ArrayList<>();

        private MutableAgent(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }

    public record ImportedAgent(
            String name, String email, String rowData, String recordsData, int recordCount,
            List<Map<String, String>> records) {
    }

    public record ImportResult(List<String> headers, List<ImportedAgent> agents, List<String> warnings) {
    }
}

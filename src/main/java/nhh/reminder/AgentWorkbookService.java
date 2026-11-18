package nhh.reminder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkbookService {

    public static final List<String> OUTPUT_COLUMNS = List.of(
            "studentid", "name", "coursename", "intake_date", "status", "agentname", "std_mobile",
            "duedate", "tuitionfee_amount", "miscfee_amount", "total_paid_amt", "total_due_amt");

    public byte[] create(String agentEmail, List<WorkspaceService.AgentRecord> records) throws IOException {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("Agent không có AgentRecord để tạo file Excel.");
        }
        try (InputStream input = getClass().getResourceAsStream("/agent-output-template.xlsx");
                XSSFWorkbook workbook = input == null ? null : new XSSFWorkbook(input);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (workbook == null || workbook.getNumberOfSheets() == 0) {
                throw new IllegalStateException("Không tìm thấy mẫu Excel đầu ra của Agent.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            workbook.setSheetName(0, safeSheetName(agentEmail));
            Row templateRow = sheet.getRow(1);
            if (templateRow == null) {
                throw new IllegalStateException("Mẫu Excel đầu ra thiếu dòng định dạng dữ liệu.");
            }
            for (int index = 0; index < records.size(); index++) {
                Map<String, String> source = records.get(index).values();
                Map<String, String> normalized = normalizeValues(source);
                Row row = index == 0 ? templateRow : sheet.createRow(index + 1);
                for (int column = 0; column < OUTPUT_COLUMNS.size(); column++) {
                    Cell templateCell = templateRow.getCell(column);
                    Cell cell = row.getCell(column);
                    if (cell == null) {
                        cell = row.createCell(column);
                    }
                    if (index > 0 && templateCell != null) {
                        cell.setCellStyle(templateCell.getCellStyle());
                    }
                    cell.setCellValue(normalized.getOrDefault(normalize(OUTPUT_COLUMNS.get(column)), ""));
                }
            }
            if (records.size() == 1) {
                for (int index = 2; index <= sheet.getLastRowNum(); index++) {
                    Row extraRow = sheet.getRow(index);
                    if (extraRow != null) {
                        sheet.removeRow(extraRow);
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    public List<String> findMissingInputColumns(List<String> inputHeaders) {
        Map<String, String> normalized = new LinkedHashMap<>();
        inputHeaders.forEach(header -> normalized.put(normalize(header), header));
        return OUTPUT_COLUMNS.stream()
                .filter(column -> !normalized.containsKey(normalize(column)))
                .toList();
    }

    private Map<String, String> normalizeValues(Map<String, String> source) {
        Map<String, String> values = new LinkedHashMap<>();
        source.forEach((key, value) -> values.put(normalize(key), value));
        return values;
    }

    private String safeSheetName(String email) {
        String sheetName = email.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }
}

package nhh.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

class ExcelImportServiceTests {

    @Test
    void bundledAgentInputSampleMatchesTheFixedImportSchema() throws Exception {
        byte[] workbook = java.nio.file.Files.readAllBytes(
                java.nio.file.Path.of("samples", "agent-records-sample.xlsx"));
        MockMultipartFile upload = new MockMultipartFile(
                "roster", "agent-records-sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);

        ExcelImportService.ImportResult result =
                new ExcelImportService(new ObjectMapper(), new AgentWorkbookService()).read(upload);

        assertEquals(2, result.agents().size());
        assertEquals(2, result.agents().get(0).recordCount());
        assertEquals(1, result.agents().get(1).recordCount());
    }

    @Test
    void reportsMissingAndMalformedAgentEmailsSeparately() throws Exception {
        byte[] source = sourceWorkbook();
        byte[] modified;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheetAt(0).getRow(1).getCell(12).setCellValue("lananhpham.kt@gmailcom");
            workbook.getSheetAt(0).getRow(2).getCell(12).setCellValue("");
            workbook.write(output);
            modified = output.toByteArray();
        }
        MockMultipartFile upload = new MockMultipartFile(
                "roster", "agents.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", modified);

        ExcelImportService.ImportResult result =
                new ExcelImportService(new ObjectMapper(), new AgentWorkbookService()).read(upload);

        assertEquals(2, result.warnings().size());
        assertEquals("Dòng 2: Agent Email không hợp lệ; email cần có dạng name@example.com.",
                result.warnings().get(0));
        assertEquals("Dòng 3: thiếu Agent Email.", result.warnings().get(1));
    }

    @Test
    void groupsRowsByAgentEmailAndGeneratesOutputWorkbookFromAgentRecords() throws Exception {
        byte[] sourceWorkbook = sourceWorkbook();
        MockMultipartFile upload = new MockMultipartFile(
                "roster", "agents.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sourceWorkbook);

        ExcelImportService.ImportResult result =
                new ExcelImportService(new ObjectMapper(), new AgentWorkbookService()).read(upload);

        assertEquals(2, result.agents().size());
        assertEquals(0, result.warnings().size());
        assertEquals("agent-one@example.test", result.agents().get(0).email());
        assertEquals(2, result.agents().get(0).recordCount());
        assertEquals(1, result.agents().get(1).recordCount());
        assertEquals("1250", result.agents().get(0).records().get(0).get("TuitionFee Amount"));
        assertEquals("50", result.agents().get(0).records().get(0).get("MiscFee Amount"));
        assertEquals("0", result.agents().get(0).records().get(0).get("Total Paid Amt"));
        assertEquals("1300", result.agents().get(0).records().get(0).get("Total Due Amt"));
        assertFalse(result.agents().get(0).records().get(0).containsKey("Agent Email"));

        byte[] output = new AgentWorkbookService().create(result.agents().get(0).email(),
                result.agents().get(0).records().stream()
                .map(WorkspaceService.AgentRecord::new)
                .toList());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(output))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("agent-one@example.test", sheet.getSheetName());
            assertEquals(AgentWorkbookService.OUTPUT_COLUMNS.size(), sheet.getRow(0).getLastCellNum());
            assertEquals("studentid", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(2, sheet.getLastRowNum());
            assertEquals("2012", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(0)));
            assertEquals("1635", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(2).getCell(0)));
            assertEquals("2026-01-01", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(3)));
            assertEquals("0400000001", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(6)));
            assertEquals("1250", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(8)));
            assertEquals("50", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(9)));
            assertEquals("0", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(10)));
            assertEquals("1300", new DataFormatter(Locale.ROOT).formatCellValue(sheet.getRow(1).getCell(11)));
        }
    }

    private byte[] sourceWorkbook() throws Exception {
        List<String> headers = List.of(
                "StudentId", "Name", "CourseName", "Intake Date", "Status", "AgentName", "Std_Mobile",
                "Duedate", "TuitionFee Amount", "MiscFee Amount", "Total Paid Amt", "Total Due Amt", "Agent Email");
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("ReportData");
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                header.createCell(index).setCellValue(headers.get(index));
            }
            addRow(sheet, 1, "2012", "Student One", "Course A", "2026-01-01", "Current", "Agent One",
                    "0400000001", "2026-12-01", "1250", "50", "0", "1300", "agent-one@example.test");
            addRow(sheet, 2, "1635", "Student Two", "Course B", "2026-02-01", "Current", "Agent One",
                    "0400000002", "2026-12-01", "1250", "50", "0", "1300", "AGENT-ONE@example.test");
            addRow(sheet, 3, "1654", "Student Three", "Course C", "2026-03-01", "Current", "Agent Two",
                    "0400000003", "2026-12-01", "1250", "50", "0", "1300", "agent-two@example.test");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void addRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int index = 0; index < values.length; index++) {
            row.createCell(index).setCellValue(values[index]);
        }
    }
}

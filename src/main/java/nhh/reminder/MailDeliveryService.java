package nhh.reminder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.InternetAddress;

@Service
public class MailDeliveryService {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final Logger logger = LoggerFactory.getLogger(MailDeliveryService.class);
    private final EncryptionService encryptionService;
    private final TemplateVariables templateVariables;
    private final AgentWorkbookService agentWorkbookService;
    private final boolean logMode;

    public MailDeliveryService(
            EncryptionService encryptionService,
            TemplateVariables templateVariables,
            AgentWorkbookService agentWorkbookService,
            @Value("${app.mail.delivery-mode:log}") String deliveryMode) {
        this.encryptionService = encryptionService;
        this.templateVariables = templateVariables;
        this.agentWorkbookService = agentWorkbookService;
        if (!"log".equalsIgnoreCase(deliveryMode) && !"smtp".equalsIgnoreCase(deliveryMode)) {
            throw new IllegalArgumentException("app.mail.delivery-mode phải là 'log' hoặc 'smtp'.");
        }
        this.logMode = "log".equalsIgnoreCase(deliveryMode);
    }

    public boolean isLogMode() {
        return logMode;
    }

    public JavaMailSender prepare(WorkspaceService.School school) {
        if (school.smtpHost().isBlank() || school.smtpUsername().isBlank()) {
            throw new IllegalStateException("Trường chưa có cấu hình SMTP đầy đủ.");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(school.smtpHost());
        sender.setPort(school.smtpPort());
        sender.setUsername(school.smtpUsername());
        sender.setPassword(encryptionService.decrypt(school.encryptedPassword()));
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.getJavaMailProperties().put("mail.transport.protocol", "smtp");
        sender.getJavaMailProperties().put("mail.smtp.auth", "true");
        sender.getJavaMailProperties().put("mail.smtp.starttls.enable", Boolean.toString(school.smtpStarttls()));
        sender.getJavaMailProperties().put("mail.smtp.starttls.required", Boolean.toString(school.smtpStarttls()));
        sender.getJavaMailProperties().put("mail.smtp.ssl.enable",
                Boolean.toString(!school.smtpStarttls() && school.smtpPort() == 465));
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "10000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "15000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    public void testConnection(WorkspaceService.School school) throws Exception {
        if (logMode) {
            throw new IllegalStateException("Đang bật chế độ log nên không thực hiện kết nối SMTP.");
        }
        ((JavaMailSenderImpl) prepare(school)).testConnection();
    }

    public void send(
            JavaMailSender sender,
            WorkspaceService.Reminding reminding,
            WorkspaceService.Agent agent,
            Map<String, String> rowValues) throws Exception {
        String subject = renderText(reminding.subjectTemplate(), rowValues);
        String body = renderHtml(reminding.bodyTemplate(), rowValues);
        boolean generatedWorkbook = agent.generatedWorkbook();
        byte[] generatedAttachment = generatedWorkbook
                ? agentWorkbookService.create(agent.email(), agent.records())
                : null;
        String generatedFilename = (safeAttachmentBaseName(agent.email()) + ".xlsx");
        if (logMode) {
            String attachment = generatedWorkbook
                    ? generatedFilename + " (" + agent.recordCount() + " records, " + generatedAttachment.length + " bytes)"
                    : StringUtils.hasText(reminding.attachmentName()) ? reminding.attachmentName() : "(không có)";
            logger.info("MAIL LOG | from={} | to={} | subject={} | attachment={} | body={}",
                    logValue(reminding.senderEmail()), logValue(agent.email()), logValue(subject),
                    logValue(attachment), logValue(body));
            return;
        }

        var message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(new InternetAddress(reminding.senderEmail(), reminding.senderName(), StandardCharsets.UTF_8.name()));
        helper.setTo(agent.email());
        helper.setSubject(subject);
        helper.setText(body, true);

        if (generatedWorkbook) {
            helper.addAttachment(generatedFilename, new ByteArrayResource(generatedAttachment));
        } else if (StringUtils.hasText(reminding.attachmentPath())) {
            File attachment = new File(reminding.attachmentPath());
            if (!attachment.isFile()) {
                throw new IllegalStateException("Không tìm thấy tệp đính kèm của Reminding.");
            }
            helper.addAttachment(reminding.attachmentName(), attachment);
        }
        sender.send(message);
    }

    private String safeAttachmentBaseName(String email) {
        return email.replaceAll("[^A-Za-z0-9._+-]", "_");
    }

    private String logValue(String value) {
        return value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
    }

    private String renderText(String template, Map<String, String> values) {
        return replaceVariables(template, values, false).trim();
    }

    private String renderHtml(String template, Map<String, String> values) {
        String escaped = HtmlUtils.htmlEscape(template);
        return replaceVariables(escaped, values, true).replace("\r\n", "\n").replace("\n", "<br>\n");
    }

    private String replaceVariables(String template, Map<String, String> values, boolean htmlEscape) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String variable = templateVariables.cleanName(matcher.group(1));
            String column = templateVariables.findColumn(variable, values.keySet())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Không tìm thấy cột Excel tương ứng với biến {{" + variable + "}}."));
            String value = values.get(column);
            String replacement = value == null ? "" : value;
            if (htmlEscape) {
                replacement = HtmlUtils.htmlEscape(replacement);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}

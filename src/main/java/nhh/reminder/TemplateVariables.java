package nhh.reminder;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class TemplateVariables {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    public String cleanName(String name) {
        return HtmlUtils.htmlUnescape(name).trim();
    }

    public Optional<String> findColumn(String variableName, Collection<String> columns) {
        String normalizedVariable = normalize(cleanName(variableName));
        return columns.stream().filter(column -> normalize(column).equals(normalizedVariable)).findFirst();
    }

    public String canonicalize(String template, Collection<String> columns) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String variable = cleanName(matcher.group(1));
            String column = findColumn(variable, columns).orElseThrow(() ->
                    new IllegalArgumentException("Không tìm thấy cột Excel tương ứng với biến {{" + variable + "}}."));
            matcher.appendReplacement(result, Matcher.quoteReplacement("{{" + column + "}}"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}

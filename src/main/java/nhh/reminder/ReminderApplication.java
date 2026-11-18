package nhh.reminder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReminderApplication {

    public static void main(String[] args) throws IOException {
        if (Boolean.getBoolean("maildesk.packaged")) {
            Path dataDirectory = packagedDataDirectory();
            Files.createDirectories(dataDirectory.resolve("logs"));
            String databasePath = dataDirectory.resolve("reminder.db").toAbsolutePath().toString()
                    .replace('\\', '/');
            System.setProperty("maildesk.data-dir", dataDirectory.toAbsolutePath().toString());
            System.setProperty("spring.datasource.url", "jdbc:sqlite:" + databasePath);
            System.setProperty("logging.file.name", dataDirectory.resolve("logs/maildesk.log").toString());
        }
        SpringApplication.run(ReminderApplication.class, args);
    }

    private static Path packagedDataDirectory() {
        String configuredDirectory = System.getProperty("maildesk.data-dir");
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory);
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "Maildesk");
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            return Path.of(System.getProperty("user.home"), "AppData", "Local", "Maildesk");
        }
        if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home"), "Library", "Application Support", "Maildesk");
        }
        return Path.of(System.getProperty("user.home"), ".local", "share", "Maildesk");
    }

}

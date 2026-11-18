package nhh.reminder;

import java.awt.Desktop;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PackagedDesktopLauncher {

    private static final Logger logger = LoggerFactory.getLogger(PackagedDesktopLauncher.class);
    private final String port;

    public PackagedDesktopLauncher(@Value("${server.port:8080}") String port) {
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        if (!Boolean.getBoolean("maildesk.packaged")) {
            return;
        }
        try {
            Desktop.getDesktop().browse(URI.create("http://127.0.0.1:" + port));
        } catch (Exception exception) {
            logger.warn("Maildesk started, but the browser could not be opened automatically.", exception);
        }
    }
}

package nhh.reminder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EncryptionServiceTests {

    @TempDir
    Path tempDirectory;

    @Test
    void packagedModePersistsAnEncryptionKeyAndCanDecryptSavedValues() throws Exception {
        assumeTrue(System.getenv("APP_ENCRYPTION_KEY") == null
                || System.getenv("APP_ENCRYPTION_KEY").isBlank());
        String oldPackaged = System.getProperty("maildesk.packaged");
        String oldDataDirectory = System.getProperty("maildesk.data-dir");
        try {
            System.setProperty("maildesk.packaged", "true");
            System.setProperty("maildesk.data-dir", tempDirectory.toString());

            EncryptionService encryption = new EncryptionService();
            String encrypted = encryption.encrypt("smtp-app-password");

            assertEquals("smtp-app-password", encryption.decrypt(encrypted));
            assertTrue(Files.isRegularFile(tempDirectory.resolve("app-encryption.key")));
        } finally {
            restoreProperty("maildesk.packaged", oldPackaged);
            restoreProperty("maildesk.data-dir", oldDataDirectory);
        }
    }

    private void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}

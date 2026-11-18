package nhh.reminder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;

import org.springframework.stereotype.Service;

@Service
public class EncryptionService {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final Object KEY_FILE_LOCK = new Object();
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, result, 0, nonce.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể mã hóa app password.", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Trường này chưa có app password.");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= NONCE_LENGTH) {
                throw new IllegalStateException("App password đã lưu không hợp lệ.");
            }
            byte[] nonce = new byte[NONCE_LENGTH];
            byte[] encrypted = new byte[payload.length - NONCE_LENGTH];
            System.arraycopy(payload, 0, nonce, 0, nonce.length);
            System.arraycopy(payload, nonce.length, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Không thể giải mã app password; hãy kiểm tra APP_ENCRYPTION_KEY.", exception);
        }
    }

    private SecretKeySpec key() {
        String encodedKey = System.getenv("APP_ENCRYPTION_KEY");
        if (encodedKey == null || encodedKey.isBlank()) {
            encodedKey = packagedKey();
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) {
                throw new IllegalStateException("APP_ENCRYPTION_KEY phải là Base64 của khóa 32 byte.");
            }
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_ENCRYPTION_KEY không phải giá trị Base64 hợp lệ.", exception);
        }
    }

    private String packagedKey() {
        String dataDirectory = System.getProperty("maildesk.data-dir");
        if (!Boolean.getBoolean("maildesk.packaged") || dataDirectory == null || dataDirectory.isBlank()) {
            throw new IllegalStateException("Chưa cấu hình APP_ENCRYPTION_KEY để bảo vệ app password.");
        }
        Path keyFile = Path.of(dataDirectory, "app-encryption.key");
        try {
            Files.createDirectories(keyFile.getParent());
            synchronized (KEY_FILE_LOCK) {
                if (Files.exists(keyFile)) {
                    return Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                }
                byte[] key = new byte[32];
                secureRandom.nextBytes(key);
                String encoded = Base64.getEncoder().encodeToString(key);
                try {
                    Files.createFile(keyFile, PosixFilePermissions.asFileAttribute(
                            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
                } catch (UnsupportedOperationException exception) {
                    Files.createFile(keyFile);
                }
                Files.writeString(keyFile, encoded, StandardCharsets.UTF_8, StandardOpenOption.WRITE);
                return encoded;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc hoặc tạo khóa mã hóa trong thư mục dữ liệu Maildesk.",
                    exception);
        }
    }

}

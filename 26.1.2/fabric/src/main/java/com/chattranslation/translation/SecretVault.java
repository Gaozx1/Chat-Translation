package com.chattranslation.translation;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Embedded credential vault for the built-in free translation endpoint.
 *
 * <p>The API key is never stored, logged or shipped as plain text. Only an AES-GCM
 * ciphertext blob is kept in the class file; the passphrase that derives the AES key is
 * itself stored obfuscated and is reconstructed in memory at runtime. This keeps the
 * credential out of casual inspection of sources, build output and repository diffs.
 *
 * <p>The vault is deliberately dependency free (JDK only) and only unlocks once: the
 * derived key is cached, so the PBKDF2 work never repeats on the translation hot path.
 */
public final class SecretVault {

    /** Built-in free provider: OpenAI-compatible endpoint hosting {@code deepseek-v4.1-flash}. */
    public static final String FREE_ENDPOINT = "https://api.mcapple.top/v1/chat/completions";

    /** Built-in free provider model id. */
    public static final String FREE_MODEL_ID = "deepseek-v4.1-flash";

    /** Passphrase bytes, XOR-obfuscated with {@link #PASS_MASK}; never stored as text. */
    private static final byte[] PASS_OBF = {
            (byte) 0x39, (byte) 0x54, (byte) 0xF0, (byte) 0x53, (byte) 0x90, (byte) 0x79,
            (byte) 0x17, (byte) 0xB6, (byte) 0x29, (byte) 0x50, (byte) 0xF0, (byte) 0x53,
            (byte) 0x8D, (byte) 0x64, (byte) 0x18, (byte) 0xE2, (byte) 0x60, (byte) 0x4A,
            (byte) 0xF0, (byte) 0x52, (byte) 0x88, (byte) 0x7F, (byte) 0x4C, (byte) 0xE2,
            (byte) 0x2C, (byte) 0x0D, (byte) 0xAB, (byte) 0x1D, (byte) 0x89, (byte) 0x68,
            (byte) 0x17, (byte) 0xA8, (byte) 0x2A, (byte) 0x50, (byte) 0xF4, (byte) 0x0A,
            (byte) 0x80, (byte) 0x6E, (byte) 0x13, (byte) 0xA8, (byte) 0x29, (byte) 0x59,
            (byte) 0xF4, (byte) 0x4C,
    };

    private static final byte[] PASS_MASK = {
            (byte) 0x5A, (byte) 0x3C, (byte) 0x91, (byte) 0x27, (byte) 0xE4, (byte) 0x0B, (byte) 0x76, (byte) 0xD8,
    };

    private static final byte[] SALT = {
            (byte) 0x43, (byte) 0x54, (byte) 0x2D, (byte) 0x53, (byte) 0x65, (byte) 0x63,
            (byte) 0x72, (byte) 0x65, (byte) 0x74, (byte) 0x2D, (byte) 0x53, (byte) 0x61,
            (byte) 0x6C, (byte) 0x74, (byte) 0x21, (byte) 0x01,
    };

    private static final int ITERATIONS = 120_000;
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    /** Base64(IV || AES-GCM ciphertext+tag) over the UTF-8 API key, split for readability. */
    private static final String[] KEY_BLOB_PARTS = {
            "7w9fr8yEhDiBmCBMK0kg7UTqFWm",
            "CizRzppAWuc9Abv/3altb4EsV/F",
            "ycnAAmqZ+JYVrwg+5N65c2YNVR7",
            "1YB6uae8wWEfaC54vBxtB4GTw==",
    };

    private static volatile String cachedKey;

    private SecretVault() {
    }

    /**
     * Decrypts (once) and returns the built-in free endpoint API key.
     *
     * @throws IllegalStateException if the embedded blob cannot be opened
     */
    public static String freeApiKey() {
        String key = cachedKey;
        if (key == null) {
            synchronized (SecretVault.class) {
                key = cachedKey;
                if (key == null) {
                    key = reveal();
                    cachedKey = key;
                }
            }
        }
        return key;
    }

    private static String reveal() {
        try {
            byte[] plain = decrypt(String.join("", KEY_BLOB_PARTS));
            try {
                return new String(plain, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Chat Translation credential vault failed to open", e);
        }
    }

    private static byte[] decrypt(String base64) throws Exception {
        byte[] blob = Base64.getDecoder().decode(base64);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
        return cipher.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
    }

    private static SecretKeySpec deriveKey() throws Exception {
        byte[] passphrase = new byte[PASS_OBF.length];
        try {
            for (int i = 0; i < PASS_OBF.length; i++) {
                passphrase[i] = (byte) (PASS_OBF[i] ^ PASS_MASK[i % PASS_MASK.length]);
            }
            PBEKeySpec spec = new PBEKeySpec(
                    new String(passphrase, StandardCharsets.UTF_8).toCharArray(), SALT, ITERATIONS, 256);
            try {
                byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                return new SecretKeySpec(key, "AES");
            } finally {
                spec.clearPassword();
            }
        } finally {
            Arrays.fill(passphrase, (byte) 0);
        }
    }
}

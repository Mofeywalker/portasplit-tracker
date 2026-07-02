package de.wss.portasplit.service;

import de.wss.portasplit.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for a small secret stored at rest (the toom account password). The key comes
 * from {@code app.toom-reserve.crypto-key} (Base64, 16/24/32 bytes) — never from source. Output is
 * {@code Base64(iv‖ciphertext‖tag)}. When no key is configured, {@link #isConfigured()} is false and
 * the credential-backed feature stays disabled rather than storing anything in the clear.
 */
@Component
public class SecretCipher {

    private static final int IV_LEN = 12;      // 96-bit nonce, recommended for GCM
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(AppProperties props) {
        this.key = buildKey(props.toomReserve().cryptoKey());
    }

    private static SecretKeySpec buildKey(String base64) {
        if (!StringUtils.hasText(base64)) {
            return null;
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            return null;
        }
        return new SecretKeySpec(raw, "AES");
    }

    public boolean isConfigured() {
        return key != null;
    }

    /** Encrypts {@code plain}; returns Base64(iv‖ciphertext). Throws if no key is configured. */
    public String encrypt(String plain) {
        if (key == null) {
            throw new IllegalStateException("Kein Crypto-Key konfiguriert (APP_TOOM_RESERVE_CRYPTO_KEY)");
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Verschlüsseln fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /** Decrypts a value produced by {@link #encrypt}. Throws if no key is configured or on tamper. */
    public String decrypt(String enc) {
        if (key == null) {
            throw new IllegalStateException("Kein Crypto-Key konfiguriert (APP_TOOM_RESERVE_CRYPTO_KEY)");
        }
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Entschlüsseln fehlgeschlagen: " + e.getMessage(), e);
        }
    }
}

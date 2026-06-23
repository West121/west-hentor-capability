package com.sgs.capability.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** Minimal Java port of ABP SimpleStringCipher defaults used by account links. */
public class AbpSimpleStringCipher {
    private static final String DEFAULT_PASS_PHRASE = "gsKnGZ041HLL4IM8";
    private static final byte[] DEFAULT_INIT_VECTOR = "jkE49230Tf093b42".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] DEFAULT_SALT = "hgt!16kl".getBytes(StandardCharsets.US_ASCII);
    private static final int DEFAULT_ITERATIONS = 1000;

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return null;
        }
        try {
            byte[] encrypted = Base64.getDecoder().decode(cipherText);
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                    .generateSecret(new PBEKeySpec(DEFAULT_PASS_PHRASE.toCharArray(), DEFAULT_SALT, DEFAULT_ITERATIONS, 256))
                    .getEncoded();
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(DEFAULT_INIT_VECTOR));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid encrypted ABP string", ex);
        }
    }
}

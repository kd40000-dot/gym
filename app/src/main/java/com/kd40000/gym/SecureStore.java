package com.kd40000.gym;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the signing key in encrypted SharedPreferences, with the AES key
 * stored in Android Keystore. Android backups are disabled for this app.
 */
public final class SecureStore {
    private static final String ALIAS = "gym_qr_aes_v1";
    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        prefs = context.getSharedPreferences("gym_settings_v1", Context.MODE_PRIVATE);
    }

    public MemberConfig load() throws GeneralSecurityException, IOException {
        String encoded = prefs.getString("encrypted_key", null);
        if (encoded == null) return null;
        String[] fields = encoded.split(":", -1);
        if (fields.length != 2) throw new GeneralSecurityException("Corrupt saved key");
        byte[] iv = Base64.decode(fields[0], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(fields[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        String signingKey = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        return new MemberConfig(
            prefs.getString("member", ""),
            prefs.getString("superclub", "0"),
            prefs.getString("club", ""),
            prefs.getString("tagx", ""),
            signingKey
        );
    }

    public void save(MemberConfig config) throws GeneralSecurityException, IOException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(config.signingKey.getBytes(StandardCharsets.UTF_8));
        String protectedKey = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
            + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!prefs.edit()
            .putString("member", config.memberId)
            .putString("superclub", config.superclubId)
            .putString("club", config.clubId)
            .putString("tagx", config.tagx)
            .putString("encrypted_key", protectedKey)
            .commit()) {
            throw new IOException("Unable to persist membership settings");
        }
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        Key existing = store.getKey(ALIAS, null);
        if (existing != null) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }
}

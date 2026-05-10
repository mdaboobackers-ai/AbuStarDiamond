package com.goldsmith.billing.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class KeystoreManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val DB_KEY_ALIAS = "goldsmith_db_key"
        private const val PIN_KEY_ALIAS = "goldsmith_pin_key"
        private const val PREFS_FILE = "goldsmith_secure_prefs"
        private const val PREF_DB_KEY = "encrypted_db_key"
        private const val PREF_PIN_HASH = "pin_hash"
        private const val PREF_PIN_SALT = "pin_salt"
        private const val PREF_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private val PORTABLE_BACKUP_HEADER = byteArrayOf(0x41, 0x53, 0x44, 0x42, 0x33, 0x01) // ASDB3
        private const val PORTABLE_BACKUP_SALT = "AbuStarDiamonds.GoogleDrive.PortableBackup.v3"
        private const val PORTABLE_BACKUP_SECRET = "com.goldsmith.billing.drive.backup.mdaboobackers19"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // ── Database key ──────────────────────────────────────────────────────────
    fun getOrCreateDatabaseKey(): String {
        val stored = securePrefs.getString(PREF_DB_KEY, null)
        if (stored != null) return stored

        val key = generateSecureKey(32)
        securePrefs.edit().putString(PREF_DB_KEY, key).apply()
        return key
    }

    private fun generateSecureKey(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ── PIN management ────────────────────────────────────────────────────────
    fun savePin(pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        securePrefs.edit()
            .putString(PREF_PIN_SALT, salt)
            .putString(PREF_PIN_HASH, hash)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = securePrefs.getString(PREF_PIN_SALT, null) ?: return false
        val storedHash = securePrefs.getString(PREF_PIN_HASH, null) ?: return false
        return hashPin(pin, salt) == storedHash
    }

    fun isPinSet(): Boolean = securePrefs.getString(PREF_PIN_HASH, null) != null

    fun resetPin() {
        securePrefs.edit()
            .remove(PREF_PIN_SALT)
            .remove(PREF_PIN_HASH)
            .apply()
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hashPin(pin: String, salt: String): String {
        // Use PBKDF2 with many iterations for PIN hashing
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            Base64.decode(salt, Base64.NO_WRAP),
            100_000,  // iterations
            256       // key length bits
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    // ── Biometric ─────────────────────────────────────────────────────────────
    fun setBiometricEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean(PREF_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(): Boolean =
        securePrefs.getBoolean(PREF_BIOMETRIC_ENABLED, false)

    // ── Keystore AES key for encrypting backup ─────────────────────────────────
    fun getOrCreateBackupKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(DB_KEY_ALIAS)) {
            return (keyStore.getEntry(DB_KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                DB_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    fun encryptBytes(data: ByteArray): ByteArray {
        val key = getOrCreateBackupKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        // Prepend IV length (4 bytes) + IV + encrypted data
        return iv.size.toByteArrayLE() + iv + encrypted
    }

    fun decryptBytes(data: ByteArray): ByteArray {
        val key = getOrCreateBackupKey()
        val ivLen = data.sliceArray(0..3).toIntLE()
        val iv = data.sliceArray(4 until 4 + ivLen)
        val encrypted = data.sliceArray(4 + ivLen until data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }

    fun encryptPortableBackupBytes(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, portableBackupKey())
        return PORTABLE_BACKUP_HEADER + cipher.iv.size.toByteArrayLE() + cipher.iv + cipher.doFinal(data)
    }

    fun decryptBackupBytes(data: ByteArray): ByteArray {
        if (data.size > PORTABLE_BACKUP_HEADER.size + 4 && data.take(PORTABLE_BACKUP_HEADER.size).toByteArray().contentEquals(PORTABLE_BACKUP_HEADER)) {
            val offset = PORTABLE_BACKUP_HEADER.size
            val ivLen = data.sliceArray(offset until offset + 4).toIntLE()
            val iv = data.sliceArray(offset + 4 until offset + 4 + ivLen)
            val encrypted = data.sliceArray(offset + 4 + ivLen until data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, portableBackupKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            return cipher.doFinal(encrypted)
        }
        return decryptBytes(data)
    }

    private fun portableBackupKey(): SecretKey {
        val spec = PBEKeySpec(
            PORTABLE_BACKUP_SECRET.toCharArray(),
            PORTABLE_BACKUP_SALT.toByteArray(Charsets.UTF_8),
            120_000,
            256
        )
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun Int.toByteArrayLE(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )

    private fun ByteArray.toIntLE(): Int =
        (this[0].toInt() and 0xFF) or
        ((this[1].toInt() and 0xFF) shl 8) or
        ((this[2].toInt() and 0xFF) shl 16) or
        ((this[3].toInt() and 0xFF) shl 24)
}

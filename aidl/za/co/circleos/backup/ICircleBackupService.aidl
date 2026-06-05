/*
 * Copyright (C) 2026 Circle OS contributors
 *
 * Binder surface for Circle Backup. Published under
 * ServiceManager.getService("circle.backup") by
 * com.circleos.server.backup.CircleBackupService.
 *
 * Encrypted local backup. AES-256-GCM, key derived from a user PIN
 * via PBKDF2 (SHA-256, 100_000 iterations, 16-byte device salt).
 * Backup target: /data/circle/backup/. Restore reads the same path.
 *
 * For alpha the backup payload is the privacy DB + permission prefs +
 * update prefs -- everything Circle services own. App data is not
 * included; that's covered by Android's standard backup transport
 * (Auto Backup) which we leave as-is.
 */

package za.co.circleos.backup;

interface ICircleBackupService {

    /**
     * Whether the user has set a backup PIN. Until they do, backup is
     * disabled (we never persist plaintext settings to disk).
     */
    boolean isConfigured();

    /**
     * Run a backup now. Reads every Circle SharedPreferences file +
     * the privacy DB, AES-GCM encrypts with the cached key, writes to
     * /data/circle/backup/circle-backup-{timestamp}.cbk. Returns the
     * timestamp the backup is keyed by, or 0 on failure.
     * Requires MANAGE_PRIVACY.
     */
    long backupNow();

    /**
     * Number of stored backups under /data/circle/backup/.
     * Bounded list -- oldest backups are pruned to keep at most 5.
     */
    int getBackupCount();

    /**
     * Restore from the backup keyed by {@code backupTimestamp}.
     * Returns true on success. Requires MANAGE_PRIVACY.
     */
    boolean restore(long backupTimestamp);
}

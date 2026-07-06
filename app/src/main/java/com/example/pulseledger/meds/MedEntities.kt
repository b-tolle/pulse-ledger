package com.example.pulseledger.meds

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meds")
data class Med(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val doseMg: Double,
    val unitsPerDay: Double,       // e.g. 1.0, 0.5, 2.0
    val startedEpoch: Long,        // when you began (or changed dose)
    val quantityOnHand: Int,       // pills at last refill
    val lastRefillEpoch: Long,
    val notes: String? = null,
)

@Entity(tableName = "dose_log")
data class DoseLog(
    @PrimaryKey val epochMillis: Long,
    val medId: Long,
    val taken: Boolean,            // taken vs skipped
)

/**
 * Private log — substances, mushrooms, cannabis, anything you want correlated
 * but kept out of the main screens. UI for this table sits behind BiometricPrompt
 * and is reachable only from Settings ▸ Private log. Consider SQLCipher if you
 * want the DB itself encrypted at rest beyond Android's file-based encryption.
 */
@Entity(tableName = "private_log")
data class PrivateEntry(
    @PrimaryKey val epochMillis: Long,
    val kind: String,              // free-form: "cannabis", "psilocybin", ...
    val amount: String? = null,
    val note: String? = null,
)

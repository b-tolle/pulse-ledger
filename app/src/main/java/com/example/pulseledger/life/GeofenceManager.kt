package com.example.pulseledger.life

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-defined places (Home, Work, Gym, Clinic, her-favorite-restaurant…).
 * Register with Android's GeofencingClient; on ENTER/EXIT we write PlaceVisit
 * rows. Joining visits with HR/stress minutes gives "HR by place" and the
 * white-coat comparison. Requires ACCESS_FINE_LOCATION and, for reliable
 * transitions, ACCESS_BACKGROUND_LOCATION ("Allow all the time").
 */
@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lat: Double, val lng: Double,
    val radiusM: Float = 120f,
    val kind: String = "generic",   // home|work|gym|clinic|social
)

@Entity(tableName = "place_visits")
data class PlaceVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeId: Long,
    val enterEpoch: Long,
    val exitEpoch: Long?,
)

@Entity(tableName = "together_sessions")
data class TogetherSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpoch: Long,
    val endEpoch: Long,
    val placeId: Long?,          // null = out and about
    val dateNight: Boolean = false,
)

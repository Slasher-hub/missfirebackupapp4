package com.example.missfirebackupapp.util

import kotlin.math.*

object CoordinateUtils {
    // Simple WGS84 -> UTM (approx) conversion; not handling all edge cases.
    // Returns Triple(easting, northing, zoneString) e.g., (500000.0, 7420000.0, "23S")
    fun wgs84ToUTM(lat: Double, lon: Double): Triple<Double, Double, String> {
        val zone = floor((lon + 180) / 6) + 1
        val latRad = Math.toRadians(lat)
        val lonRad = Math.toRadians(lon)
        val a = 6378137.0
        val f = 1 / 298.257223563
        val k0 = 0.9996
        val e = sqrt(f * (2 - f))
        val lonOrigin = Math.toRadians((zone * 6) - 183)
        val N = a / sqrt(1 - e * e * sin(latRad).pow(2.0))
        val T = tan(latRad).pow(2.0)
        val C = (e * e / (1 - e * e)) * cos(latRad).pow(2.0)
        val A = cos(latRad) * (lonRad - lonOrigin)
        val M = a * ((1 - e * e / 4 - 3 * e.pow(4.0) / 64 - 5 * e.pow(6.0) / 256) * latRad
                - (3 * e * e / 8 + 3 * e.pow(4.0) / 32 + 45 * e.pow(6.0) / 1024) * sin(2 * latRad)
                + (15 * e.pow(4.0) / 256 + 45 * e.pow(6.0) / 1024) * sin(4 * latRad)
                - (35 * e.pow(6.0) / 3072) * sin(6 * latRad))
        var easting = (k0 * N * (A + (1 - T + C) * A.pow(3.0) / 6 + (5 - 18 * T + T * T + 72 * C - 58 * (e * e / (1 - e * e))) * A.pow(5.0) / 120) + 500000.0)
        var northing = (k0 * (M + N * tan(latRad) * (A.pow(2.0) / 2 + (5 - T + 9 * C + 4 * C * C) * A.pow(4.0) / 24 + (61 - 58 * T + T * T + 600 * C - 330 * (e * e / (1 - e * e))) * A.pow(6.0) / 720)))
        val hemisphere = if (lat < 0) 'S' else 'N'
        if (lat < 0) {
            northing += 10000000.0
        }
        val zoneStr = "${zone.toInt()}$hemisphere"
        return Triple(easting, northing, zoneStr)
    }

    // Datum adjustments (very simplified offsets for SAD69/SIRGAS2000 demonstration)
    private data class Offset(val dLat: Double, val dLon: Double)
    private val SAD69_OFFSET = Offset(-0.0005, -0.0006) // ~50-60m placeholder

    fun adjustDatum(lat: Double, lon: Double, system: String): Pair<Double, Double> {
        return when {
            system.startsWith("SAD69") -> Pair(lat + SAD69_OFFSET.dLat, lon + SAD69_OFFSET.dLon)
            // SIRGAS2000 treated similar to WGS84 for most practical contexts
            else -> Pair(lat, lon)
        }
    }
}

package com.example.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object NbrbClient {
    private const val TAG = "NbrbClient"

    suspend fun getExchangeRateInByn(currency: String): Double = withContext(Dispatchers.IO) {
        val cleanCurrency = currency.trim().uppercase()
        if (cleanCurrency == "BYN" || cleanCurrency.isEmpty()) {
            return@withContext 1.0
        }

        try {
            val urlString = "https://api.nbrb.by/exrates/rates/$cleanCurrency?parammode=2"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val json = JSONObject(response.toString())
                val scale = json.getInt("Cur_Scale")
                val officialRate = json.getDouble("Cur_OfficialRate")

                if (scale > 0) {
                    val finalRate = officialRate / scale
                    Log.d(TAG, "Fetched rate for $cleanCurrency: $finalRate (Scale: $scale, Rate: $officialRate)")
                    return@withContext finalRate
                }
            } else {
                Log.e(TAG, "Failed to fetch rate for $cleanCurrency. Code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching rate for $cleanCurrency: ${e.message}", e)
        }

        // Offline / failure standard Belarus NBRB fallbacks (as of 2026/average)
        return@withContext when (cleanCurrency) {
            "USD" -> 3.25
            "EUR" -> 3.55
            "RUB" -> 3.42 / 100.0 // 100 RUB = 3.42 BYN
            else -> 1.0
        }
    }
}

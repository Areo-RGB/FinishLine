package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isUpdateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {

    private const val TAG = "UpdateChecker"

    suspend fun checkForUpdates(
        currentVersionName: String,
        repoSlug: String = "PaZiske/FinishLine" // Default GitHub repo
    ): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$repoSlug/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FinishLine-App")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode == 200) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)

                val tagName = json.optString("tag_name", "").removePrefix("v")
                val releaseNotes = json.optString("body", "No release notes provided.")
                val htmlUrl = json.optString("html_url", "https://github.com/$repoSlug/releases")

                var apkDownloadUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", htmlUrl)
                            break
                        }
                    }
                }

                val isNewer = isVersionNewer(currentVersionName, tagName)

                UpdateInfo(
                    isUpdateAvailable = isNewer,
                    currentVersion = currentVersionName,
                    latestVersion = tagName.ifBlank { "v1.0.0" },
                    downloadUrl = apkDownloadUrl,
                    releaseNotes = releaseNotes
                )
            } else {
                Log.w(TAG, "GitHub API returned code ${connection.responseCode}")
                UpdateInfo(
                    isUpdateAvailable = false,
                    currentVersion = currentVersionName,
                    latestVersion = currentVersionName,
                    downloadUrl = "https://github.com/$repoSlug/releases",
                    releaseNotes = "Could not check GitHub releases."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking updates: ${e.message}", e)
            UpdateInfo(
                isUpdateAvailable = false,
                currentVersion = currentVersionName,
                latestVersion = currentVersionName,
                downloadUrl = "https://github.com/$repoSlug/releases",
                releaseNotes = "Unable to connect: ${e.message}"
            )
        }
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        if (latest.isBlank()) return false
        val currParts = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = latest.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(currParts.size, lateParts.size)
        for (i in 0 until maxLen) {
            val c = currParts.getOrElse(i) { 0 }
            val l = lateParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    fun openDownloadUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL $url", e)
        }
    }
}

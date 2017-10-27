package com.simplemobiletools.musicplayer.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelManager
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.BusProvider
import com.simplemobiletools.musicplayer.models.Events
import com.squareup.otto.Bus
import java.util.HashSet
import com.github.salomonbrys.kotson.*
import android.provider.MediaStore.MediaColumns
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.StringReader

class DownloadBroadcastReceiver: BroadcastReceiver() {
    companion object {
        private val TAG = DownloadBroadcastReceiver::class.java.simpleName
        private lateinit var mBus: Bus
    }

    private fun onDownloadSuccess(destination: File, playlistNames: Collection<String>) {
        Log.e(TAG, "Successfully downloaded file; passing it to MainActivity via SongDownloaded event.")
        playlistNames.forEach {
            mBus.post(Events.SongDownloaded(destination, it))
        }
    }

    private fun handleChoirSong(song: ChoirSong, basepath: File) {
        val destination = File(basepath, song.filename)
        FuelManager.instance.baseHeaders = mapOf("Authorization" to "banana")
        Fuel.download(song.url)
                .destination { response, url ->
                    destination
                }
                .progress { readBytes, totalBytes ->
                    if (readBytes == totalBytes) {
                        Log.e(TAG, "Download progress completed. Allowing .response{} handler to call onDownloadSuccess().")
                    }
                }
                .response { request, response, result ->
                    result.fold(success = { response ->
                        onDownloadSuccess(destination, song.parts)
                    }, failure = { error ->
                        Log.e(TAG, "During downloading, an error occurred ${error} :(")
                    })
                }
    }
    private fun handleChoirDataResponse(cdr: ChoirDataResponse, context: Context) {
        // uses internal storage i.e. app file path; cleared every time we handle a choir data response
        val cachedir = context.getFileStreamPath("songcache")
        if (cachedir.exists()) {
            cachedir.deleteRecursively()
        }
        cachedir.mkdir()

        // Calculate list of playlists. I know this means looping twice. I think that's livable
        // for now. It also relies on a silly hard-coded sorting trick to get the parts into
        // Soprano, Alto, Tenor, Bass order.
        val songSet = HashSet<String>()

        cdr.songs.forEach({ song ->
            song.parts.forEach({ part ->
                songSet.add(part)
            })
        })

        // Delete all playlists
        mBus.post(Events.SetPlaylistList(
                songSet.sortedWith(compareBy({! it.startsWith("Soprano")}, { ! it.startsWith("Alto") }, { ! it.startsWith("Tenor") },{! it.startsWith("Bass")}, {it}))))

        for (song in cdr.songs) {
            val request = DownloadManager.Request(Uri.parse(song.url))
            // Save it somewhere we can read it.
            request.setDestinationInExternalFilesDir(context.applicationContext, "choir-downloads", Uri.parse(song.url).path)
            // Add authz.
            if (context.config.currentPassword.isNotEmpty()) {
                request.addRequestHeader("Authorization", context.config.currentPassword)
            }
            // Add notification metadta.
            request.setTitle("Choir song: ${song.url.split(Regex("/")).last()}")
            // HACK alert :) Using JSON in description to pass data downstream.
            request.setDescription(song.parts.toJsonArray().toString())
            // Hide the notification anyway :)
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            // Send it :)
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager)?.apply {
                enqueue(request)
            }
        }
    }

    // DownloadManager gives us "file paths" as URIs, which are really content:// URIs, so OK, let's
    // resolve those.
    fun getFilePathFromUri(c: Context, uri: Uri): String {
        if ("content" == uri.scheme) {
            val filePathColumn = arrayOf(MediaColumns.DATA)
            val contentResolver = c.contentResolver

            val cursor = contentResolver.query(uri, filePathColumn, null, null, null)

            cursor!!.moveToFirst()

            val columnIndex = cursor.getColumnIndex(filePathColumn[0])
            val filePath = cursor.getString(columnIndex)
            cursor.close()
            return filePath
        } else if ("file" == uri.scheme) {
            return File(uri.path).absolutePath
        } else {
            return "unknown URL scheme, everything will break"
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        mBus = BusProvider.instance
        mBus.register(this)

        Log.e("OMGOMGOMGOMGOMGOMGOMOMG", " OMG OMG OMG OMG OMG OMG OMG OMG OMG")
        if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
            Toast.makeText(context, "omg huh maybe good", Toast.LENGTH_SHORT).show()
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
            val query = DownloadManager.Query()
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE)
            if (dm is DownloadManager) {
                val receivedID = intent.getLongExtra(
                        DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                query.setFilterById(receivedID)
                val cursor = dm.query(query)
                if (cursor.count == 0) {
                    Log.e("WEIRD", "cursor.count was 0, is not that weird? seems weird")
                    return;
                }

                // it shouldn't be empty, but just in case
                if (!cursor.moveToFirst()) {
                    Log.e(TAG, "Empty row");
                    return;
                }

                val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI))
                    // TODO(someday): Stop relying on the URL text :)
                    val isJson = uri.endsWith(".json")
                    val filenameEtc = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)) ?: "FILENAME MISSING, Aiee"
                    Log.e(TAG, "OMG FILE PATH WAS ${filenameEtc}")
                    val filePath = getFilePathFromUri(context, Uri.parse(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))))
                    val openFile = BufferedReader(FileReader(filePath))
                    if (isJson) {
                        // Process it now, and enqueue other downloads.
                        Log.e(TAG, "huh parsing starting")
                        val response = openFile.readText()
                        Log.e(TAG, "Response was: ${response}")
                        val cdr = ChoirDataResponse.Deserializer().deserialize(StringReader(response))
                        Log.e(TAG, "huh parsing worked")
                        if (cdr == null) {
                            Log.e(TAG, "HMM yikes cdr was null")
                            Log.e(TAG, "Response was: ${response}")
                        } else {
                            Log.e(TAG, "YAY NOT NULL")
                            handleChoirDataResponse(cdr, context)
                        }
                    } else {
                        Log.e(TAG, "OK we got a non-JSON, presumably music data")
                        // Use the "Description" field as cached JSON of the list of playlists this goes into. A bit hacky, but I don't have any better ideas.
                        val description = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION))
                        Log.e(TAG, "Parsing description: ${description}...")
                        val gson = Gson()
                        val descriptionParsed = gson.fromJson<List<String>>(description)
                        Log.e(TAG, "Parsed description! ${descriptionParsed}")
                        this.onDownloadSuccess(File(filePath), descriptionParsed)
                    }
                }
            }
        }
    }
}

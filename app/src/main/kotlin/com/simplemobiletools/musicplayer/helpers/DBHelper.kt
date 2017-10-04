package com.simplemobiletools.musicplayer.helpers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Song
import java.io.File

class DBHelper private constructor(val context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    private val TABLE_NAME_PLAYLISTS = "playlists"
    private val COL_ID = "id"
    private val COL_TITLE = "title"

    private val TABLE_NAME_SONGS = "songs"
    private val COL_PATH = "path"
    private val COL_PLAYLIST_ID = "playlist_id"
    private val COL_PLAYLIST_DOWNLOADED = "playlist_downloaded";

    private val mDb: SQLiteDatabase = writableDatabase

    companion object {
        private val DB_VERSION = 1
        val DB_NAME = "playlists.db"
        val INITIAL_PLAYLIST_ID = 1

        fun newInstance(context: Context): DBHelper {
            return DBHelper(context)
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_PLAYLISTS ($COL_ID INTEGER PRIMARY KEY, $COL_TITLE TEXT)")
        createSongsTable(db)
        addInitialPlaylist(db)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }

    private fun createSongsTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE $TABLE_NAME_SONGS ($COL_ID INTEGER PRIMARY KEY, $COL_PATH TEXT, $COL_PLAYLIST_ID INTEGER, " +
                "UNIQUE($COL_PATH, $COL_PLAYLIST_ID) ON CONFLICT IGNORE)")
    }

    private fun addInitialPlaylist(db: SQLiteDatabase) {
        val initialPlaylist = context.resources.getString(R.string.initial_playlist)
        val playlist = Playlist(INITIAL_PLAYLIST_ID, initialPlaylist)
        addPlaylist(playlist, db)
    }

    private fun addPlaylist(playlist: Playlist, db: SQLiteDatabase) {
        insertPlaylist(playlist, db)
    }

    fun insertPlaylist(playlist: Playlist, db: SQLiteDatabase = mDb): Int {
        val values = ContentValues().apply { put(COL_TITLE, playlist.title) }
        val insertedId = db.insert(TABLE_NAME_PLAYLISTS, null, values).toInt()
        return insertedId
    }

    fun removePlaylist(id: Int) {
        removePlaylists(arrayListOf(id))
    }

    fun removePlaylists(ids: ArrayList<Int>) {
        val args = TextUtils.join(", ", ids.filter { it != INITIAL_PLAYLIST_ID })
        val selection = "$COL_ID IN ($args)"
        mDb.delete(TABLE_NAME_PLAYLISTS, selection, null)

        val songSelection = "$COL_PLAYLIST_ID IN ($args)"
        mDb.delete(TABLE_NAME_SONGS, songSelection, null)

        if (ids.contains(context.config.currentPlaylist)) {
            context.playlistChanged(DBHelper.INITIAL_PLAYLIST_ID)
        }
    }

    fun updatePlaylist(playlist: Playlist): Int {
        val selectionArgs = arrayOf(playlist.id.toString())
        val values = ContentValues().apply { put(COL_TITLE, playlist.title) }
        val selection = "$COL_ID = ?"
        return mDb.update(TABLE_NAME_PLAYLISTS, values, selection, selectionArgs)
    }

    fun addSongToPlaylist(path: String) {
        addSongsToPlaylist(ArrayList<String>().apply { add(path) })
    }

    fun addSongsToPlaylist(paths: ArrayList<String>) {
        val playlistId = context.config.currentPlaylist
        addSongsToSpecificPlaylist(paths, playlistId)
    }

    fun addSongsToSpecificPlaylist(paths: ArrayList<String>, playlistId: Int) {
        for (path in paths) {
            ContentValues().apply {
                put(COL_PATH, path)
                put(COL_PLAYLIST_ID, playlistId)
                val insertResult = mDb.insert(TABLE_NAME_SONGS, null, this)
                Log.e("OMG INSERT", "INSERT RESULT ${insertResult} is that OK?")
                Log.e("OMG INSERT", "BTW ${path} and ${playlistId}")
            }
        }
    }

    fun getPlaylists(callback: (types: ArrayList<Playlist>) -> Unit) {
        val playlists = ArrayList<Playlist>(3)
        val cols = arrayOf(COL_ID, COL_TITLE)
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_PLAYLISTS, cols, null, null, null, null, "$COL_TITLE ASC")
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(COL_ID)
                    val title = cursor.getStringValue(COL_TITLE)
                    val playlist = Playlist(id, title)
                    playlists.add(playlist)
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        callback(playlists)
    }

    fun getPlaylistIdWithTitle(title: String): Int {
        val cols = arrayOf(COL_ID)
        val selection = "$COL_TITLE = ? COLLATE NOCASE"
        val selectionArgs = arrayOf(title)
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_PLAYLISTS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                return cursor.getIntValue(COL_ID)
            }
        } finally {
            cursor?.close()
        }
        return -1
    }

    fun getPlaylistWithId(id: Int): Playlist? {
        val cols = arrayOf(COL_TITLE)
        val selection = "$COL_ID = ?"
        val selectionArgs = arrayOf(id.toString())
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_PLAYLISTS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                val title = cursor.getStringValue(COL_TITLE)
                return Playlist(id, title)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    fun removeSongFromPlaylist(path: String, playlistId: Int) {
        removeSongsFromPlaylist(ArrayList<String>().apply { add(path) }, playlistId)
    }

    fun removeSongsFromPlaylist(paths: ArrayList<String>, playlistId: Int = context.config.currentPlaylist) {
        val SPLICE_SIZE = 200
        for (i in 0..paths.size - 1 step SPLICE_SIZE) {
            val curPaths = paths.subList(i, Math.min(i + SPLICE_SIZE, paths.size))
            val questionMarks = getQuestionMarks(curPaths.size)
            var selection = "$COL_PATH IN ($questionMarks)"
            if (playlistId != -1) {
                selection += " AND $COL_PLAYLIST_ID = $playlistId"
            }
            val selectionArgs = curPaths.toTypedArray()

            mDb.delete(TABLE_NAME_SONGS, selection, selectionArgs)
        }
    }

    fun getPlaylistSongPaths(playlistId: Int): ArrayList<String> {
        val paths = ArrayList<String>()
        val cols = arrayOf(COL_PATH)
        val selection = "$COL_PLAYLIST_ID = ?"
        val selectionArgs = arrayOf(playlistId.toString())
        var cursor: Cursor? = null
        try {
            cursor = mDb.query(TABLE_NAME_SONGS, cols, selection, selectionArgs, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val path = cursor.getStringValue(COL_PATH)
                    Log.e("OMG PATHS", "${path} was the path, does it exist? we'll see")
                    if (File(path).exists()) {
                        paths.add(path)
                        Log.e("OMG PATHS", "wow yes it does, huh")
                    } else {
                        removeSongFromPlaylist(path, -1)
                    }
                } while (cursor.moveToNext())
            }
        } finally {
            cursor?.close()
        }
        Log.e("OMG PATHS", "about to return paths ${paths}")
        return paths
    }

    fun getSongs(): ArrayList<Song> {
        val SPLICE_SIZE = 200
        val paths = getPlaylistSongPaths(context.config.currentPlaylist)
        val songs = ArrayList<Song>(paths.size)
        if (paths.isEmpty()) {
            Log.e("getSongs", "in the early return, weird")
            return songs
        }

        for (i in 0..paths.size - 1 step SPLICE_SIZE) {
            val curPaths = paths.subList(i, Math.min(i + SPLICE_SIZE, paths.size))
            Log.e("HMM", "curPaths: ${curPaths}")
            val uri = android.net.Uri.fromFile(Environment.getDataDirectory())
            val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA)
            val questionMarks = getQuestionMarks(curPaths.size)
            val selection = "${MediaStore.Audio.Media.DATA} IN ($questionMarks)"
            val selectionArgs = curPaths.toTypedArray()

            for (path in curPaths) {
                songs.add(Song(0, "title", "artist", path, 2))
            }
        }
        Log.e("HMM", "about to return ${songs}")
        return songs
    }

    private fun getQuestionMarks(cnt: Int) = "?" + ",?".repeat(Math.max(cnt - 1, 0))
}

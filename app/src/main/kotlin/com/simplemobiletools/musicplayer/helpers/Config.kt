package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var isShuffleEnabled: Boolean
        get() = prefs.getBoolean(SHUFFLE, true)
        set(shuffle) = prefs.edit().putBoolean(SHUFFLE, shuffle).apply()

    var sorting: Int
        get() = prefs.getInt(SORTING, SORT_BY_TITLE)
        set(sorting) = prefs.edit().putInt(SORTING, sorting).apply()

    var equalizer: Int
        get() = prefs.getInt(EQUALIZER, 0)
        set(equalizer) = prefs.edit().putInt(EQUALIZER, equalizer).apply()

    var development: Int
        get() = prefs.getInt(DEVELOPMENT, 0)
        set(equalizer) = prefs.edit().putInt(DEVELOPMENT, equalizer).apply()

    var wasInitialPlaylistFilled: Boolean
        get() = prefs.getBoolean(WAS_INITIAL_PLAYLIST_FILLED, false)
        set(wasInitialPlaylistFilled) = prefs.edit().putBoolean(WAS_INITIAL_PLAYLIST_FILLED, wasInitialPlaylistFilled).apply()

    var currentPlaylist: Int
        get() = prefs.getInt(CURRENT_PLAYLIST, DBHelper.INITIAL_PLAYLIST_ID)
        set(currentPlaylist) = prefs.edit().putInt(CURRENT_PLAYLIST, currentPlaylist).apply()

    var repeatSong: Boolean
        get() = prefs.getBoolean(REPEAT_SONG, false)
        set(repeat) = prefs.edit().putBoolean(REPEAT_SONG, repeat).apply()
}

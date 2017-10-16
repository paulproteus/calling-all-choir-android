package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.updateTextColors
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.dialogs.PasswordEntryDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.FORCE_REFRESH
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupCustomizeColors()
        setupManagePlaylists()
        setupEqualizer()
        setupDevelopment()
        setupForceRefresh()
        setupStorePassword()
        updateTextColors(settings_scrollview)
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupManagePlaylists() {
        settings_manage_playlists_holder.setOnClickListener {
            startActivity(Intent(this, PlaylistsActivity::class.java))
        }
    }

    private fun setupDevelopment() {
        val items = arrayListOf(
                RadioItem(0, "No"),
                RadioItem(1, "Yes")
        )
        settings_development.text = items[config.development].title
        settings_development_holder.setOnClickListener {
            RadioGroupDialog(this@SettingsActivity, items, config.development) {
                config.development = it as Int
                settings_development.text = items[it].title
            }
        }
    }


    private fun setupForceRefresh() {
        settings_force_refresh_holder.setOnClickListener {
            sendIntent(FORCE_REFRESH)
        }
    }

    private fun setupStorePassword() {
        settings_store_password_holder.setOnClickListener {
            val currentPassword = config.currentPassword
            PasswordEntryDialog(this@SettingsActivity, currentPassword) {
                val newPassword = it.toLowerCase().replace(" ", "")
                if (it == "") {
                    toast("OK! Using sample data.")
                    config.development = 1
                } else if (it != currentPassword) {
                    config.currentPassword = newPassword
                    toast("Saved password.")
                }
            }
        }
    }

    private fun setupEqualizer() {
        val equalizer = MusicService.mEqualizer ?: return
        val items = arrayListOf<RadioItem>()
        (0..equalizer.numberOfPresets - 1).mapTo(items) { RadioItem(it, equalizer.getPresetName(it.toShort())) }

        settings_equalizer.text = items[config.equalizer].title
        settings_equalizer_holder.setOnClickListener {
            RadioGroupDialog(this@SettingsActivity, items, config.equalizer) {
                config.equalizer = it as Int
                settings_equalizer.text = items[it].title
            }
        }
    }
}

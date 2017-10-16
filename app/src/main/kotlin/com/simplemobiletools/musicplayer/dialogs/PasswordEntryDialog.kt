package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.content.Context
import android.support.v7.app.AlertDialog
import android.view.WindowManager
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.value
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.dialog_edit_password.view.*

class PasswordEntryDialog(val activity: Activity, var currentPassword: String, val callback: (newPassword: String) -> Unit) : AlertDialog.Builder(activity) {
    init {
        val view = activity.layoutInflater.inflate(R.layout.dialog_edit_password, null).apply {
            edit_password_text.setText(currentPassword)
        }
        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
            window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            activity.setupDialogStuff(view, this, R.string.set_password)
            getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener({
                val newPassword = view.edit_password_text.value
                val currentPassword = currentPassword
                val isPasswordChanged = (newPassword != currentPassword)

                callback(newPassword)
                dismiss()
            })
        }
    }
}

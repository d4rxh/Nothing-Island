package com.d4rxh.nothingisland.service

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import androidx.core.content.ContextCompat
import com.d4rxh.nothingisland.R

class NothingInCallService : InCallService() {

    companion object {
        private const val TAG = "NothingInCallService"
        var activeCall: Call? = null
            private set

        fun answerCurrentCall(): Boolean {
            val call = activeCall ?: return false
            return try {
                val videoState = call.details?.videoState ?: VideoProfile.STATE_AUDIO_ONLY
                call.answer(videoState)
                Log.i(TAG, "Answered call via InCallService Call.answer($videoState)")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Error in Call.answer: ${e.message}")
                try {
                    call.answer(VideoProfile.STATE_AUDIO_ONLY)
                    true
                } catch (e2: Exception) {
                    Log.w(TAG, "Fallback Call.answer failed: ${e2.message}")
                    false
                }
            }
        }

        fun declineOrEndCurrentCall(): Boolean {
            val call = activeCall ?: return false
            return try {
                if (call.state == Call.STATE_RINGING) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        call.reject(Call.REJECT_REASON_DECLINED)
                    } else {
                        @Suppress("DEPRECATION")
                        call.reject(false, null)
                    }
                } else {
                    call.disconnect()
                }
                activeCall = null
                Log.i(TAG, "Declined/Disconnected call via InCallService")
                true
            } catch (e: Exception) {
                Log.w(TAG, "Error in Call.disconnect: ${e.message}")
                false
            }
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            if (call == null) return
            handleCallState(call, state)
        }
    }

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        if (call == null) return
        activeCall = call
        call.registerCallback(callCallback)
        handleCallState(call, call.state)
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        if (call == activeCall) {
            activeCall = null
            IslandCallManager.onCallEnded()
        }
        call?.unregisterCallback(callCallback)
    }

    private fun handleCallState(call: Call, state: Int) {
        val details = call.details
        var callerNumber = details?.handle?.schemeSpecificPart ?: ""
        var callerName = details?.callerDisplayName
            ?: details?.contactDisplayName
            ?: ""

        var contactIcon: Drawable? = null

        // Lookup Contact Name & Avatar from phone book if permission is granted
        if (callerNumber.isNotEmpty()) {
            val contactInfo = resolveContactInfo(callerNumber)
            if (contactInfo.first.isNotEmpty()) {
                callerName = contactInfo.first
            }
            if (contactInfo.second != null) {
                contactIcon = contactInfo.second
            }
        }

        if (callerName.isEmpty()) {
            callerName = if (callerNumber.isNotEmpty()) callerNumber else "Incoming Call"
        }

        if (contactIcon == null) {
            contactIcon = ContextCompat.getDrawable(this, R.drawable.ic_call)
        }

        when (state) {
            Call.STATE_RINGING -> {
                IslandCallManager.onIncomingCall(
                    name = callerName,
                    number = callerNumber,
                    sourceAppName = "PHONE",
                    sourceAppIcon = contactIcon,
                    directCall = call
                )
            }
            Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                IslandCallManager.onCallActive(
                    name = callerName,
                    number = callerNumber,
                    sourceAppName = "PHONE",
                    sourceAppIcon = contactIcon,
                    directCall = call
                )
            }
            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                IslandCallManager.onCallEnded()
            }
        }
    }

    private fun resolveContactInfo(phoneNumber: String): Pair<String, Drawable?> {
        if (checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Pair("", null)
        }
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            )
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val name = if (nameIdx != -1) cursor.getString(nameIdx) ?: "" else ""
                    val photoUriStr = if (photoIdx != -1) cursor.getString(photoIdx) else null

                    var drawable: Drawable? = null
                    if (!photoUriStr.isNullOrEmpty()) {
                        try {
                            val photoUri = Uri.parse(photoUriStr)
                            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, photoUri))
                            } else {
                                @Suppress("DEPRECATION")
                                MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
                            }
                            drawable = BitmapDrawable(resources, bitmap)
                        } catch (e: Exception) {
                            // ignore photo decoding error
                        }
                    }
                    return Pair(name, drawable)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error looking up contact info: ${e.message}")
        }
        return Pair("", null)
    }
}

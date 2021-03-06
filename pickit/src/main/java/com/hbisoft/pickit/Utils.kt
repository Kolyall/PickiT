package com.hbisoft.pickit

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.CursorLoader
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

class Utils constructor() {
    private var failReason: String? = null
    fun errorReason(): String? {
        return failReason
    }

    @SuppressLint("NewApi")
    fun getRealPathFromURI_API19(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            return getFromKitKatDocument(uri, context)
        } else if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return getFromContent(uri, context)
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path
        }
        return null
    }

    private fun getFromContent(uri: Uri, context: Context): String? {
        if (isGooglePhotosUri(uri)) {
            return uri.lastPathSegment
        }
        if (getDataColumn(context, uri, null, null) == null) {
            failReason = "dataReturnedNull"
        }
        return getDataColumn(context, uri, null, null)
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private fun getFromKitKatDocument(uri: Uri, context: Context): String? {
        return if (isExternalStorageDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":").toTypedArray()
            val type = split[0]
            if ("primary".equals(type, ignoreCase = true)) {
                if (split.size > 1) {
                    Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                } else {
                    Environment.getExternalStorageDirectory().toString() + "/"
                }
            } else {
                "storage" + "/" + docId.replace(":", "/")
            }
        } else if (isRawDownloadsDocument(uri)) {
            val fileName = getFilePath(context, uri)
            val subFolderName = getSubFolders(uri)
            if (fileName != null) {
                return Environment.getExternalStorageDirectory().toString() + "/Download/" + subFolderName + fileName
            }
            val id = DocumentsContract.getDocumentId(uri)
            val contentUri =
                ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
            return getDataColumn(context, contentUri, null, null)
        } else if (isDownloadsDocument(uri)) {
            val fileName = getFilePath(context, uri)
            if (fileName != null) {
                return Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
            }
            var id = DocumentsContract.getDocumentId(uri)
            if (id.startsWith("raw:")) {
                id = id.replaceFirst("raw:".toRegex(), "")
                val file = File(id)
                if (file.exists()) return id
            }
            if (id.startsWith("raw%3A%2F")) {
                id = id.replaceFirst("raw%3A%2F".toRegex(), "")
                val file = File(id)
                if (file.exists()) return id
            }
            val contentUri =
                ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
            return getDataColumn(context, contentUri, null, null)
        } else if (isMediaDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":").toTypedArray()
            val type = split.first()
            var contentUri: Uri? = null
            when (type) {
                "image" -> {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                "video" -> {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                "audio" -> {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
            }
            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])
            return getDataColumn(context, contentUri, selection, selectionArgs)
        } else {
            return null
        }
    }

    private fun getSubFolders(uri: Uri): String {
        val replaceChars = uri.toString().replace("%2F", "/").replace("%20", " ").replace("%3A", ":")
        val bits = replaceChars.split("/").toTypedArray()
        val sub5 = bits[bits.size - 2]
        val sub4 = bits[bits.size - 3]
        val sub3 = bits[bits.size - 4]
        val sub2 = bits[bits.size - 5]
        val sub1 = bits[bits.size - 6]
        return if (sub1 == "Download") {
            "$sub2/$sub3/$sub4/$sub5/"
        } else if (sub2 == "Download") {
            "$sub3/$sub4/$sub5/"
        } else if (sub3 == "Download") {
            "$sub4/$sub5/"
        } else if (sub4 == "Download") {
            "$sub5/"
        } else {
            ""
        }
    }

    fun getRealPathFromURIBelowAPI19(context: Context?, contentUri: Uri?): String {
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val loader = CursorLoader(context, contentUri, projection, null, null, null)
        val cursor = loader.loadInBackground()
        val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        cursor.moveToFirst()
        val result = cursor.getString(columnIndex)
        cursor.close()
        return result
    }

    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        uri?:return null
        var cursor: Cursor? = null
        val column = MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } catch (e: Exception) {
            failReason = e.message
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun getFilePath(context: Context, uri: Uri): String? {
        var cursor: Cursor? = null
        val projection = arrayOf(MediaStore.Files.FileColumns.DISPLAY_NAME)
        try {
            cursor = context.contentResolver.query(
                uri, projection, null, null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                return cursor.getString(index)
            }
        } catch (e: Exception) {
            failReason = e.message
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isRawDownloadsDocument(uri: Uri): Boolean {
        val uriToString = uri.toString()
        return uriToString.contains("com.android.providers.downloads.documents/document/raw")
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }
}
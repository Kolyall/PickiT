package com.hbisoft.pickit

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.AsyncTask
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class DownloadAsyncTask(
    context: Context,
    private val taskId: Int,
    private val taskCallBack: TaskCallBack,
    private val filename: String
) : AsyncTask<Uri, Int?, String?>() {

    private var folder: File? = null
    private var returnCursor: Cursor? = null
    private var inputStream: InputStream? = null
    private var extension: String? = null
    private var errorReason: String? = ""
    private val contentResolver: ContentResolver

    init {
        folder = context.getExternalFilesDir("Temp")
        contentResolver = context.contentResolver
    }

    override fun onPreExecute() {
        taskCallBack.onPreExecute(taskId)

    }

    override fun onProgressUpdate(vararg values: Int?) {
        super.onProgressUpdate(*values)
        val post = values.firstOrNull()
        taskCallBack.onProgressUpdate(taskId, post)
    }

    override fun doInBackground(vararg params: Uri): String? {
        val uri = params.firstOrNull()
        if (uri == null) {
            errorReason = "a least one uri must be defined"
            return null
        }
        var file: File? = null
        var size = -1
        try {
            val mime = MimeTypeMap.getSingleton()
            extension = mime.getExtensionFromMimeType(contentResolver.getType(uri))
            inputStream = contentResolver.openInputStream(uri)

            returnCursor = contentResolver.query(uri, null, null, null, null)
            returnCursor.use { cursor ->
                if (cursor?.moveToFirst() == true) {
                    when (uri.scheme) {
                        "content" -> {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            size = cursor.getLong(sizeIndex).toInt()
                        }
                        "file" -> {
                            val ff = File(uri.path)
                            size = ff.length().toInt()
                        }
                    }
                }
            }
            file = if (extension == null) {
                File(folder.toString() + "/" + filename)
            } else {
                File(folder.toString() + "/" + filename + "." + extension)
            }
            val bis = BufferedInputStream(inputStream ?: return null)
            val fos = FileOutputStream(file)
            val data = ByteArray(1024)
            var total: Long = 0
            var count: Int
            while (bis.read(data).also { count = it } != -1) {
                if (!isCancelled) {
                    total += count.toLong()
                    if (size != -1) {
                        publishProgress((total * 100 / size).toInt())
                    }
                    fos.write(data, 0, count)
                }
            }
            fos.flush()
            fos.close()
        } catch (e: IOException) {
            Log.e("Pickit IOException = ", e.message)
            errorReason = e.message
        }
        return file?.absolutePath
    }

    override fun onPostExecute(result: String?) {
        if (result == null) {
            taskCallBack.onPostExecute(taskId, result, status = PickiTStatus.failed, reason = errorReason)
        } else {
            taskCallBack.onPostExecute(taskId, result, status = PickiTStatus.success)
        }
    }

}
package io.github.zadam.triliumsender

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import okhttp3.*
import okhttp3.internal.Util
import okio.BufferedSink
import okio.Okio
import okio.Source
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.*
import android.opengl.ETC1.getHeight
import android.opengl.ETC1.getWidth
import android.R.attr.bitmap
import android.opengl.ETC1.getHeight
import android.R.attr.maxWidth
import android.opengl.ETC1.getWidth
import android.R.attr.maxHeight






class ShareActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)

        val prefs = this.getSharedPreferences(MainActivity.PREFRENCES_NAME, Context.MODE_PRIVATE);

        val triliumAddress = prefs.getString(MainActivity.PREF_TRILIUM_ADDRESS, "");
        val token = prefs.getString(MainActivity.PREF_TOKEN, "");

        if (triliumAddress.isBlank() || token.isBlank()) {
            Toast.makeText(this, "Trilium Sender is not configured. Can't sent the image.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val imageUri = intent.extras!!.get(Intent.EXTRA_STREAM) as Uri
        val mimeType = contentResolver.getType(imageUri)

        val sendImageTask = SendImageTask(imageUri, mimeType, triliumAddress, token)
        sendImageTask.execute(null as Void?)
    }

    inner class SendImageResult (val success: Boolean, val contentLength: Long? = null)

    inner class SendImageTask internal constructor(private val imageUri: Uri, private val mimeType: String,
                                                   private val triliumAddress: String, private val token: String) : AsyncTask<Void, Void, SendImageResult>() {

        val TAG : String = "SendImageTask"

        override fun doInBackground(vararg params: Void): SendImageResult {

            val imageStream = contentResolver.openInputStream(imageUri);

            val imageBody = RequestBodyUtil.create(MediaType.parse(mimeType)!!, scaleImage(imageStream, mimeType))

            val contentLength = imageBody.contentLength()

            val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("upload", "image", imageBody)
                    .build()

            val client = OkHttpClient()

            val request = Request.Builder()
                    .url(triliumAddress + "/api/sender/image")
                    .addHeader("Authorization", token)
                    .post(requestBody)
                    .build()

            try {
                val response = client.newCall(request).execute()

                return SendImageResult(response.code() == 200, contentLength)
            }
            catch (e: Exception) {
                Log.e(TAG, "Sending to Trilium failed", e)

                return SendImageResult(false)
            }
        }

        override fun onPostExecute(result: SendImageResult) {
            if (result.success) {
                Toast.makeText(this@ShareActivity, "Image sent to Trilium (" + (result.contentLength!! / 1000) + " KB)", Toast.LENGTH_LONG).show()
            }
            else {
                Toast.makeText(this@ShareActivity, "Sending to Trilium failed", Toast.LENGTH_LONG).show()
            }

            finish()
        }

        override fun onCancelled() {
        }
    }

    private fun scaleImage(inputStream: InputStream, mimeType: String): InputStream {
        // we won't do anything with GIFs, PNGs etc. This is minority use case anyway
        if (mimeType != "image/jpeg") {
            return inputStream;
        }

        val options = BitmapFactory.Options()
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)

        val maxWidth = 2000
        val maxHeight = 2000

        val scale = Math.min(maxHeight.toFloat() / bitmap.width, maxWidth.toFloat() / bitmap.height)

        val newWidth : Int = if (scale < 1) (bitmap.width * scale).toInt() else bitmap.width;
        val newHeight : Int = if (scale < 1) (bitmap.height * scale).toInt() else bitmap.height;

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

        val baos = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos)
        val bitmapdata = baos.toByteArray()

        return ByteArrayInputStream(bitmapdata)
    }

    object RequestBodyUtil {
        fun create(mediaType: MediaType, inputStream: InputStream): RequestBody {
            return object : RequestBody() {
                override fun contentType(): MediaType? {
                    return mediaType
                }

                override fun contentLength(): Long {
                    try {
                        return inputStream.available().toLong()
                    } catch (e: IOException) {
                        return 0
                    }
                }

                @Throws(IOException::class)
                override fun writeTo(sink: BufferedSink) {
                    var source: Source? = null
                    try {
                        source = Okio.source(inputStream)
                        sink.writeAll(source!!)
                    } finally {
                        Util.closeQuietly(source)
                    }
                }
            }
        }
    }
}
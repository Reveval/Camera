package ru.startandroid.develop.p1311cameraintent

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {
    lateinit var directory: File
    lateinit var ivPhoto: ImageView

    //Обрабатываем результат получения фото из камеры
    val photo = registerForActivityResult(ActivityResultContracts
        .StartActivityForResult()) { result ->
        when(result.resultCode) {
            RESULT_OK -> {
                //вытаскиваем путь к получившемуся файлу
                val intent = result.data
                if (intent != null) {
                    Log.d(LOG_TAG, "Photo uri: ${intent.data}")
                    //пытаемся вытащить bitmap с получившимся изображением
                    val bundle = intent.extras
                    if (bundle != null) {
                        val obj = intent.extras!!.get("data")
                        if (obj is Bitmap) {
                            Log.d(LOG_TAG, "bitmap ${obj.width} x ${obj.height}")
                            ivPhoto.setImageBitmap(obj)
                        }
                    }
                } else {
                    Log.d(LOG_TAG, MESSAGE_INTENT_IS_NULL)
                }
            }

            RESULT_CANCELED -> Log.d(LOG_TAG, MESSAGE_RESULT_CANCELED)
        }
    }

    //Обрабатываем результат получения видео из камеры
    val video = registerForActivityResult(ActivityResultContracts
        .StartActivityForResult()) { result ->
        when(result.resultCode) {
            RESULT_OK -> {
                val intent = result.data
                if (intent != null) {
                    Log.d(LOG_TAG, "Video Uri: ${intent.data}")
                } else {
                    Log.d(LOG_TAG, MESSAGE_INTENT_IS_NULL)
                }
            }

            RESULT_CANCELED -> Log.d(LOG_TAG, MESSAGE_RESULT_CANCELED)
        }
    }

    /*
        В onCreate мы вызываем свой метод createDirectory, который в папке Pictures создаст папку
            для наших файлов и поместит соответствующий объект File в переменную directory.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createDirectory()
        ivPhoto = findViewById(R.id.ivPhoto)

        /*
            Вешаем обработчики на кнопки, где создаем Intent с соответствующими Action, добавляем
                в эти Intent желаемый путь к файлу и отправляем при помощи метода launch.
         */
        findViewById<Button>(R.id.btnPhoto).setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                //.putExtra(MediaStore.EXTRA_OUTPUT, generateFileUri(TYPE_PHOTO))
            photo.launch(intent)
        }

        findViewById<Button>(R.id.btnVideo).setOnClickListener {
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                //.putExtra(MediaStore.EXTRA_OUTPUT, generateFileUri(TYPE_VIDEO))
            video.launch(intent)
        }
    }

    /*
        Метод generateFileUri генерирует путь к файлу. Для этого он берет путь из directory,
            определяет префикс и расширение в зависимости от типа (фото или видео) и использует
            системное время, как основную часть имени файла. Далее все это конвертируется в Uri и
            возвращается как результат метода.
     */
    private fun generateFileUri(type: Int) : Uri {
        val file = when(type) {
            TYPE_PHOTO -> File("${directory.path}/photo_${System.currentTimeMillis()}.jpg")
            TYPE_VIDEO -> File("${directory.path}/video${System.currentTimeMillis()}.mp4")
            else -> File("")
        }
        Log.d(LOG_TAG, "fileName = $file")
        return FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", file)
    }

    private fun createDirectory() {
        directory = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "/MyFolder")
        if (!directory.exists()) directory.mkdirs()
    }

    companion object {
        const val TYPE_PHOTO = 1
        const val TYPE_VIDEO = 2

        const val LOG_TAG = "myLogs"

        const val MESSAGE_INTENT_IS_NULL = "Intent is null"
        const val MESSAGE_RESULT_CANCELED = "Canceled"
    }
}
package ru.startandroid.develop.p1321camerascreen

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics.*
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi

const val LOG_TAG = "myLogs"

//Напишем логику для получения информации о камерах девайса с использованием Camera2 API.
class MainActivity : AppCompatActivity() {
    private lateinit var cameraManager: CameraManager
    private lateinit var myCameras: Array<String?>

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
            Работу начнём с создания экземпляра класса CameraManager. Это менеджер системного
                сервиса, который позволяет отыскать доступные камеры, получить их характеристики
                нужные вам для работы и задать для камер настройки съемки.
         */
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager


        try {
            //получаем список камер с устройства
            myCameras = arrayOfNulls(cameraManager.cameraIdList.size)

            //выводим информацию по камере
            for (cameraID in cameraManager.cameraIdList) {
                Log.d(LOG_TAG, "cameraID = $cameraID")
                val id = cameraID.toInt()

                //Получаем характеристики камеры
                val cc = cameraManager.getCameraCharacteristics(cameraID)

                //Получаем список выходного формата, который поддерживает камера
                val configurationMap = cc.get(SCALER_STREAM_CONFIGURATION_MAP)

                //определяем, какая камера куда смотрит
                when(cc.get(LENS_FACING)) {
                    LENS_FACING_FRONT -> {
                        Log.d(LOG_TAG, "Camera with ID = $cameraID is Front Camera")
                    }

                    LENS_FACING_BACK -> {
                        Log.d(LOG_TAG, "Camera with ID = $cameraID is Back Camera")
                    }
                }

                //Получаем список разрешений, которые поддерживаются для формата jpeg
                val sizesJPEG = configurationMap?.getOutputSizes(ImageFormat.JPEG)
                if (sizesJPEG != null) {
                    for (item in sizesJPEG) {
                        Log.d(LOG_TAG, "w: ${item.width}, h: ${item.height}")
                    }
                } else {
                    Log.d(LOG_TAG, "Camera don't support JPEG")
                }
            }
        } catch (ex: Exception) {
            ex.message?.let { Log.d(LOG_TAG, it) }
        }
    }
}
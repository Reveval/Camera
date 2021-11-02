package ru.startandroid.develop.p1321preparecamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

const val LOG_TAG = "myLogs"
const val BACK_CAMERA_ID = 0
const val FRONT_CAMERA_ID = 1

class MainActivity : AppCompatActivity() {
    lateinit var myCameras: Array<CameraService?>
    lateinit var cameraManager: CameraManager

    lateinit var buttonOpenFrontCamera: Button
    lateinit var buttonOpenBackCamera: Button
    lateinit var buttonToMakeShot: Button

    lateinit var imageView: TextureView

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground")
        backgroundThread?.start()
        backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private fun stopBackgroundThread() {
        backgroundThread?.apply {
            quitSafely()
            try {
                join()
                backgroundThread = null
                backgroundHandler = null
            } catch (ex: InterruptedException) {
                ex.printStackTrace()
            }
        }
    }

    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d(LOG_TAG, "Запрашиваем разрешение")
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }

        imageView = findViewById(R.id.textureView)

        buttonOpenFrontCamera = findViewById(R.id.button_open_front_camera)
        buttonOpenBackCamera = findViewById(R.id.button_open_back_camera)
        buttonToMakeShot = findViewById(R.id.button_make_shot)

        buttonOpenFrontCamera.setOnClickListener {
            if (myCameras[BACK_CAMERA_ID]?.isOpen()!!) myCameras[BACK_CAMERA_ID]?.closeCamera()
            if (!myCameras[FRONT_CAMERA_ID]?.isOpen()!!) myCameras[FRONT_CAMERA_ID]?.openCamera()
        }

        buttonOpenBackCamera.setOnClickListener {
            if (myCameras[FRONT_CAMERA_ID]?.isOpen()!!) myCameras[FRONT_CAMERA_ID]?.closeCamera()
            if (!myCameras[BACK_CAMERA_ID]?.isOpen()!!) myCameras[BACK_CAMERA_ID]?.openCamera()
        }

        buttonToMakeShot.setOnClickListener {
            if (myCameras[BACK_CAMERA_ID]?.isOpen()!!) myCameras[BACK_CAMERA_ID]?.makePhoto()
            if (myCameras[FRONT_CAMERA_ID]?.isOpen()!!) myCameras[FRONT_CAMERA_ID]?.makePhoto()
        }

        /*
            В главном потоке создаем экземпляр mCameraManager и с его помощью заполним массив
            объектов myCameras. В данном случае их всего два — фронтальная и селфи камеры.
         */
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            //получаем список камер с устройства
            myCameras = arrayOfNulls(cameraManager.cameraIdList.size)

            for (cameraID in cameraManager.cameraIdList) {
                Log.d(LOG_TAG, "cameraID = $cameraID")
                val id = cameraID.toInt()

                //создаем обработчик для камеры
                myCameras[id] = CameraService(cameraManager, cameraID)
            }
        } catch (ex: Exception) {
            ex.message?.let { Log.d(LOG_TAG, it) }
            ex.printStackTrace()
        }
    }

    /*
        В классе CameraService мы разместим инициализацию камер и затем пропишем все коллбэки,
            которые потребуются.
     */
    inner class CameraService(_cameraManager: CameraManager, _cameraID: String) {
        private val cameraID = _cameraID
        private val cameraManager = _cameraManager
        private lateinit var cameraDevice: CameraDevice
        private lateinit var captureSession: CameraCaptureSession
        private lateinit var imageReader: ImageReader

        private val file = File(getExternalFilesDir(Environment.DIRECTORY_DCIM), "test1.jpg")

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        fun makePhoto() {
            try {
                val captureBuilder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureBuilder.addTarget(imageReader.surface)
                val captureCallback = object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult) {}
                }

                captureSession.apply {
                    stopRepeating()
                    abortCaptures()
                    capture(captureBuilder.build(), captureCallback, backgroundHandler)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        private val onImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
            Toast.makeText(this@MainActivity, "Photo is available for saving",
                Toast.LENGTH_SHORT).show()
            backgroundHandler?.post(ImageSaver(reader.acquireNextImage(), file))
        }

        /*
            CameraDevice.StateCallback - коллбэк состояния камеры. Он сообщит нам открыта ли
                камера, закрыта или может быть вообще ничего там нет и выдаст ошибку.
         */
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        val cameraCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                Log.d(LOG_TAG, "Open camera with id: ${cameraDevice.id}")

                createCameraPreviewSession()
            }

            override fun onDisconnected(camera: CameraDevice) {
                cameraDevice.close()
                Log.d(LOG_TAG, "Disconnect camera with id: ${cameraDevice.id}")

            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.d(LOG_TAG, "Error! Camera id: ${camera.id}, error: $error")
            }
        }

        /*
            Если камера доступна для работы (сработал метод onOpened()), вызываем метод ниже,
                который позволит вывести изображение в ImageView
         */
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun createCameraPreviewSession() {
            imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG,
                1)
            imageReader.setOnImageAvailableListener(onImageAvailableListener, backgroundHandler)

            val surface = Surface(imageView.surfaceTexture)

            try {
                val builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                builder.addTarget(surface)

                val stateCallback = object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            captureSession.setRepeatingRequest(builder.build(), null,
                                backgroundHandler)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {}

                }

                cameraDevice.createCaptureSession(listOf(surface), stateCallback, backgroundHandler)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        fun isOpen() : Boolean {
            return ::cameraDevice.isInitialized
        }

        @RequiresApi(Build.VERSION_CODES.M)
        fun openCamera() {
            try {
                if (checkSelfPermission(Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                        cameraManager.openCamera(cameraID, cameraCallback, backgroundHandler)
                }
            } catch (ex: Exception) {
                ex.message?.let { Log.d(LOG_TAG, it) }
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        fun closeCamera() {
            if (!::cameraDevice.isInitialized) cameraDevice.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onPause() {
        if (myCameras[BACK_CAMERA_ID]?.isOpen()!!) myCameras[BACK_CAMERA_ID]?.closeCamera()
        if (myCameras[FRONT_CAMERA_ID]?.isOpen()!!) myCameras[FRONT_CAMERA_ID]?.closeCamera()
        super.onPause()
        stopBackgroundThread()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
    }

    /*
        Класс ImageSaver, который быстренько перекачает данные с картинки в байтовый буфер, а
            оттуда уже в бинарный файл.
     */
    class ImageSaver(_image: Image, _file: File) : Runnable {
        private val image = _image
        private val file = _file

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        override fun run() {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            var output: FileOutputStream? = null
            try {
                output = FileOutputStream(file)
                output.write(bytes)
            } catch (ex: IOException) {
                ex.printStackTrace()
            } finally {
                image.close()
                if (output != null) {
                    try {
                        output.close()
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }
}
package com.bryan_lunay.opencv_parcial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.provider.MediaStore
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCamera2View
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

// La actividad principal implementa CvCameraViewListener2 para recibir
// los frames de la cámara de OpenCV en tiempo real
class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    // Vista de la cámara proporcionada por OpenCV
    private lateinit var cameraView: CameraBridgeViewBase
    // Vista para mostrar imágenes cargadas desde la galería
    private lateinit var galleryView: ImageView
    // Contenedor de los botones en la parte inferior
    private lateinit var btnLayout: LinearLayout

    // Controla si estamos en modo cámara (true) o galería (false)
    private var isCameraMode = true
    // Efecto actual: 0 = Normal, 1 = Sketch, 2 = Sepia
    private var currentEffect = 0
    // Guarda la imagen original cargada desde la galería como matriz OpenCV
    private var currentGalleryImage: Mat? = null
    // Índice de la cámara activa: trasera por defecto
    private var cameraIndex = CameraBridgeViewBase.CAMERA_ID_BACK

    // Declaración de la función nativa en C++ que aplica los filtros
    private external fun processImageNative(matAddrRgba: Long, effectMode: Int)

    companion object {
        init {
            // Carga la librería nativa compilada con C++ y OpenCV
            System.loadLibrary("opencv_parcial")
            // Inicializa OpenCV en modo debug
            OpenCVLoader.initDebug()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Activa el modo pantalla completa (oculta la barra de estado)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        // Inicializar vistas desde el layout XML
        cameraView = findViewById(R.id.camera_view)
        galleryView = findViewById(R.id.gallery_view)
        btnLayout = findViewById(R.id.btn_layout)

        // Mostrar la cámara y configurar el listener para recibir frames
        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)
        // Usar cámara trasera al inicio
        cameraView.setCameraIndex(cameraIndex)

        // Verificar si ya tenemos permiso de cámara
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // Si no hay permiso, solicitarlo al usuario
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            // Si ya hay permiso, desbloquear la cámara en OpenCV 4
            cameraView.setCameraPermissionGranted()
        }

        // Botón CAM/GAL: alterna entre modo cámara y modo galería
        findViewById<Button>(R.id.btn_mode).setOnClickListener {
            isCameraMode = !isCameraMode
            if (isCameraMode) {
                // Volver al modo cámara: ocultar galería, mostrar cámara
                galleryView.visibility = View.GONE
                cameraView.visibility = View.VISIBLE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraView.setCameraPermissionGranted()
                    cameraView.enableView()
                }
            } else {
                // Ir al modo galería: apagar cámara, mostrar galería y abrir selector
                cameraView.disableView()
                cameraView.visibility = View.GONE
                galleryView.visibility = View.VISIBLE
                openGallery()
            }
        }

        // Botón GIRAR CAM: cambia entre cámara trasera y delantera
        findViewById<Button>(R.id.btn_switch_cam).setOnClickListener {
            if (isCameraMode) {
                cameraView.disableView() // Apagar cámara actual
                // Alternar índice de cámara
                cameraIndex = if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK) {
                    CameraBridgeViewBase.CAMERA_ID_FRONT
                } else {
                    CameraBridgeViewBase.CAMERA_ID_BACK
                }
                // Configurar y encender la nueva cámara
                cameraView.setCameraIndex(cameraIndex)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraView.setCameraPermissionGranted()
                    cameraView.enableView()
                }
            }
        }

        // Botón FILTRO: cicla entre los efectos disponibles (0, 1, 2)
        findViewById<Button>(R.id.btn_effect).setOnClickListener {
            currentEffect = (currentEffect + 1) % 4
            // Si estamos en modo galería, aplicar el nuevo efecto inmediatamente
            if (!isCameraMode && currentGalleryImage != null) {
                applyEffectToGalleryImage()
            }
        }
    }

    // Se ejecuta al volver a la app: reactiva la cámara si estaba en modo cámara
    override fun onResume() {
        super.onResume()
        if (isCameraMode && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraView.setCameraPermissionGranted()
            cameraView.enableView()
        }
    }

    // Se ejecuta al salir de la app: apagar la cámara para liberar recursos
    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    // Se ejecuta al cerrar la app: apagar la cámara definitivamente
    override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }

    // Resultado de la solicitud de permisos al usuario
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permiso aceptado: desbloquear cámara y activarla
            cameraView.setCameraPermissionGranted()
            if (isCameraMode) {
                cameraView.enableView()
            }
        }
    }

    // Se llama cuando la cámara comienza a transmitir frames
    override fun onCameraViewStarted(width: Int, height: Int) {
        // Forzar resolución máxima de 1920x1080 para mejor calidad
        (cameraView as? JavaCamera2View)?.setMaxFrameSize(1920, 1080)
    }

    // Se llama cuando la cámara deja de transmitir
    override fun onCameraViewStopped() {}

    // Se llama por cada frame que llega de la cámara en tiempo real
    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        // Obtener el frame actual en formato RGBA
        val rgba = inputFrame.rgba()

        // Corregir efecto espejo de la cámara frontal
        if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT) {
            Core.flip(rgba, rgba, 1) // 1 = voltear horizontalmente
        }

        // Enviar el frame a C++ para aplicar el filtro seleccionado
        processImageNative(rgba.nativeObjAddr, currentEffect)

        // Devolver el frame procesado para que OpenCV lo muestre en pantalla
        return rgba
    }

    // Manejador del resultado al seleccionar una imagen de la galería
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri = result.data?.data!!

            // Leer los metadatos EXIF para detectar la rotación original de la foto
            val inputStream = contentResolver.openInputStream(imageUri)
            val exif = ExifInterface(inputStream!!)
            val rotation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            // Convertir el valor EXIF a grados de rotación
            val degrees = when (rotation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f // Sin rotación necesaria
            }
            inputStream.close()

            // Decodificar la imagen desde la URI
            val inputStream2 = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream2)
            inputStream2?.close()

            // Aplicar la rotación correcta si es necesario
            val rotatedBitmap = if (degrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(degrees)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap // Ya está en la orientación correcta
            }

            // Convertir el Bitmap a Mat de OpenCV para poder procesarlo con C++
            val mat = Mat()
            val bmp32 = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
            Utils.bitmapToMat(bmp32, mat)
            currentGalleryImage = mat

            // Aplicar el efecto actual a la imagen cargada
            applyEffectToGalleryImage()
        }
    }

    // Abre el selector de imágenes del sistema
    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    // Aplica el efecto actual a la imagen de la galería y la muestra en pantalla
    private fun applyEffectToGalleryImage() {
        currentGalleryImage?.let { originalMat ->
            // Clonar la imagen original para no modificarla permanentemente
            val matCopy = originalMat.clone()

            // Aplicar el filtro seleccionado mediante C++
            processImageNative(matCopy.nativeObjAddr, currentEffect)

            // Convertir el resultado de Mat a Bitmap para mostrarlo en el ImageView
            val resultBitmap = Bitmap.createBitmap(matCopy.cols(), matCopy.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(matCopy, resultBitmap)

            // Actualizar la interfaz en el hilo principal
            runOnUiThread {
                galleryView.scaleType = ImageView.ScaleType.CENTER_CROP
                galleryView.setImageBitmap(resultBitmap)
            }
        }
    }
}
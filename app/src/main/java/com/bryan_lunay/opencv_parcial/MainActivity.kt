package com.bryan_lunay.opencv_parcial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: CameraBridgeViewBase
    private lateinit var galleryView: ImageView

    private var isCameraMode = true
    private var currentEffect = 0 // 0: Normal, 1: Sketch, 2: Sepia
    private var currentGalleryImage: Mat? = null

    // Variable para controlar qué cámara usamos (Trasera por defecto)
    private var cameraIndex = CameraBridgeViewBase.CAMERA_ID_BACK

    private external fun processImageNative(matAddrRgba: Long, effectMode: Int)

    companion object {
        init {
            System.loadLibrary("opencv_parcial")
            OpenCVLoader.initDebug()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.camera_view)
        galleryView = findViewById(R.id.gallery_view)

        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)
        cameraView.setCameraIndex(cameraIndex) // Configurar cámara trasera al inicio

        // Verificar permisos y desbloquear la vista de OpenCV
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            // ¡VITAL PARA OPENCV 4! Desbloquea la cámara internamente quitando la pantalla negra
            cameraView.setCameraPermissionGranted()
        }

        // Botón: Cambiar entre Cámara y Galería
        findViewById<Button>(R.id.btn_mode).setOnClickListener {
            isCameraMode = !isCameraMode
            if (isCameraMode) {
                galleryView.visibility = View.GONE
                cameraView.visibility = View.VISIBLE
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraView.setCameraPermissionGranted()
                    cameraView.enableView()
                }
            } else {
                cameraView.disableView()
                cameraView.visibility = View.GONE
                galleryView.visibility = View.VISIBLE
                openGallery()
            }
        }

        // Botón para cambiar cámara (Trasera / Delantera)
        findViewById<Button>(R.id.btn_switch_cam).setOnClickListener {
            if (isCameraMode) {
                cameraView.disableView() // Apagar cámara actual

                // Cambiar el índice
                cameraIndex = if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK) {
                    CameraBridgeViewBase.CAMERA_ID_FRONT
                } else {
                    CameraBridgeViewBase.CAMERA_ID_BACK
                }

                cameraView.setCameraIndex(cameraIndex)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraView.setCameraPermissionGranted()
                    cameraView.enableView() // Encender nueva cámara
                }
            }
        }

        // Botón: Cambiar Efecto
        findViewById<Button>(R.id.btn_effect).setOnClickListener {
            currentEffect = (currentEffect + 1) % 3
            if (!isCameraMode && currentGalleryImage != null) {
                applyEffectToGalleryImage()
            }
        }
    }

    // --- MANEJO DEL CICLO DE VIDA (ARREGLA LA PANTALLA NEGRA AL CAMBIAR DE APP) ---
    override fun onResume() {
        super.onResume()
        if (isCameraMode && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraView.setCameraPermissionGranted() // Desbloquear cámara
            cameraView.enableView() // Encender cámara
        }
    }

    override fun onPause() {
        super.onPause()
        cameraView.disableView()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView.disableView()
    }

    // --- MANEJO DE PERMISOS (AL ACEPTAR POR PRIMERA VEZ) ---
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraView.setCameraPermissionGranted() // Permiso concedido, desbloquear cámara
            if (isCameraMode) {
                cameraView.enableView()
            }
        }
    }

    // --- LÓGICA DE LA CÁMARA (TIEMPO REAL) ---
    override fun onCameraViewStarted(width: Int, height: Int) {}
    override fun onCameraViewStopped() {}

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgba = inputFrame.rgba()

        // OpenCV por defecto invierte la cámara frontal (efecto espejo). Esto lo arregla:
        if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT) {
            Core.flip(rgba, rgba, 1) // 1 significa voltear horizontalmente
        }

        // Pasamos la matriz a C++ para aplicar el filtro
        processImageNative(rgba.nativeObjAddr, currentEffect)

        return rgba
    }

    // --- LÓGICA DE LA GALERÍA ---
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri = result.data?.data
            val inputStream = contentResolver.openInputStream(imageUri!!)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            val mat = Mat()
            val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            Utils.bitmapToMat(bmp32, mat)
            currentGalleryImage = mat

            applyEffectToGalleryImage()
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun applyEffectToGalleryImage() {
        currentGalleryImage?.let { originalMat ->
            val matCopy = originalMat.clone()
            processImageNative(matCopy.nativeObjAddr, currentEffect)

            val resultBitmap = Bitmap.createBitmap(matCopy.cols(), matCopy.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(matCopy, resultBitmap)

            // Para asegurar que se actualice la vista en el hilo principal
            runOnUiThread {
                galleryView.setImageBitmap(resultBitmap)
            }
        }
    }
}
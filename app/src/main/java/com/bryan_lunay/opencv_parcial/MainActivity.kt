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

class MainActivity : AppCompatActivity(), CameraBridgeViewBase.CvCameraViewListener2 {

    private lateinit var cameraView: CameraBridgeViewBase
    private lateinit var galleryView: ImageView
    private lateinit var btnLayout: LinearLayout

    private var isCameraMode = true
    private var currentEffect = 0
    private var currentGalleryImage: Mat? = null
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

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

        cameraView = findViewById(R.id.camera_view)
        galleryView = findViewById(R.id.gallery_view)
        btnLayout = findViewById(R.id.btn_layout)

        cameraView.visibility = SurfaceView.VISIBLE
        cameraView.setCvCameraViewListener(this)
        cameraView.setCameraIndex(cameraIndex)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        } else {
            cameraView.setCameraPermissionGranted()
        }

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

        findViewById<Button>(R.id.btn_switch_cam).setOnClickListener {
            if (isCameraMode) {
                cameraView.disableView()
                cameraIndex = if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK) {
                    CameraBridgeViewBase.CAMERA_ID_FRONT
                } else {
                    CameraBridgeViewBase.CAMERA_ID_BACK
                }
                cameraView.setCameraIndex(cameraIndex)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraView.setCameraPermissionGranted()
                    cameraView.enableView()
                }
            }
        }

        findViewById<Button>(R.id.btn_effect).setOnClickListener {
            currentEffect = (currentEffect + 1) % 3
            if (!isCameraMode && currentGalleryImage != null) {
                applyEffectToGalleryImage()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isCameraMode && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraView.setCameraPermissionGranted()
            cameraView.enableView()
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraView.setCameraPermissionGranted()
            if (isCameraMode) {
                cameraView.enableView()
            }
        }
    }

    override fun onCameraViewStarted(width: Int, height: Int) {
        (cameraView as? JavaCamera2View)?.setMaxFrameSize(1920, 1080)
    }

    override fun onCameraViewStopped() {}

    override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat {
        val rgba = inputFrame.rgba()
        if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_FRONT) {
            Core.flip(rgba, rgba, 1)
        }
        processImageNative(rgba.nativeObjAddr, currentEffect)
        return rgba
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val imageUri = result.data?.data!!

            // Leer rotación EXIF
            val inputStream = contentResolver.openInputStream(imageUri)
            val exif = ExifInterface(inputStream!!)
            val rotation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (rotation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            inputStream.close()

            // Decodificar y rotar
            val inputStream2 = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream2)
            inputStream2?.close()

            val rotatedBitmap = if (degrees != 0f) {
                val matrix = Matrix()
                matrix.postRotate(degrees)
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            val mat = Mat()
            val bmp32 = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
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
            runOnUiThread {
                galleryView.scaleType = ImageView.ScaleType.CENTER_CROP
                galleryView.setImageBitmap(resultBitmap)
            }
        }
    }
}
package com.liux.cameraxdemo

import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor


/**
 * @author liux
 * @Date    2020/9/1  11:53 AM
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {

    companion object {
        lateinit var cameraExecutor: Executor
        private var isShowPic = false

                private const val cameraId = CameraSelector.LENS_FACING_BACK
//        private const val cameraId = CameraSelector.LENS_FACING_FRONT

        lateinit var imageCapture: ImageCapture
        lateinit var outputDirectory: File
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = ContextCompat.getMainExecutor(this)

        pv_main.post {
            openCameraX()
        }

        btn_show_pic.setOnClickListener { isShowPic = true }

        outputDirectory = getOutputDirectory()

        btn_save_pic.setOnClickListener { takePhoto() }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private fun openCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        val preview = Preview.Builder().apply { setTargetAspectRatio(AspectRatio.RATIO_4_3) }.build()

        imageCapture = ImageCapture.Builder()
                .build()

        // 对每一帧的图片进行处理
        val imageAnalysis = ImageAnalysis.Builder().apply {
            setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setTargetAspectRatio(AspectRatio.RATIO_4_3)
        }.build().also { it ->
            it.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer { imageplanes ->
                val planes = imageplanes.planes

                if (isShowPic) {
                    val nv21 = toFaceByte(planes)
                    var bitmap = toFaceBitmap(nv21, imageplanes.width, imageplanes.height)
                    bitmap = convert(bitmap)
                    if (!bitmap.isRecycled) {
                        img_pic.setImageBitmap(bitmap)
                    }
                    isShowPic = false
                }

                imageplanes.close()
            })
        }

        val cameraSelestor = CameraSelector.Builder().requireLensFacing(cameraId).build()

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()

            val camera = cameraProvider.bindToLifecycle(this@MainActivity as LifecycleOwner, cameraSelestor, preview, imageAnalysis, imageCapture)
            preview.setSurfaceProvider(pv_main.createSurfaceProvider())
        }, cameraExecutor)
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.CHINA
                ).format(System.currentTimeMillis()) + ".jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                LogUtils.e("Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"
                //显示照片路径
                ToastUtils.showShort(msg)
                LogUtils.d(msg)
            }
        })
    }

    // planes转byte[]
    private fun toFaceByte(planes: Array<ImageProxy.PlaneProxy>): ByteArray {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        return nv21
    }

    // nv21转bitmap
    private fun toFaceBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        //输出流
        val out = ByteArrayOutputStream()
        //压缩写入out
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
        //转数组
        val imageBytes: ByteArray = out.toByteArray()
        //生成bitmap
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // 旋转图片
    private fun convert(a: Bitmap): Bitmap {
        val m = Matrix()
        when (cameraId) {
            CameraSelector.LENS_FACING_BACK -> {
                m.postRotate((90).toFloat());  //旋转90度
            }
            CameraSelector.LENS_FACING_FRONT -> {
                m.setScale((-1).toFloat(), 1F) //水平翻转
                m.setScale(1F, (-1).toFloat()) //垂直翻转
                m.postRotate((-90).toFloat());  //旋转-90度
            }
        }

        val w: Int = a.width
        val h: Int = a.height
        //生成的翻转后的bitmap
        return Bitmap.createBitmap(a, 0, 0, w, h, m, true)
    }
}
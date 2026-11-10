package com.mustakimzx.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.mustakimzx.scanner.databinding.ActivityQrScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Continuous QR + barcode scanning.
 *
 * Every detected code is sent over Bluetooth immediately; a short cooldown
 * stops the same code being sent again while it stays in front of the camera.
 */
class QrScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScanBinding
    private lateinit var prefs: Prefs
    private val feedback by lazy { Feedback(this) }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var analyzer: ImageAnalysis? = null
    private var scanner: BarcodeScanner? = null
    private lateinit var analysisExecutor: ExecutorService
    private var torchOn = false

    private var lastValue: String? = null
    private var lastSentAt = 0L

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Prefs(this)
        BtClient.init(this)
        analysisExecutor = Executors.newSingleThreadExecutor()
        // Default options scan every format ML Kit supports (QR, Code 39/93/128,
        // ITF, Codabar, EAN-8/13, UPC-A/E, Data Matrix, PDF417, Aztec).
        scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().build()
        )

        binding.btnClose.setOnClickListener { finish() }
        binding.btnFlash.setOnClickListener { toggleTorch() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                bindUseCases(provider)
            } catch (e: Exception) {
                toast(e.message ?: "Camera error")
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindUseCases(provider: ProcessCameraProvider) {
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.preview.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(analysisExecutor, ::analyze) }
        analyzer = analysis

        val selector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        provider.unbindAll()
        try {
            camera = provider.bindToLifecycle(this, selector, preview, analysis)
            torchOn = false
            binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
            binding.btnFlash.visibility =
                if (camera?.cameraInfo?.hasFlashUnit() == true) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            toast(e.message ?: "Camera error")
            finish()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val client = scanner
        if (mediaImage == null || client == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        // Success listener stays on the main thread so the UI chip can be updated;
        // the socket write itself is dispatched to BtClient's IO scope.
        client.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { it.rawValue ?: it.displayValue }
                if (!value.isNullOrBlank()) onCodeDetected(value)
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onCodeDetected(value: String) {
        val now = System.currentTimeMillis()
        if (value == lastValue && now - lastSentAt < prefs.duplicateWindowMs) return

        lastValue = value
        lastSentAt = now

        BtClient.sendAsync(value) { delivered ->
            ScanLog.add(value, delivered)
            if (prefs.feedbackEnabled) {
                if (delivered) feedback.success() else feedback.error()
            }
            binding.scanPrompt.text = getString(
                if (delivered) R.string.sent_toast else R.string.send_failed_toast
            )
        }
    }

    private fun toggleTorch() {
        val control = camera?.cameraControl ?: return
        if (camera?.cameraInfo?.hasFlashUnit() != true) return
        torchOn = !torchOn
        control.enableTorch(torchOn)
        binding.btnFlash.setImageResource(
            if (torchOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
        binding.btnFlash.contentDescription =
            getString(if (torchOn) R.string.flash_on else R.string.flash_off)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { analyzer?.clearAnalyzer() }
        runCatching { scanner?.close() }
        runCatching { analysisExecutor.shutdown() }
        feedback.release()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

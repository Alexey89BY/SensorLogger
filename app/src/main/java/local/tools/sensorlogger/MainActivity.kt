package local.tools.sensorlogger

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.snackbar.Snackbar
import local.tools.sensorlogger.databinding.ActivityMainBinding
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerSensor: Sensor
    private lateinit var gyroscopeSensor: Sensor
    private lateinit var accelerometerListener: MEMSSensorListener
    private lateinit var gyroscopeListener: MEMSSensorListener
    private lateinit var sensorTimer: Timer
    private var isLoggerStarted = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonStart.setOnClickListener {
            loggerStart()
        }

        binding.buttonStop.setOnClickListener {
            loggerStop()
        }

        binding.buttonClear.setOnClickListener {
            loggerClear()
        }

        binding.buttonSave.setOnClickListener {
            loggerSave()
        }

        binding.buttonAnalyze.setOnClickListener {
            loggerAnalyze()
        }

        binding.buttonSetZero.setOnClickListener {
            loggerCalibrate()
        }

        binding.buttonClearZero.setOnClickListener {
            loggerResetCalibration()
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)!!
        accelerometerListener = MEMSSensorListener("Accelerometer")
        gyroscopeListener = MEMSSensorListener("Gyroscope")

        val spinnerItems = listOf(
            "Both sensors (accelerometer & gyroscope)",
            "Only " + accelerometerSensor.name,
            "Only " + gyroscopeSensor.name,
        )

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSensor.adapter = spinnerAdapter

        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS)
                == PackageManager.PERMISSION_GRANTED
        ) {
            binding.buttonStart.isEnabled = true
        }

        updateInfo()
        updateZeroInfo()
        updateAnalyzeInfo()
    }


    private fun loggerStop() {
        if (! isLoggerStarted)
            return

        sensorManager.unregisterListener(accelerometerListener)
        sensorManager.unregisterListener(gyroscopeListener)
        sensorTimer.cancel()

        binding.buttonStart.isEnabled = true
        binding.buttonStop.isEnabled = false
        binding.buttonSave.isEnabled = true
        binding.buttonAnalyze.isEnabled = true
        binding.spinnerSensor.isEnabled = true

        updateInfo()

        isLoggerStarted = false
    }


    private fun loggerStart() {
        if (isLoggerStarted)
            return

        when (binding.spinnerSensor.selectedItemPosition) {
            0 -> {
                sensorManager.registerListener(accelerometerListener, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST)
                sensorManager.registerListener(gyroscopeListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST)
            }
            1 -> {
                sensorManager.registerListener(accelerometerListener, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST)
            }
            2 -> {
                sensorManager.registerListener(gyroscopeListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST)
            }
            else -> return
        }

        sensorTimer = Timer()
        sensorTimer.schedule(SensorTask(this), 0, timerPeriod)

        binding.buttonStart.isEnabled = false
        binding.buttonStop.isEnabled = true
        binding.buttonSave.isEnabled = false
        binding.buttonAnalyze.isEnabled = false
        binding.spinnerSensor.isEnabled = false

        updateInfo()

        isLoggerStarted = true
    }


    private fun loggerClear() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("")
            .setMessage("Clear?")
            .setPositiveButton("Clear") { _, _ ->
                accelerometerListener.clearSamples()
                gyroscopeListener.clearSamples()
                updateInfo()
            }
            .setNeutralButton("Cancel") {_, _ ->
            }
            .create()
        dialog.show()
    }


    private fun loggerSave() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("")
            .setMessage("Save?")
            .setPositiveButton("Save") { _, _ ->
                val fileName = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss_SSS").format(LocalDateTime.now())

                Snackbar.make(binding.root, fileName, 1000).show()

                val filesDir = this.getExternalFilesDir(null)

                val fileAccel = File(filesDir, fileName + "_accel.csv")
                accelerometerListener.saveToFile(fileAccel)

                val fileGyro = File(filesDir, fileName + "_gyro.csv")
                gyroscopeListener.saveToFile(fileGyro)
            }
            .setNeutralButton("Cancel") {_, _ ->
            }
            .create()
        dialog.show()
    }


    override fun onPause() {
        super.onPause()
        loggerStop()
    }


    //override fun onResume() {
    //    super.onResume()
    //}


    private fun updateInfo() {
        binding.textInfo.text = buildString {
            append(accelerometerListener.getInfo())
            append(gyroscopeListener.getInfo())
        }
    }

    private fun updateInfoFromTimer() {
        if (accelerometerListener.isOverflowed() || gyroscopeListener.isOverflowed()) {
            loggerStop()
        } else {
            updateInfo()
        }
    }


    private fun loggerAnalyze() {
        accelerometerListener.calculateMean()
        accelerometerListener.calculateDeviation()

        gyroscopeListener.calculateMean()
        gyroscopeListener.calculateDeviation()

        updateAnalyzeInfo()
    }

    private fun updateAnalyzeInfo() {
        binding.textAnalyze.text = buildString {
            append(accelerometerListener.getAnalyzeInfo())
            append(gyroscopeListener.getAnalyzeInfo())
        }
    }


    private fun updateZeroInfo() {
        binding.textZero.text = buildString {
            append(accelerometerListener.getZeroInfo())
            append(gyroscopeListener.getZeroInfo())
        }
    }


    private fun loggerCalibrate() {
        val meanAccel = accelerometerListener.getMean()
        val deviationAccel = accelerometerListener.getDeviation()
        accelerometerListener.setZero(meanAccel, deviationAccel)

        val meanGyro = gyroscopeListener.getMean()
        val deviationGyro = gyroscopeListener.getDeviation()
        gyroscopeListener.setZero(meanGyro, deviationGyro)

        updateZeroInfo()
    }

    private fun loggerResetCalibration() {
        val zero = MEMSSensorListener.Vector3D()
        accelerometerListener.setZero(zero, zero)
        gyroscopeListener.setZero(zero, zero)

        updateZeroInfo()
    }



    companion object {
        private const val timerPeriod: Long = 419
    }


    class SensorTask(private val activity: MainActivity) : TimerTask() {
        override fun run() {
            activity.runOnUiThread {
                activity.updateInfoFromTimer()
            }
        }
    }
}
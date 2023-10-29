package local.tools.sensorlogger

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import java.io.File
import java.util.Locale
import kotlin.math.sqrt


class MemsSensorListener(private val sensorName: String): SensorEventListener {

    data class Sample(
        val timestamp: Long = 0,
        val x: Float = 0.0F,
        val y: Float = 0.0F,
        val z: Float = 0.0F,
    )

    private val samplesLimit = 1000 * 120
    private var samplesCount: Int = samplesLimit
    private var sampleIndex: Int = 0
    private var samplesZero: Sample = Sample()
    private var samplesMean: Sample = Sample()
    private var samplesDeviation: Sample = Sample()
    private val samplesStorage = Array(samplesLimit) { Sample() }

    override fun onSensorChanged(p0: SensorEvent?) {
        val event: SensorEvent = p0!!
        // event.sensor.type == Sensor.TYPE_ACCELEROMETER

        if (sampleIndex >= samplesCount)
            return

        samplesStorage[sampleIndex] = Sample(
            timestamp = event.timestamp,
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
        )

        sampleIndex++
    }


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }


    fun getCount(): Int {
        return sampleIndex
    }


    fun clearSamples() {
        sampleIndex = 0
    }


    fun saveToFile(file: File) {
        file.createNewFile()

        val lines = buildString {
            append("ts,x,y,z\n")
            for (index: Int in 0 until sampleIndex) {
                val sample = samplesStorage[index]
                val line = String.format(Locale.US, "%d,%g,%g,%g\n",
                    sample.timestamp,
                    sample.x,
                    sample.y,
                    sample.z)
                append(line)
            }
        }

        file.appendText(lines)
    }


    fun getTimeStamp(): Float {
        val sample1 = getLastSample()
        val sample0 = samplesStorage[0]
        return (sample1.timestamp - sample0.timestamp) * 1.0e-9F
    }


    fun calculateMean() {
        if (sampleIndex > 0) {
            var mX = 0.0
            var mY = 0.0
            var mZ = 0.0

            for (index: Int in 0 until sampleIndex) {
                val sample = samplesStorage[index]
                mX += sample.x
                mY += sample.y
                mZ += sample.z
            }

            samplesMean = Sample(
                timestamp = 0,
                x = (mX / sampleIndex - samplesZero.x).toFloat(),
                y = (mY / sampleIndex - samplesZero.y).toFloat(),
                z = (mZ / sampleIndex - samplesZero.z).toFloat(),
            )
        } else {
            samplesMean = Sample()
        }
    }

    fun calculateDeviation() {
        if (sampleIndex > 0)
        {
            val zeroX = samplesZero.x + samplesMean.x
            val zeroY = samplesZero.y + samplesMean.y
            val zeroZ = samplesZero.z + samplesMean.z

            var mqX = 0.0
            var mqY = 0.0
            var mqZ = 0.0

            for (index: Int in 0 until sampleIndex) {
                val sample = samplesStorage[index]
                val dx = sample.x - zeroX
                mqX += dx * dx
                val dy = sample.y - zeroY
                mqY += dy * dy
                val dz = sample.z - zeroZ
                mqZ += dz * dz
            }

            samplesDeviation = Sample(
                timestamp = 0,
                x = (mqX / sampleIndex).toFloat(),
                y = (mqY / sampleIndex).toFloat(),
                z = (mqZ / sampleIndex).toFloat(),
            )
        } else {
            samplesDeviation = Sample()
        }
    }


    fun isOverflowed(): Boolean {
        return sampleIndex >= samplesLimit
    }

    fun getMean(): Sample {
        return samplesMean
    }

    fun setZero(zero: Sample) {
        samplesZero = zero
    }

    fun getDeviation(): Sample {
        return samplesDeviation
    }

    fun getZero(): Sample {
        return samplesZero
    }

    fun getLastSample(): Sample {
        return samplesStorage[if (sampleIndex == 0) 0 else sampleIndex-1]
    }

    fun getZeroInfo(): String {
        return String.format(
            Locale.US, "\n*** %s ***\nZero: %f, %f, %f @ %f\n",
            sensorName,
            samplesZero.x,
            samplesZero.y,
            samplesZero.z,
            length(samplesZero),
        )
    }

    fun getAnalyzeInfo(): String {
        return String.format(
            Locale.US, "\n*** %s ***\nCount: %d\nMean: %f, %f, %f @ %f\nDeviation: %g, %g, %g @ %g\n",
            sensorName,
            getCount(),
            samplesMean.x,
            samplesMean.y,
            samplesMean.z,
            length(samplesMean),
            samplesDeviation.x,
            samplesDeviation.y,
            samplesDeviation.z,
            length(samplesDeviation),
        )
    }

    fun getInfo(): String {
        val time = getTimeStamp()
        val count = getCount()
        val sample = getLastSample()
        return String.format("\n*** %s ***\nTime: %.3f\nSamples: %d @ %.1f Hz\nLast: %f, %f, %f @ %f\n",
            sensorName,
            getTimeStamp(),
            count,
            count / time,
            sample.x,
            sample.y,
            sample.z,
            length(sample),
        )
    }

    companion object {
        fun length(sample: Sample): Double {
            return sqrt(0.0 + sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        }
    }
}
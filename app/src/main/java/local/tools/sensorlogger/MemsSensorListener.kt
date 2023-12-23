package local.tools.sensorlogger

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import java.io.File
import java.util.Locale
import kotlin.math.sqrt


class MEMSSensorListener(private val sensorName: String): SensorEventListener {

    data class Sample(
        val timestamp: Long = 0,
        val x: Float = 0.0F,
        val y: Float = 0.0F,
        val z: Float = 0.0F,
    )

    data class Vector3D(
        val x: Double = 0.0,
        val y: Double = 0.0,
        val z: Double = 0.0,
    )

    private val samplesLimit = 1000 * 120
    private var samplesCount: Int = samplesLimit
    private var sampleIndex: Int = 0
    private var samplesZeroMean: Vector3D = Vector3D()
    private var samplesZeroDeviation: Vector3D = Vector3D()
    private var samplesMean: Vector3D = Vector3D()
    private var samplesDeviation: Vector3D = Vector3D()
    private var samplesMeanWithZero: Vector3D = Vector3D()
    private var samplesDeviationWithZero: Vector3D = Vector3D()
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

        ++sampleIndex
    }


    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {

    }


    private fun getCount(): Int {
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


    private fun getTimeStamp(): Float {
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

            samplesMean = Vector3D(
                x = mX / sampleIndex,
                y = mY / sampleIndex,
                z = mZ / sampleIndex,
            )
        } else {
            samplesMean = Vector3D()
        }

        samplesMeanWithZero = Vector3D(
            x = samplesMean.x - samplesZeroMean.x,
            y = samplesMean.y - samplesZeroMean.y,
            z = samplesMean.z - samplesZeroMean.z,
        )
    }

    fun calculateDeviation() {
        if (sampleIndex > 1) {
            var mqX = 0.0
            var mqY = 0.0
            var mqZ = 0.0

            for (index: Int in 0 until sampleIndex) {
                val sample = samplesStorage[index]
                val dx = sample.x - samplesMean.x
                mqX += dx * dx
                val dy = sample.y - samplesMean.y
                mqY += dy * dy
                val dz = sample.z - samplesMean.z
                mqZ += dz * dz
            }

            samplesDeviation = Vector3D(
                x = sqrt(mqX / (sampleIndex - 1)),
                y = sqrt(mqY / (sampleIndex - 1)),
                z = sqrt(mqZ / (sampleIndex - 1)),
            )
        } else {
            samplesDeviation = Vector3D()
        }

        samplesDeviationWithZero = Vector3D(
            x = sqrtSigned(sqr(samplesDeviation.x) - sqr(samplesZeroDeviation.x)),
            y = sqrtSigned(sqr(samplesDeviation.y) - sqr(samplesZeroDeviation.y)),
            z = sqrtSigned(sqr(samplesDeviation.z) - sqr(samplesZeroDeviation.z)),
        )
    }


    fun isOverflowed(): Boolean {
        return sampleIndex >= samplesLimit
    }

    fun getMean(): Vector3D {
        return samplesMean
    }

    fun setZero(
        zeroMean: Vector3D,
        zeroDeviation: Vector3D,
    ) {
        samplesZeroMean = zeroMean
        samplesZeroDeviation = zeroDeviation
    }

    fun getDeviation(): Vector3D {
        return samplesDeviation
    }


    private fun getLastSample(): Sample {
        return samplesStorage[if (sampleIndex == 0) 0 else sampleIndex-1]
    }

    fun getZeroInfo(): String {
        return String.format(
            Locale.US, "\n*** %s ***\nZero mean: %f, %f, %f @ %f\nZero st.dev: %f, %f, %f @ %f\n",
            sensorName,
            samplesZeroMean.x,
            samplesZeroMean.y,
            samplesZeroMean.z,
            length(samplesZeroMean),
            samplesZeroDeviation.x,
            samplesZeroDeviation.y,
            samplesZeroDeviation.z,
            length(samplesZeroDeviation),
        )
    }

    fun getAnalyzeInfo(): String {
        return String.format(
            Locale.US, "\n*** %s ***\n" +
                    "Mean: %f, %f, %f @ %f\nSt.dev: %g, %g, %g @ %g\n" +
                    "Mean (*): %f, %f, %f @ %f\nSt.dev (*): %g, %g, %g @ %g\n",
            sensorName,
            samplesMean.x,
            samplesMean.y,
            samplesMean.z,
            length(samplesMean),
            samplesDeviation.x,
            samplesDeviation.y,
            samplesDeviation.z,
            length(samplesDeviation),
            samplesMeanWithZero.x,
            samplesMeanWithZero.y,
            samplesMeanWithZero.z,
            length(samplesMeanWithZero),
            samplesDeviationWithZero.x,
            samplesDeviationWithZero.y,
            samplesDeviationWithZero.z,
            length(samplesDeviationWithZero),
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

        fun length(sample: Vector3D): Double {
            return sqrt(sqr(sample.x) + sqr(sample.y) + sqr(sample.z))
        }

        fun sqr(x: Double) :Double {
            return x * x
        }

        fun sqrtSigned(x: Double) :Double {
            return if (x < 0) -sqrt(-x) else sqrt(x)
        }
    }
}
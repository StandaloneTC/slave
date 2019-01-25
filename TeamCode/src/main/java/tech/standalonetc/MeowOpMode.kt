package tech.standalonetc

import android.widget.Toast
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import com.qualcomm.robotcore.hardware.*
import com.qualcomm.robotcore.util.RobotLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import tech.standalonetc.protocol.RobotPacket
import tech.standalonetc.protocol.network.NetworkTools
import tech.standalonetc.protocol.network.PacketCallback
import tech.standalonetc.protocol.packet.Packet
import tech.standalonetc.protocol.packet.encode
import java.util.*

@TeleOp
class MeowOpMode : OpMode() {

    private lateinit var networkTools: NetworkTools

    //Effectors
    private lateinit var motors: MutableMap<Byte, DcMotorEx>
    private lateinit var servos: MutableMap<Byte, Servo>
    private lateinit var continuousServos: MutableMap<Byte, CRServo>

    //Sensors
    private val encoders get() = motors
    private lateinit var colors: MutableMap<Byte, ColorSensor>
    private lateinit var touches: MutableMap<Byte, TouchSensor>

    private lateinit var displayMessages: MutableList<Pair<String, String>>

    private val deviceDescriptionCallback: Packet<*>.() -> ByteArray? = {
        AppUtil.getInstance().runOnUiThread {
            Toast.makeText(AppUtil.getInstance().rootActivity, this.toString(), Toast.LENGTH_SHORT).show()
        }
        if (this is RobotPacket.DeviceDescriptionPacket)
            runCatching {
                hardwareMap[deviceName]
            }.also { result ->
                result.onSuccess {
                    when (it) {
                        is DcMotorEx   -> motors[deviceId] = it
                        is Servo       -> servos[deviceId] = it
                        is CRServo     -> continuousServos[deviceId] = it
                        is ColorSensor -> colors[deviceId] = it
                        is TouchSensor -> touches[deviceId] = it
                    }
                }
                result.onFailure {
                    RobotLog.e(javaClass.name, it, "Unable to find device")
                }
            }.isSuccess.encode()
        else false.encode()
    }

    private val workingCallback: PacketCallback = {
        val packet = this
        AppUtil.getInstance().runOnUiThread {
            Toast.makeText(AppUtil.getInstance().rootActivity, this.toString(), Toast.LENGTH_SHORT).show()
        }
        GlobalScope.launch {
            with(packet) {
                when (this) {
                    is RobotPacket.MotorPowerPacket           -> motors[id]?.power = power
                    is RobotPacket.ServoPositionPacket        -> servos[id]?.position = degree
                    is RobotPacket.PwmEnablePacket            -> servos[id] ?: motors[id]?.let {
                        (it.controller as PWMOutputControllerEx).run {
                            if (enable)
                                setPwmEnable(it.portNumber)
                            else setPwmDisable(it.portNumber)
                        }
                    }
                    is RobotPacket.ContinuousServoPowerPacket -> continuousServos[id]?.power = power
                    is RobotPacket.ColorSensorLedPacket       -> colors[id]?.enableLed(data)

                    is RobotPacket.TelemetryDataPacket        -> displayMessages.add(caption to string)
                    is RobotPacket.TelemetryClearPacket       -> displayMessages.clear()

                    //TODO Gyro

                    else                                      -> {
                    }
                }
            }
        }
    }

    private lateinit var tasksQueue: Queue<DataOutputTask>

    private var last = 0L

    override fun init() {
        motors = mutableMapOf()
        servos = mutableMapOf()
        continuousServos = mutableMapOf()
        colors = mutableMapOf()
        touches = mutableMapOf()
        displayMessages = Collections.synchronizedList(mutableListOf())

        networkTools = NetworkTools("Robot", "Host", 2, 2, onPacketReceive = workingCallback)
        networkTools.debug = true
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.INIT))
        networkTools.setPacketConversion(RobotPacket.Conversion)
        networkTools.setTcpPacketReceiveCallback(deviceDescriptionCallback)
    }

    override fun init_loop() {
        telemetry.addLine().addData("Standalone", "Starting to listen devices...")
    }

    override fun start() {
        telemetry.clear()

        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.START))
        networkTools.setTcpPacketReceiveCallback { workingCallback(this);true.encode() }

        motors.forEach { (_, motor) -> motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER }

        RobotLog.i("found devices: motor[${motors.map { "(${it.key},${it.value.deviceName})" }.joinToString()}]")
        RobotLog.i("found devices: servo[${servos.map { "(${it.key},${it.value.deviceName})" }.joinToString()}]")
        RobotLog.i("found devices: continuousServos[${continuousServos.map { "(${it.key},${it.value.deviceName})" }.joinToString()}]")

        tasksQueue = LinkedList()

        encoders.map { (id, device) -> EncoderDataOutputTask(id, device) }.let(tasksQueue::addAll)
        colors.map { (id, device) -> ColorSensorOutputTask(id, device) }.let(tasksQueue::addAll)
        touches.map { (id, device) -> TouchSensorOutputTask(id, device) }.let(tasksQueue::addAll)
        tasksQueue.add(GamepadDataOutputTask(true, gamepad1))
        tasksQueue.add(GamepadDataOutputTask(false, gamepad2))
        tasksQueue.add(VoltageDataOutputTask(hardwareMap.voltageSensor.toList()))
    }

    override fun loop() {
        last = System.currentTimeMillis()
        telemetry.addLine().addData("Standalone Motor", motors.toString())
        telemetry.addLine().addData("Standalone Servo", servos.toString())
        telemetry.addLine().addData("Standalone CRServo", continuousServos.toString())
        displayMessages.forEach { (caption, string) ->
            telemetry.addLine().addData(caption, string)
        }
        tasksQueue.forEach { it.run(networkTools) }
        networkTools.broadcastPacket(RobotPacket.OperationPeriodPacket((System.currentTimeMillis()-last).toInt()))
    }

    override fun stop() {
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.STOP))
        networkTools.close()
        motors.clear()
        servos.clear()
        continuousServos.clear()
    }

}
package tech.standalonetc

import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.hardware.*
import com.qualcomm.robotcore.util.RobotLog
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.standalonetc.protocol.RobotPacket
import tech.standalonetc.protocol.network.NetworkTools
import tech.standalonetc.protocol.network.PacketCallback
import tech.standalonetc.protocol.packet.Packet
import tech.standalonetc.protocol.packet.encode
import java.util.*


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

    private val displayMessages: MutableList<Pair<String, String>> = Collections.synchronizedList(mutableListOf())

    private val deviceDescriptionCallback: Packet<*>.() -> ByteArray? = {
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
        else null
    }

    private val workingCallback: PacketCallback = {
        val packet = this
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

    override fun init() {
        networkTools = NetworkTools("Robot", "Host", 0, 20)
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.INIT))
        networkTools.setPacketConversion(RobotPacket.Conversion)
        networkTools.setTcpPacketReceiveCallback(deviceDescriptionCallback)
    }

    override fun init_loop() {
        telemetry.addLine().addData("Standalone", "Starting to listen devices...")
    }

    override fun start() {
        networkTools.close()
        networkTools = NetworkTools("Robot", "Host", onPacketReceive = workingCallback)
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.START))
        networkTools.setTcpPacketReceiveCallback { workingCallback(this);true.encode() }
        networkTools.setPacketConversion(RobotPacket.Conversion)
        motors.forEach { (_, motor) -> motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER }

        RobotLog.i("found devices: motor[${motors.map { "(${it.key},${it.value.deviceName})" }.joinToString()}]")
        RobotLog.i("found devices: servo[${servos.map { "(${it.key},${it.value.deviceName})" }.joinToString()}]")
        RobotLog.i("found devices: continuousServos[${continuousServos.map { "(${it.key},${it.value.deviceName})" }.joinToString()}]")

        tasksQueue = LinkedList()

        encoders.map { (id, device) -> EncoderDataOutputTask(id, device) }.let(tasksQueue::addAll)
        colors.map { (id, device) -> ColorSensorOutputTask(id, device) }.let(tasksQueue::addAll)
        touches.map { (id, device) -> TouchSensorOutputTask(id, device) }.let(tasksQueue::addAll)

    }

    override fun loop() {
        displayMessages.forEach { (caption, string) ->
            telemetry.addLine().addData(caption, string)
        }
        tasksQueue.forEach { it.run(networkTools) }
    }

    override fun stop() {
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.STOP))
        networkTools.close()
        motors.clear()
        servos.clear()
        continuousServos.clear()
    }

}
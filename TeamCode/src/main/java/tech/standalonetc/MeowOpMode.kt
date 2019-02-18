package tech.standalonetc

import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.eventloop.opmode.TeleOp
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
import java.util.concurrent.ConcurrentSkipListSet

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

    private lateinit var displayMessages: ConcurrentSkipListSet<Pair<String, String>>

    private val deviceDescriptionCallback: Packet<*>.() -> ByteArray? = {
        if (this is RobotPacket.DeviceDescriptionPacket)
            runCatching {
                deviceName to hardwareMap[deviceName]
            }.also { result ->
                result.onSuccess {
                    it.second.also { device ->
                        when (device) {
                            is DcMotorEx   -> motors[deviceId] = device
                            is Servo       -> servos[deviceId] = device
                            is CRServo     -> continuousServos[deviceId] = device
                            is ColorSensor -> colors[deviceId] = device
                            is TouchSensor -> touches[deviceId] = device
                        }
                    }

                }.onFailure {
                    RobotLog.e(javaClass.name, it, "Unable to find device")
                }
            }.isSuccess.encode()
        else false.encode()
    }

    private val workingCallback: PacketCallback = {
        val packet = this

        RobotLog.d("received packet $this")
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
        displayMessages = ConcurrentSkipListSet { e1, e2 ->
            e1.hashCode().compareTo(e2.hashCode())
        }

        motors = mutableMapOf()
        servos = mutableMapOf()
        continuousServos = mutableMapOf()
        colors = mutableMapOf()
        touches = mutableMapOf()

        networkTools = NetworkTools(
            "Robot",
            "Host",
            2,
            2,
            onPacketReceive = workingCallback/*,
            loggerConfig = {

            }*/)
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.INIT))
        networkTools.setTcpPacketReceiveCallback(deviceDescriptionCallback)
        telemetry.isAutoClear = false
    }

    override fun init_loop() {
        telemetry.addLine().addData("Standalone", "Starting to listen devices...")
    }

    override fun start() {
        telemetry.isAutoClear = true
        telemetry.clear()

        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.START))
        networkTools.setTcpPacketReceiveCallback { workingCallback(this);true.encode() }

        motors.forEach { (_, motor) -> motor.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER }

        RobotLog.i("found devices: motor[${motors.keys.joinToString()}]")
        RobotLog.i("found devices: servo[${servos.keys.joinToString()}]")
        RobotLog.i("found devices: continuousServos[${continuousServos.keys.joinToString()}]")

        tasksQueue = LinkedList()

        encoders.map { (id, device) -> EncoderDataOutputTask(id, device) }.let(tasksQueue::addAll)
        colors.map { (id, device) -> ColorSensorOutputTask(id, device) }.let(tasksQueue::addAll)
        touches.map { (id, device) -> TouchSensorOutputTask(id, device) }.let(tasksQueue::addAll)
        tasksQueue.add(GamepadDataOutputTask(true, gamepad1))
        tasksQueue.add(GamepadDataOutputTask(false, gamepad2))
        tasksQueue.add(VoltageDataOutputTask(hardwareMap.voltageSensor.toList()))

        last = System.currentTimeMillis()
    }

    override fun loop() {
        telemetry.addLine().addData("Standalone Motor", motors.keys.joinToString())
        telemetry.addLine().addData("Standalone Servo", servos.keys.joinToString())
        telemetry.addLine().addData("Standalone CRServo", continuousServos.keys.joinToString())
        displayMessages.forEach {
            telemetry.addLine().addData(it.first, it.second)
        }
        tasksQueue.forEach { it.run(networkTools) }
        networkTools.broadcastPacket(RobotPacket.OperationPeriodPacket((System.currentTimeMillis() - last).toInt()))
        last = System.currentTimeMillis()
    }

    override fun stop() {
        networkTools.broadcastPacket(RobotPacket.OpModeInfoPacket(javaClass.simpleName, RobotPacket.OpModeInfoPacket.STOP))
        networkTools.close()
        motors.clear()
        servos.clear()
        continuousServos.clear()
    }

}
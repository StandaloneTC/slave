package tech.standalonetc.slave

import com.qualcomm.robotcore.eventloop.opmode.Disabled
import com.qualcomm.robotcore.eventloop.opmode.OpMode
import com.qualcomm.robotcore.hardware.*
import com.qualcomm.robotcore.util.RobotLog
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit
import org.mechdancer.dependency.Component
import org.mechdancer.dependency.DynamicScope
import tech.standalonetc.host.breakAllConnections
import tech.standalonetc.host.data.ColorSensorData
import tech.standalonetc.host.data.EncoderData
import tech.standalonetc.host.data.GamepadData
import tech.standalonetc.host.struct.Device
import tech.standalonetc.host.struct.RobotComponent
import tech.standalonetc.host.struct.effector.ContinuousServo
import tech.standalonetc.host.struct.effector.Motor
import tech.standalonetc.host.struct.sensor.Encoder
import tech.standalonetc.host.struct.sensor.gamepad.Gamepad

@Disabled
abstract class ExperimentalOpMode : OpMode() {

    protected val master = Gamepad(0)

    protected val helper = Gamepad(1)

    private val devices = mutableListOf<Device>()

    private val availableDevices = mutableMapOf<Device, HardwareDevice>()

    private val scope = DynamicScope()


    //Effectors
    private val motors: MutableMap<Motor, DcMotorEx> = mutableMapOf()
    private val servos: MutableMap<tech.standalonetc.host.struct.effector.Servo, Servo> = mutableMapOf()
    private val continuousServos: MutableMap<ContinuousServo, CRServo> = mutableMapOf()

    //Sensors
    private val encoders: MutableMap<Encoder, DcMotorEx> = mutableMapOf()
    private val colors: MutableMap<tech.standalonetc.host.struct.sensor.ColorSensor, ColorSensor> = mutableMapOf()
    private val touches: MutableMap<tech.standalonetc.host.struct.sensor.TouchSensor, TouchSensor> = mutableMapOf()


    protected infix fun setup(component: Component) =
        scope.setup(component)

    override fun init() {
        scope setup master
        scope setup helper

        devices.forEach { device ->
            runCatching {
                hardwareMap[device.name]
            }.onSuccess {
                availableDevices[device] = it
                RobotLog.i("Found ${device.name}")
            }.onFailure {
                RobotLog.i("Unable to find ${device.name}")
            }
        }
        availableDevices.forEach { (device, hardwareDevice) ->
            when (hardwareDevice) {
                is DcMotorEx   -> {
                    when (device) {
                        is Motor   -> motors[device] = hardwareDevice
                        is Encoder -> encoders[device] = hardwareDevice
                    }
                }
                is Servo       -> servos[device as tech.standalonetc.host.struct.effector.Servo] = hardwareDevice
                is CRServo     -> continuousServos[device as ContinuousServo] = hardwareDevice
                is ColorSensor -> colors[device as tech.standalonetc.host.struct.sensor.ColorSensor] = hardwareDevice
                is TouchSensor -> touches[device as tech.standalonetc.host.struct.sensor.TouchSensor] = hardwareDevice
            }
        }
    }

    override fun start() {
        availableDevices.forEach { device, _ ->
            scope setup device
        }

        scope.components.mapNotNull { it as? RobotComponent }.forEach(RobotComponent::init)
        initTask()

        motors.forEach { (device, hardwareDevice) ->
            device.power linkWithTransform {
                hardwareDevice.power = it
            }
        }
        servos.forEach { (device, hardwareDevice) ->
            device.pwmEnable.close()
            device.position linkWithTransform {
                hardwareDevice.position = it
            }
        }
        continuousServos.forEach { (device, hardwareDevice) ->
            device.pwmEnable.close()
            device.power linkWithTransform {
                hardwareDevice.power = it
            }
        }

    }

    override fun loop() {

        with(gamepad1) {
            master.update(GamepadData(
                left_bumper,
                right_bumper,
                a,
                b,
                x,
                y,
                dpad_up,
                dpad_down,
                dpad_left,
                dpad_right,
                left_stick_x.toDouble(),
                left_stick_y.toDouble(),
                left_stick_button,
                right_stick_x.toDouble(),
                right_stick_y.toDouble(),
                right_stick_button,
                left_trigger.toDouble(),
                right_trigger.toDouble()
            ))
        }

        with(gamepad2) {
            master.update(GamepadData(
                left_bumper,
                right_bumper,
                a,
                b,
                x,
                y,
                dpad_up,
                dpad_down,
                dpad_left,
                dpad_right,
                left_stick_x.toDouble(),
                left_stick_y.toDouble(),
                left_stick_button,
                right_stick_x.toDouble(),
                right_stick_y.toDouble(),
                right_stick_button,
                left_trigger.toDouble(),
                right_trigger.toDouble()
            ))
        }


        encoders.forEach { (device, hardwareDevice) ->
            device.update(EncoderData(
                hardwareDevice.currentPosition.toDouble(),
                hardwareDevice.getVelocity(AngleUnit.RADIANS)
            ))
        }
        colors.forEach { (device, hardwareDevice) ->
            device.update(ColorSensorData(
                hardwareDevice.red().toDouble(),
                hardwareDevice.green().toDouble(),
                hardwareDevice.blue().toDouble(),
                hardwareDevice.alpha().toDouble()
            ))
        }
        touches.forEach { (device, hardwareDevice) ->
            device.update(hardwareDevice.isPressed)
        }
    }

    override fun stop() {
        scope.components.mapNotNull { it as? RobotComponent }.forEach(RobotComponent::stop)
        stopTask()

        breakAllConnections()
        devices.clear()
        availableDevices.clear()
        motors.clear()
        servos.clear()
        continuousServos.clear()
        encoders.clear()
        colors.clear()
        touches.clear()
    }

    abstract fun initTask()

    abstract fun stopTask()


}
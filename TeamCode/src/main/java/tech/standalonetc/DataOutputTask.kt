package tech.standalonetc

import com.qualcomm.robotcore.hardware.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import tech.standalonetc.protocol.RobotPacket
import tech.standalonetc.protocol.network.NetworkTools
import tech.standalonetc.protocol.packet.Packet

sealed class DataOutputTask {

    protected abstract val packet: Packet<*>

    fun run(networkTools: NetworkTools) {
        GlobalScope.launch(Dispatchers.IO) {
            networkTools.broadcastPacket(packet)
        }
    }
}

class EncoderDataOutputTask(private val id: Byte,
                            private val encoder: DcMotorEx)
    : DataOutputTask() {
    override val packet: Packet<*>
        get() = RobotPacket.EncoderDataPacket(id, encoder.currentPosition, encoder.velocity)
}

class VoltageDataOutputTask(networkTools: NetworkTools,
                            private val voltageSensors: List<VoltageSensor>)
    : DataOutputTask() {
    override val packet: Packet<*>
        get() = RobotPacket.VoltageDataPacket(voltageSensors.find { it.voltage > 0 }?.voltage ?: .0)
}

class GamepadDataOutputTask(private val isMaster: Boolean,
                            private val gamepad: Gamepad)
    : DataOutputTask() {
    override val packet: Packet<*>
        get() =
            with(gamepad) {
                RobotPacket.GamepadDataPacket(
                    if (isMaster)
                        RobotPacket.BuiltinId.GamepadMaster
                    else RobotPacket.BuiltinId.GamepadHelper,
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
                )
            }

}

class ColorSensorOutputTask(
    private val id: Byte,
    private val colorSensor: ColorSensor
) : DataOutputTask() {
    override val packet: Packet<*>
        get() =
            with(colorSensor) {
                RobotPacket.ColorSensorDataPacket(id,
                    red().toDouble(),
                    green().toDouble(),
                    blue().toDouble(),
                    alpha().toDouble()
                )
            }
}

class TouchSensorOutputTask(
    private val id: Byte,
    private val touchSensor: TouchSensor
) : DataOutputTask() {
    override val packet: Packet<*>
        get() = RobotPacket.TouchSensorDataPacket(id, touchSensor.isPressed)

}
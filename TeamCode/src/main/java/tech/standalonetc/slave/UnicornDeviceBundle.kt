package org.mechdancer.ftc

import tech.standalonetc.host.DeviceBundle
import tech.standalonetc.host.struct.effector.Motor
import tech.standalonetc.slave.OMRON_ENCODER_CPR
import kotlin.math.PI

object UnicornDeviceBundle : DeviceBundle() {
    init {
        "chassis" {
            motor("LF", Motor.Direction.REVERSED)
            motor("LB", Motor.Direction.REVERSED)
            motor("RF", Motor.Direction.FORWARD)
            motor("RB", Motor.Direction.FORWARD)
        }

        "dumper"{
            motor("am", Motor.Direction.REVERSED)
            encoder("am", OMRON_ENCODER_CPR)
            servo("servo", .0..PI)
        }

        "dustpan" {
            servo("servo", .0..PI)
        }

        "expander" {
            motor("matrix")
            servo("servo", .0..PI)
        }


        "lifter" {
            motor("am", Motor.Direction.REVERSED)
            touchSensor("touch")
        }

        "collector" {
            motor("matrix", Motor.Direction.REVERSED)
            continuousServo("cr")
        }
    }
}


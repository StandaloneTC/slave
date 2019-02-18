package tech.standalonetc.slave

import com.qualcomm.robotcore.eventloop.opmode.TeleOp
import org.mechdancer.dataflow.core.linkTo
import org.mechdancer.dataflow.core.minus
import tech.standalonetc.host.struct.preset.chassis.MecanumChassis
import tech.standalonetc.slave.strucs.*

@TeleOp
class RemoteControl : ExperimentalOpMode() {
    override fun initTask() {
        setup(Collector)
        setup(Dumper)
        setup(Dustpan)
        setup(Expander)
        setup(Lifter)

        val chassis=MecanumChassis().also { setup(it) }

        //Chassis
        master.updated - {
            MecanumChassis.Descartes(
                it.leftStickY,
                it.leftStickX,
                -it.rightStickX
            )
        } - chassis.descartesControl


        //Expander

        //Helper up: Expanding/Stop
        helper.up.pressing - { Expander.State.Expanding } - Expander.state
        helper.up.releasing - { Expander.State.Stop } - Expander.state
        //Helper down: Shirking/Stop
        helper.down.pressing - { Expander.State.Shrinking } - Expander.state
        helper.down.releasing - { Expander.State.Stop } - Expander.state
        //Helper left and right: Lock/Unlock
        helper.left.pressing - { true } - Expander.lock
        helper.right.pressing - { false } - Expander.lock

        //Collector

        //Helper left stick: Lifting/Dropping/Stop
        helper.leftStick.valueChanged - {
            when {
                it.y > 0.5  -> Collector.ArmState.Lifting
                it.y < -0.5 -> Collector.ArmState.Dropping
                else        -> Collector.ArmState.Stop
            }
        } - Collector.arm
        //Helper right trigger: Collecting/Stop
        helper.rightTrigger.pressing - { Collector.CollectorState.Collecting } - Collector.core
        helper.rightTrigger.releasing - { Collector.CollectorState.Stop } - Collector.core
        //Helper left bumper: Spiting/Stop
        helper.leftBumper.pressing - { Collector.CollectorState.Spiting } - Collector.core
        helper.leftBumper.releasing - { Collector.CollectorState.Stop } - Collector.core

        //Dumper TODO: Arguments

        //Master right bumper: Pushes rod 2 times
        master.rightBumper.pressing - { 2 } - Dumper.pushRod
        master.y.pressing - { 0.35 + if (Dumper.inSadArea) 0.2 else .0 } - Dumper.power
        master.y.releasing - { .0 } - Dumper.power
        master.a.pressing - { 0.5 + if (Dumper.inSadArea) 0.2 else .0 } - Dumper.power
        master.b.releasing - { .0 } - Dumper.power
        //Helper y: Enable dumper lock
        helper.y.pressing linkTo { Dumper.lockEnable = !Dumper.lockEnable }

        //Lifter

        //When master left trigger is pressed, robot lands to ground
        //Presses right trigger to interrupt
        master.leftTrigger.pressing - { Lifter.State.Landing } - Lifter.state
        master.rightTrigger.pressing - {
            if (Lifter.lastState == Lifter.State.Landing)
                Lifter.State.Stop
            else
                Lifter.State.Lifting
        } - Lifter.state
        master.rightTrigger.releasing - { Lifter.State.Stop } - Lifter.state

        //Dustpan
        //Master left bumper: dumps 1 time
        master.leftBumper.pressing - { 1 } - Dustpan.dump
    }

    override fun stopTask() {

    }

}
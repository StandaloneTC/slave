package tech.standalonetc.host

import org.mechdancer.dataflow.core.intefaces.IFullyBlock
import org.mechdancer.dataflow.core.intefaces.ILink

fun ByteArray.decodeToBoolean() = when (firstOrNull()?.toInt()) {
    1    -> true
    0    -> false
    null -> null
    else -> throw IllegalArgumentException()
}


fun <T : Comparable<T>> T.checkedValue(range: ClosedFloatingPointRange<T>) =
    takeIf { it in range }


fun deviceBundle(block: DeviceBundle.() -> Unit) = DeviceBundle().apply(block)

val DeviceBundle.idMaps
    get() = idMapping.toList().toTypedArray()

fun breakAllConnections() = ILink.list.forEach {
    it.dispose()
}

typealias DataBlock<T> = IFullyBlock<T, T>
#!/usr/bin/env kotlin

import java.io.File
import kotlin.math.roundToInt

val lines = File("beat_times.txt").readLines()

val timestamps = mutableListOf<Float>()
var previous = 0.0f
var cumulative = 0f

lines.forEachIndexed { index, line ->
    val timestamp = line.trim().toFloat()
    timestamps.add(timestamp)
    when{
        index == 0 -> {
            previous = timestamp
        }
        else -> {
            val elapsed = timestamp - previous
            cumulative += elapsed

            println("Processing: $timestamp elapsed: $elapsed cumulative: $cumulative")
            previous = timestamp
        }
    }
}

val averageElapsed = (cumulative/(timestamps.size - 1))
val bpm = (60f/averageElapsed)
println("bpm: $bpm")


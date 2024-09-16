#!/usr/bin/env kotlin

@file:DependsOn("org:jaudiotagger:2.0.3")//for bitrate

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

val filepath = args.first()
val file = File(filepath)

println("\n\uD83E\uDD41 Looking for BPM tag in: $filepath\n")

Logger.getLogger("org.jaudiotagger").level = Level.OFF
var mp3 = AudioFileIO.read(file) as MP3File
var bpm = mp3.iD3v2Tag.getFirst("TBPM").toFloatOrNull() ?: mp3.iD3v2TagAsv24.getFirst("TBPM").toFloatOrNull() ?: -1f
if(bpm == -1f){
    println("No BPM tag found in file")
}else{
    println("BPM: $bpm (${bpm.toInt()})")
}

println()
#!/usr/bin/env kotlin

@file:DependsOn("org.apache.tika:tika-parser-audiovideo-module:2.0.0")
@file:DependsOn("org.xerial:sqlite-jdbc:3.34.0")
@file:DependsOn("org:jaudiotagger:2.0.3")//for bitrate

import org.apache.tika.metadata.Metadata
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.mp3.Mp3Parser
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3File
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.system.exitProcess


println("\n\n\n")
println("  ::::::::::. :::.       .,-::::: .,::::::  .        :    :::.      :::  .   .,:::::: :::::::..")
println("  `;;;```.;;;;;`;;    ,;;;'````' ;;;;''''  ;;,.    ;;;   ;;`;;     ;;; .;;,.;;;;'''' ;;;;``;;;;")
println("  `]]nnn]]',[[ '[[,  [[[         [[cccc   [[[[, ,[[[[, ,[[ '[[,   [[[[[/'   [[cccc   [[[,/[[['")
println("  $$$\"\"  c$$\$cc$$\$c $$$         $$\"\"\"\"   $$$$$$$$\"$$\$c$$\$cc$$\$c _$$$$,     $$\"\"\"\"   $$$$$\$c")
println("  888o    888   888,`88bo,__,o, 888oo,__ 888 Y88\" 888o888   888,\"888\"88o,  888oo,__ 888b \"88bo,")
println("  YMMMb   YMM   \"\"`   \"YUMMMMMP\"\"\"\"\"YUMMMMMM  M'  \"MMMYMM   \"\"`  MMM \"MMP\" \"\"\"\"YUMMMMMMM   \"W\"")
println("\n\n")

//Check Pacemaker path is available, error if not
when{
    pacemakerMounted() -> println("\uD83D\uDC4D Pacemaker found\n")
    else -> {
        println("\n\uD83D\uDE2D Connect your Pacemaker with a USB cable and ensure it's in mass storage mode (press the rear power button once after connection)\n")
        exitProcess(-1)
    }
}

//Check arguments
when {
    args.isEmpty() -> {
        println("⚠\uFE0F \n\nUsage:")
        println("./pacemaker.main.kts install - setup a new Pacemaker or wiped hard disk")
        println("./pacemaker.main.kts install latest - as above but uses the last beta from 2015")
        println("./pacemaker.main.kts install some.pfw - install custom firmware")
        println("./pacemaker.main.kts version - fetches the firmware version from a connected Pacemaker")
        println("./pacemaker.main.kts some.mp3 - extract details from MP3 file and copy to Pacemaker")
        println("./pacemaker.main.kts some.mp3 -bpm - as above but calculates BPM using Aubio (if installed)")
        println("./pacemaker.main.kts /folder/path - processes a directory of MP3s")
        println("./pacemaker.main.kts /folder/path -bpm - as above but calculates BPM using Aubio (if installed)")
        println("./pacemaker.main.kts restore - restore/undo last operation in an emergency")
        println("./pacemaker.main.kts copydb - copy database from Pacemaker to local directory")
        println("./pacemaker.main.kts pushdb - copy database from local directory to Pacemaker")
        println("./pacemaker.main.kts unmount - unmount the Pacemaker volume from Mac OS ready for use")
        println("./pacemaker.main.kts tree - for debug use, requires tree to be installed")
        println()
        exitProcess(0)
    }

    args.first() == "install" -> {
        if(args.size == 1) {
            println("⚠\uFE0F \n\nThis will install the last official firmware (14142), are you sure? (y, n)")
            val confirmation = readln()
            if (confirmation.lowercase() == "y") {
                println("\nCopying 14142 firmware to Pacemaker\n")
                shell("cp assets/dfw_14142.pfw /Volumes/Pacemaker/.Pacemaker/")
                shell("diskutil unmount /Volumes/Pacemaker")
                println("\nUnplug Pacemaker from USB to finish firmware update\n")
            } else {
                println("\nCancelling process\n")
            }
            exitProcess(0)
        }else if(args[1].lowercase().endsWith(".pfw")) {
            //Custom firmware
            println("⚠\uFE0F \n\nThis will install file ${args[1]}, are you sure? (y, n)")
            val confirmation = readln()
            if (confirmation.lowercase() == "y") {
                println("\nCopying ${args[1]} firmware to Pacemaker\n")
                shell("cp '${args[1]}' /Volumes/Pacemaker/.Pacemaker/")
                shell("diskutil unmount /Volumes/Pacemaker")
                println("\nUnplug Pacemaker from USB to finish firmware update\n")
            } else {
                println("\nCancelling process\n")
            }
            exitProcess(0)
        }else if(args[1].lowercase() == "latest"){
            println("⚠\uFE0F \n\nThis will install the 2015 beta firmware, are you sure? (y, n)")
            val confirmation = readln()
            if (confirmation.lowercase() == "y") {
                println("\nCopying firmware to Pacemaker\n")
                shell("cp 'assets/dfw_13606814.pfw' /Volumes/Pacemaker/.Pacemaker/")
                shell("diskutil unmount /Volumes/Pacemaker")
                println("\nUnplug Pacemaker from USB to finish firmware update\n")
            } else {
                println("\nCancelling process\n")
            }
            exitProcess(0)
        }else{
            println("⚠\uFE0F \n\nBad argument: ${args[1]}\n")
            exitProcess(-1)
        }

    }
    args.first() == "version" -> {
        println("\uD83D\uDDD2\uFE0F  Attempting to fetch firmware version...\n")
        val firmware = checkFirmwareVersion()
        println("${parseFirmwareVersion(firmware)}\n")
        exitProcess(0)
    }

    args.first() == "restore" -> {
        println("⚠\uFE0F \n\nAttempting to restore from backup")
        shell("cp /Volumes/Pacemaker/.Pacemaker/music.db.bak /Volumes/Pacemaker/.Pacemaker/music.db")
        exitProcess(0)
    }

    args.first() == "copydb" -> {
        println("⚠\uFE0F \n\nCopying database from Pacemaker")
        shell("cp /Volumes/Pacemaker/.Pacemaker/music.db music.db")
        exitProcess(0)
    }

    args.first() == "pushdb" -> {
        println("⚠\uFE0F \n\nPushing local database to Pacemaker")
        shell("cp music.db /Volumes/Pacemaker/.Pacemaker/music.db")
        exitProcess(0)
    }

    args.first() == "unmount" -> {
        println("⚠\uFE0F \n\nUnmounting Pacemaker")
        shell("diskutil unmount /Volumes/Pacemaker")
        exitProcess(0)
    }

    args.first().lowercase().endsWith(".mp3") -> {
        when{
            args.size > 1 && args[1].lowercase() == "-bpm" -> processMP3(args.first(), true)
            else -> processMP3(args.first(), false)
        }
    }
    File(args.first()).isDirectory -> {
        when{
            args.size > 1 && args[1].lowercase() == "-bpm" -> processDirectory(args.first(), true)
            else -> processDirectory(args.first(), false)
        }
    }
    args.first() == "tree" -> {
        println("⚠\uFE0F \n\nChecking Pacemaker filesystem")
        shell("tree -a /Volumes/Pacemaker")
        exitProcess(0)
    }
    else -> {
        println("\nI don't know what to do with this: ${args.first()}\n")
        exitProcess(-1)
    }
}

fun processDirectory(path: String, calculateBpm: Boolean){
    val directory = File(path)
    directory.listFiles()?.forEach { file ->
        if(file.name.lowercase().endsWith(".mp3")){
            processMP3(file.path, calculateBpm)
        }
    }
}

fun processMP3(filepath: String, calculateBpm: Boolean) {
    println("processMP3: $filepath")
    if(!filepath.endsWith(".mp3")){
        println("Bad file: $filepath")
        exitProcess(-1)
    }
    if(checkFirmwareVersion() != "14142"){
        println("\n✋ This script only works with firmware version 14142 (the last official firmware from Tonium)")
        exitProcess(-1)
    }else{
        println("\uD83D\uDC4D Pacemaker firmware version: 14142\n")
    }

    println("\uD83D\uDDC4\uFE0F  Copying database\n")
    shell("cp /Volumes/Pacemaker/.Pacemaker/music.db music.db")

    println("\uD83D\uDDC4\uFE0F  Creating local backup\n")
    shell("cp music.db music.db.bak")

    println("\uD83D\uDDC4\uFE0F  Creating on-device backup\n")
    shell("cp /Volumes/Pacemaker/.Pacemaker/music.db /Volumes/Pacemaker/.Pacemaker/music.db.bak")

    println("\uD83D\uDD09 Inspecting file: $filepath\n")

    val file = File(filepath)

    val input: InputStream = FileInputStream(file)
    val handler = DefaultHandler()
    val metadata = Metadata()
    val parser = Mp3Parser()
    val parseCtx = ParseContext()
    parser.parse(input, handler, metadata, parseCtx)
    input.close()

    val metadataNames: Array<String> = metadata.names()
    metadataNames.forEach { entry ->
        println("Entry $entry: ${metadata.get(entry)}")
    }
    println()

    val connection: Connection = DriverManager.getConnection("jdbc:sqlite:music.db")

    println("\uD83D\uDDC4\uFE0F  Building new Pacemaker entry\n")

    //todo - this isn't needed, is primary key
    val track_id =
        connection.createStatement().executeQuery("select count(*) as idCount, * from Tracks")
            .getInt("idCount") + 1
    println("track_id: $track_id")

    val artist = metadata.get("xmpDM:artist") ?: metadata.get("dc:creator") ?: ""
    println("artist: $artist")

    val album_artist = metadata.get("xmpDM:albumArtist") ?: artist
    println("album_artist: $album_artist")

    val title = metadata.get("dc:title") ?: ""
    println("title: $title")

    val composer = ""

    val album = metadata.get("xmpDM:album") ?: ""
    println("album: $album")

    val track_number = metadata.get("xmpDM:trackNumber")?.toInt() ?: 0
    println("track_number: $track_number")

    val year = metadata.get("xmpDM:releaseDate")?.toInt() ?: 0
    println("album: $year")

    val genre = ""
    val is_part_of_c = 0

    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(file.nameWithoutExtension.toByteArray())

    @OptIn(ExperimentalStdlibApi::class)
    val global_id = digest.toHexString()

    val location = "/pmdb_tracks/${global_id}.${file.extension}"
    println("location: $location")

    val format = metadata.get("xmpDM:audioCompressor") ?: {
        if (filepath.endsWith("mp3", true)) {
            "MP3"
        } else if (filepath.endsWith("flac", true)) {
            "FLAC"
        } else {
            "Unknown"
        }
    }
    println("format: $format")

    val sample_rate =
        metadata.get("samplerate")?.toInt() ?: metadata.get("xmpDM:audioSampleRate")?.toInt()
        ?: 44100

    Logger.getLogger("org.jaudiotagger").level = Level.OFF
    val mp3 = AudioFileIO.read(file) as MP3File
    val bit_rate = mp3.mP3AudioHeader.bitRateAsNumber
    println("bit_rate: $bit_rate")

    println("sample_rate: $sample_rate")

    val file_size = file.length()
    println("file_size: $file_size")

    val play_time_secs = metadata.get("xmpDM:duration").toFloat().toInt()
    println("play_time_seconds: $play_time_secs")

    val rating = 0

    val comments = metadata.get("xmpDM:logComment").replace("\n", " ")
    println("comments: $comments")

    val date_added = 1725800667123

    val last_played = -1
    val times_played = 0
    val cue_point = -1
    val rc_mixes = 0

    var bpm = mp3.iD3v2Tag.getFirst("TBPM").toFloatOrNull() ?: mp3.iD3v2TagAsv24.getFirst("TBPM")
        .toFloatOrNull() ?: -1f
        
    if(bpm == -1f && calculateBpm){
        val calculatedBpm = shellResponse("aubio tempo \"${file.name}\"")
        println(">>> Aubio calculatedBpm: $calculatedBpm\n")
        bpm = calculatedBpm.split(" ").first().toFloat()
    }
    
    println("bpm: $bpm")

    val label = ""//unused
    val track_flags = 2//no idea

    println("global_id: $global_id")

    val loop_in = -1
    val loop_out = -1

    val structured_ct = ""


    val ind_title = title
    val ind_artist = artist
    val ind_album = album
    val ind_genre = genre
    val ind_bpm = bpm.toInt()
    val discid = ""
    val producer = ""
    val remixer = ""
    val key = ""
    val number_of_tracks = 0
    val number_of_discs = 0
    val disc_number = 0
    val date_modified = 1725800673592
    val modified_by_ed = "2.0.2.14170"
    val analyzed_by_ed = "2.0.2.14170"
    val analysis_ver = 1

    println("\n\uD83D\uDCC1 Copying music to Pacemaker...")
    shell("cp \"$filepath\" /Volumes/Pacemaker/.Pacemaker/Music/")
    shell("mv \"/Volumes/Pacemaker/.Pacemaker/Music/${file.name}\" /Volumes/Pacemaker/.Pacemaker/Music/${global_id}.${file.extension}")

    println("\n\uD83D\uDDC4\uFE0F   Writing entry to local database...")
    val writeStatement: Statement = connection.createStatement()
    writeStatement.execute(
        "INSERT INTO Tracks (artist,album_artist,title,composer,album,track_number,year,genre,is_part_of_c,location,format,bit_rate,sample_rate,file_size,play_time_secs,rating,comments,date_added,last_played,times_played,cue_point,rc_mixes,bpm,label,track_flags,global_id,loop_in,loop_out,structured_ct,ind_title,ind_artist,ind_album,ind_genre,ind_bpm,discid,producer,remixer,key,number_of_tracks,disc_number,number_of_discs,date_modified,modified_by_ed,analyzed_by_ed,analysis_ver) VALUES (\"$artist\",\"$album_artist\",\"$title\",\"$composer\",\"$album\",$track_number,$year,\"$genre\",$is_part_of_c,\"$location\",\"$format\",$bit_rate,$sample_rate,$file_size,$play_time_secs,$rating,\"$comments\",$date_added,$last_played,$times_played,$cue_point,$rc_mixes,$bpm,\"$label\",$track_flags,\"$global_id\",$loop_in,$loop_out,\"$structured_ct\",\"$ind_title\",\"$ind_artist\",\"$ind_album\",\"$ind_genre\",$ind_bpm,\"$discid\",\"$producer\",\"$remixer\",\"$key\",$number_of_tracks,$disc_number,$number_of_discs,$date_modified,\"$modified_by_ed\",\"$analyzed_by_ed\",$analysis_ver);"
    )
    connection.close()

    println("\n\uD83D\uDCC1 Copying updated database to Pacemaker...")
    shell("cp music.db /Volumes/Pacemaker/.Pacemaker/music.db")

    println("\n♾\uFE0F  Pacemaker script finished (use ./pacemaker.main.kts unmount to start playing).\n\n")
}

fun pacemakerMounted(): Boolean {
    return File("/Volumes/Pacemaker").exists()
}

fun checkFirmwareVersion(): String {
    shell("cp /Volumes/Pacemaker/.Pacemaker/system.xml system.xml")
    File("system.xml").readText().also { xml ->
        var firmware = xml.substring(xml.indexOf("SWVERSION=") + 11, xml.length)
        firmware = firmware.substring(0, firmware.indexOf("\""))
        shell("rm system.xml")
        return firmware
    }
}

fun parseFirmwareVersion(firmware: String): String{
    return when(firmware){
        "13606814" -> "13606814: December 2015 beta, bug fixes and new white noise effect"
        "146061537" -> "146061537: February 2014 beta, bug fixes, scratch improvements"
        "180096069" -> "180096069: September 2012 beta, new scratching feature, various fixes"
        "16219" -> "16219: Unstable leaked official Tonium firmware"
        "14142" -> "14142: The last official Tonium firmware"
        "14108" -> "14108: Early Tonium firmware - recommend upgrading to 14142"
        "13557" -> "13557: Early Tonium firmware - recommend upgrading to 14142"
        else -> "Unknown firmware version: $firmware"
    }
}

fun shell(command: String) {
    val process = ProcessBuilder("/bin/bash", "-c", command).inheritIO().start()
    process.waitFor()
}

fun shellResponse(command: String): String {
    val process = ProcessBuilder("/bin/bash", "-c", command).start()
    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
        return reader.readLine()
    }
}

package org.hathitrust.htrc.tools.rsyncgenerator

import java.io.{File, FileInputStream, PrintWriter}
import java.util.Properties

import org.hathitrust.htrc.data._
import org.hathitrust.htrc.tools.scala.io.IOUtils.using
import org.rogach.scallop.{ScallopConf, ScallopOption}

import scala.io.{Source, StdIn}
import scala.util.Try

/**
  * This tool generates a shell script that allows one to download the extracted features
  * files for the volumes of a given workset.
  *
  * @author Boris Capitanu
  */

object Main extends App {

  case class Config(volumes: TraversableOnce[String], scriptFile: File, datasetName: String, format: String)

  def loadConfiguration(configFile: String): Try[Config] = Try {
    val props = new Properties()
    props.load(new FileInputStream(configFile))

    Config(
      using(Source.fromFile(props.getProperty("collectionLocation")))(_.getLines().toList),
      new File(props.getProperty("outputDir"), props.getProperty("outputFile")),
      props.getProperty("dataset"),
      props.getProperty("format", "pairtree")
    )
  }

  def generateRsyncScript(volIds: TraversableOnce[String], dataset: String, format: HtrcVolumeId => String): String = {
    // convert the volume ID list to EF paths
    val volPaths = volIds.toSet[String]
      .map(HtrcVolumeId.parseUnclean(_).get)
      .map(format)
      .mkString("\n")

    val script =
      s"""
        |#!/usr/bin/env bash
        |
        |read -e -p "Enter the folder where to save the Extracted Features files: [$$PWD] " DEST
        |DEST=$${DEST:-$$PWD}
        |
        |if [ ! -d "$$DEST" ]; then
        |   read -e -p "Folder [$$DEST] does not exist - create? [Y/n] " YN
        |   YN=$${YN:-Y}
        |   case $$YN in
        |      [yY][eE][sS]|[yY])
        |         mkdir -p "$$DEST" || exit $$?
        |         ;;
        |      *)
        |         echo "Aborting."
        |         exit 2
        |         ;;
        |   esac
        |fi
        |
        |cat << EOF | rsync -avh --no-perms --no-owner --stats --files-from=- data.analytics.hathitrust.org::$dataset "$$DEST"
        |$volPaths
        |EOF
      """.stripMargin.trim

    script
  }

  def pairtreeFormat(id: HtrcVolumeId): String =
    id.toPairtreeDoc.extractedFeaturesPath

  def stubbytreeFormat(id: HtrcVolumeId): String =
    id.toStubbytreeDoc.extractedFeaturesPath

  // get configuration either from the 'collection.properties' file (if no command line argument
  // is provided), or from the command line
  val config =
    if (args.isEmpty)
      loadConfiguration("collection.properties").get
    else {
      val conf = new Conf(args)
      val outputFile = conf.outputFile()
      val format = conf.outputFormat()
      val datasetName = conf.datasetName()
      val htids = conf.htids.toOption match {
        case Some(file) => using(Source.fromFile(file))(_.getLines().toList)
        case None => Iterator.continually(StdIn.readLine()).takeWhile(_ != null)
      }
      Config(htids, outputFile,datasetName, format)
    }

  val script = generateRsyncScript(config.volumes, config.datasetName, config.format match {
    case "pairtree" => pairtreeFormat
    case "stubby" => stubbytreeFormat
  })

  using(new PrintWriter(config.scriptFile))(_.write(script))
}


/**
  * Command line argument configuration
  *
  * @param arguments The cmd line args
  */
class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  val (appTitle, appVersion, appVendor) = {
    val p = getClass.getPackage
    val nameOpt = Option(p).flatMap(p => Option(p.getImplementationTitle))
    val versionOpt = Option(p).flatMap(p => Option(p.getImplementationVersion))
    val vendorOpt = Option(p).flatMap(p => Option(p.getImplementationVendor))
    (nameOpt, versionOpt, vendorOpt)
  }

  version(appTitle.flatMap(
    name => appVersion.flatMap(
      version => appVendor.map(
        vendor => s"$name $version\n$vendor"))).getOrElse("rsync-generator"))

  val outputFile: ScallopOption[File] = opt[File]("output",
    descr = "Writes the generated rsync script to FILE",
    required = true,
    argName = "FILE"
  )

  val outputFormat: ScallopOption[String] = opt[String]("format",
    descr = "The output format (one of 'pairtree' or 'stubby')",
    default = Some("pairtree"),
    validate = Set("pairtree", "stubby").contains
  )

  val datasetName: ScallopOption[String] = opt[String]("dataset",
    descr = "The name of the dataset to rsync from",
    required = true
  )

  val htids: ScallopOption[File] = trailArg[File]("ids",
    descr = "The file containing the list of HT IDs to rsync (if omitted, will read from stdin)",
    required = false
  )

  validateFileExists(htids)
  verify()
}

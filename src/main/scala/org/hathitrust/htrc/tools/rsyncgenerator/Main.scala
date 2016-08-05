package org.hathitrust.htrc.tools.rsyncgenerator

import java.io.{File, FileInputStream, PrintWriter}
import java.util.Properties

import org.hathitrust.htrc.tools.pairtreehelper.PairtreeHelper
import org.rogach.scallop.ScallopConf

import scala.io.{Source, StdIn}
import scala.util.Try
import resource._

/**
  * This tool generates a shell script that allows one to download the extracted features
  * files for the volumes of a given workset.
  *
  * @author Boris Capitanu
  */

object Main extends App {

  case class Config(volumes: TraversableOnce[String], scriptFile: File)

  def loadConfiguration(configFile: String): Try[Config] = Try {
    val props = new Properties()
    props.load(new FileInputStream(configFile))

    Config(
      Source.fromFile(props.getProperty("collectionLocation")).getLines(),
      new File(props.getProperty("outputDir"), props.getProperty("outputFile"))
    )
  }

  def generateRsyncScript(volIds: TraversableOnce[String]): String = {
    // convert the volume ID list to EF paths
    val volPaths = volIds.toSet[String]
      .map(PairtreeHelper.getDocFromUncleanId)
      .map(d => s"${d.getDocumentRootPath}/${d.getCleanId}.json.bz2")
      .mkString("\n")

    val script =
      s"""
        |#!/bin/bash
        |
        |CWD=$$(pwd)
        |read -e -p "Enter the folder where to save the Extracted Features files: [$$CWD] " DEST
        |DEST=$${DEST:-$$CWD}
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
        |cat << EOF | rsync -v --stats --files-from=- data.analytics.hathitrust.org::pd-features "$$DEST"
        |$volPaths
        |EOF
      """.stripMargin.trim

    script
  }

  // get configuration either from the 'collection.properties' file (if no command line argument
  // is provided), or from the command line
  val config =
    if (args.isEmpty)
      loadConfiguration("collection.properties").get
    else {
      val conf = new Conf(args)
      val outputFile = conf.outputFile()
      val htids = conf.htids.toOption match {
        case Some(file) => Source.fromFile(file).getLines()
        case None => Iterator.continually(StdIn.readLine()).takeWhile(_ != null)
      }
      Config(htids, outputFile)
    }

  val script = generateRsyncScript(config.volumes)

  for (writer <- managed(new PrintWriter(config.scriptFile)))
    writer.write(script)
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

  val outputFile = opt[File]("output",
    descr = "Writes the generated rsync script to FILE",
    required = true,
    argName = "FILE"
  )

  val htids = trailArg[File]("ids",
    descr = "The file containing the list of HT IDs to rsync (if omitted, will read from stdin)",
    required = false
  )

  validateFileExists(htids)
  verify()
}
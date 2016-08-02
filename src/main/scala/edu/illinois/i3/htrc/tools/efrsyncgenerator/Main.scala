package edu.illinois.i3.htrc.tools.efrsyncgenerator

import java.io.{File, FileInputStream, PrintWriter}
import java.util.Properties

import edu.illinois.i3.htrc.tools.PairtreeHelper

import scala.io.Source
import scala.util.Try
import resource._

/**
  * This tool generates a shell script that allows one to download the extracted features
  * files for the volumes of a given workset.
  *
  * @author Boris Capitanu
  */

object Main extends App {

  case class Config(volumes: Iterator[String], scriptFile: File)

  def loadConfiguration(configFile: String): Try[Config] = Try {
    val props = new Properties()
    props.load(new FileInputStream(configFile))

    Config(
      Source.fromFile(props.getProperty("collectionLocation")).getLines(),
      new File(props.getProperty("outputDir"), props.getProperty("outputFile"))
    )
  }

  val config = loadConfiguration("collection.properties").get

  // convert the volume ID list to EF paths
  val volPaths = config.volumes
    .map(PairtreeHelper.getDocFromUncleanId)
    .map(_.getDocumentPathPrefix + ".json.bz2")
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

  for (writer <- managed(new PrintWriter(config.scriptFile)))
    writer.write(script)
}

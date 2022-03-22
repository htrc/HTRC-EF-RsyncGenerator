package org.hathitrust.htrc.tools.rsyncgenerator

import org.hathitrust.htrc.data._
import org.rogach.scallop.{ScallopConf, ScallopOption}
import play.api.libs.json.Reads._
import play.api.libs.json._

import java.io.{File, FileInputStream, InputStream, PrintWriter}
import java.net.URL
import java.util.Properties
import scala.io.{Source, StdIn}
import scala.util.{Try, Using}

/**
  * This tool generates a shell script that allows one to download the extracted features
  * files for the volumes of a given workset.
  *
  * @author Boris Capitanu
  */

object Main extends App {

  case class Config(volumes: IterableOnce[String], scriptFile: File, datasetName: String, datasetsRegistry: URL)

  case class RsyncEndpoint(host: String, port: Option[Int])

  object Dataset {
    private val regex = raw"""([^:]+)(?::(\d{3,4}))?""".r
  }
  case class Dataset(name: String, format: String, endpoint: String) {
    import Dataset.regex

    val rsyncEndpoint: RsyncEndpoint = endpoint match {
      case regex(host, port) => RsyncEndpoint(host, Option(port).map(_.toInt))
      case regex(host) => RsyncEndpoint(host, None)
      case _ => throw new RuntimeException(s"Invalid dataset endpoint in configuration: $endpoint")
    }
  }

  def loadConfiguration(configStream: InputStream): Try[Config] = Try {
    val props = new Properties()
    props.load(configStream)

    Config(
      Using.resource(Source.fromFile(props.getProperty("collectionLocation")))(_.getLines().toList),
      new File(props.getProperty("outputDir"), props.getProperty("outputFile")),
      props.getProperty("dataset"),
      new URL(props.getProperty("datasetsRegistry", Conf.DEFAULT_DATASETS_REGISTRY))
    )
  }

  def loadDatasetsRegistry(datasetsRegistryUrl: URL): Map[String, Dataset] = {
    implicit val datasetReads: Reads[Dataset] = Json.reads[Dataset]

    val datasets = Using.resource(datasetsRegistryUrl.openStream()) { is =>
      Json.parse(is).as[List[Dataset]]
    }

    datasets.iterator.map(d => d.name -> d).toMap
  }

  def generateRsyncScript(volIds: IterableOnce[String], dataset: Dataset): String = {
    val format: HtrcVolumeId => String = dataset.format match {
      case "pairtree" => pairtreeFormat
      case "stubby" => stubbytreeFormat
    }

    // convert the volume ID list to EF paths
    @SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
    val volPaths = volIds.iterator.to(Set)
      .map(id => HtrcVolumeId.parseUnclean(id).get)
      .map(format)
      .mkString("\n")

    val portArg = dataset.rsyncEndpoint.port.map(port => s"--port $port ").getOrElse("")

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
        |cat << EOF | rsync -avh ${portArg}--no-perms --no-owner --stats --files-from=- ${dataset.rsyncEndpoint.host}::${dataset.name} "$$DEST"
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
  @SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
  val config =
    if (args.isEmpty) {
      val COLLECTION_FILENAME = "collection.properties"
      val configStream =
        Option(System.getProperty("RSYNC_GENERATOR_CONFIG", COLLECTION_FILENAME)).flatMap(c => Try(new FileInputStream(c)).toOption)
          .getOrElse {
            throw new RuntimeException(COLLECTION_FILENAME + " not found!")
          }
      Using.resource(configStream)(loadConfiguration(_).get)
    } else {
      val conf = new Conf(args.toIndexedSeq)
      val outputFile = conf.outputFile()
      val datasetsRegistry = conf.datasetsRegistry()
      val datasetName = conf.datasetName()
      val htids = conf.htids.toOption match {
        case Some(file) => Using.resource(Source.fromFile(file))(_.getLines().toList)
        case None => Iterator.continually(StdIn.readLine()).takeWhile(_ != null)
      }
      Config(htids, outputFile,datasetName, datasetsRegistry)
    }

  val datasets = loadDatasetsRegistry(config.datasetsRegistry)
  val dataset = datasets.getOrElse(config.datasetName, {
    throw new RuntimeException(s"Unknown dataset: ${config.datasetName} - forgot to update dataset registry?")
  })

  val script = generateRsyncScript(config.volumes, dataset)

  Using(new PrintWriter(config.scriptFile))(_.write(script))
}

object Conf {
  val DEFAULT_DATASETS_REGISTRY = "https://data.htrc.illinois.edu/rsync_datasets_registry.json"
}

/**
  * Command line argument configuration
  *
  * @param arguments The cmd line args
  */
class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  import Conf.DEFAULT_DATASETS_REGISTRY

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


  val datasetsRegistry: ScallopOption[URL] = opt[URL]("registry",
    descr = "The dataset registry URL",
    argName = "URL",
    default = Some(new URL(DEFAULT_DATASETS_REGISTRY))
  )

  val outputFile: ScallopOption[File] = opt[File]("output",
    descr = "Writes the generated rsync script to FILE",
    required = true,
    argName = "FILE"
  )

  val datasetName: ScallopOption[String] = opt[String]("dataset",
    descr = "The name of the dataset to rsync from",
    required = true,
    argName = "NAME"
  )

  val htids: ScallopOption[File] = trailArg[File]("ids",
    descr = "The file containing the list of HT IDs to rsync (if omitted, will read from stdin)",
    required = false
  )

  validateFileExists(htids)
  verify()
}

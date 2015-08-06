package com.collective.utils


import java.io.{PrintWriter, FileWriter}

import akka.actor.Actor
import com.collective.models.{DFPCampaign, AppnexusCampaign}
import com.collective.utils.AppnexusFileWriterActor.{XFPFileData, AppnexusFileData}

/**
 * Created by anand on 03/08/15.
 */
object AppnexusFileWriterActor {
  case class AppnexusFileData(data: List[AppnexusCampaign])
  case class XFPFileData(data: List[DFPCampaign])
}

class AppnexusFileWriterActor extends Actor with Logging {

  def receive = {
    case AppnexusFileData(data) => writeToAppnexusFile(data)
    case XFPFileData(data) => writeToXFPFile(data)
  }

  def writeToAppnexusFile(data: List[AppnexusCampaign]) = {
    //log.info("writing to file campaigns size = " + data.size)
    val fileWriter: FileWriter = new FileWriter(ServicesConfig.appnexusConfig("appnexus.output.file.path"), true)
    val writer: PrintWriter = new PrintWriter(fileWriter, true)
    try {
      for (campaign <- data) {
        writer.println(campaign.toString)
      }
    } catch {
      case error: Exception => log.error("Error while writing appnexus campaigns to file ", error)
    } finally {
      fileWriter.close()
      writer.close()
    }
  }

  def writeToXFPFile(data: List[DFPCampaign]) = {
    //log.info("writing to file campaigns size = " + data.size)
    val fileWriter: FileWriter = new FileWriter(ServicesConfig.appnexusConfig("xfp.output.file.path"), true)
    val writer: PrintWriter = new PrintWriter(fileWriter, true)
    try {
      for (lineItem <- data) {
        writer.println(lineItem.toString)
      }
    } catch {
      case error: Exception => log.error("Error while writing XFP campaigns to file ", error)
    } finally {
      fileWriter.close()
      writer.close()
    }
  }

  def writeToDiscrepancyReport(data: List[DFPCampaign]) = {
    //log.info("writing to file campaigns size = " + data.size)
    val fileWriter: FileWriter = new FileWriter(ServicesConfig.appnexusConfig("xfp.output.file.path"), true)
    val writer: PrintWriter = new PrintWriter(fileWriter, true)
    try {
      for (lineItem <- data) {
        writer.println(lineItem.toString)
      }
    } catch {
      case error: Exception => log.error("Error while writing XFP campaigns to file ", error)
    } finally {
      fileWriter.close()
      writer.close()
    }
  }

}

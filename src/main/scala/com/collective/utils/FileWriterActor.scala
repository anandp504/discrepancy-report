package com.collective.utils


import java.io.{PrintWriter, FileWriter}

import akka.actor.Actor
import com.collective.models.{DFPCampaign, AppnexusCampaign}
import com.collective.utils.FileWriterActor.{XFPFileData, AppnexusFileData}

/**
 * Created by anand on 03/08/15.
 */
object FileWriterActor {
  case class AppnexusFileData(data: List[AppnexusCampaign])
  case class XFPFileData(data: List[DFPCampaign])
}

class FileWriterActor extends Actor with Logging {

  def receive = {
    case AppnexusFileData(data) => writeToAppnexusFile(data)
    case XFPFileData(data) => writeToXFPFile(data)
  }

  def writeToAppnexusFile(data: List[AppnexusCampaign]) = {
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
    val fileWriter: FileWriter = new FileWriter(ServicesConfig.appnexusConfig("xfp.output.file.path"), true)
    val writer: PrintWriter = new PrintWriter(fileWriter, true)
    try {
      for (lineItem <- data) {
        if(!(lineItem.lineItemName.contains("^MOB") || lineItem.lineItemName.contains("^TAB") || lineItem.lineItemName.contains("^Mob") || lineItem.lineItemName.contains("^Tab")
          || lineItem.lineItemName.contains("^ MOB") || lineItem.lineItemName.contains("^ TAB") || lineItem.lineItemName.contains("^ Mob") || lineItem.lineItemName.contains("^ Tab")
          || lineItem.lineItemName.contains("MOB") || lineItem.lineItemName.contains("TAB") || lineItem.lineItemName.contains("Mob") || lineItem.lineItemName.contains("Tab"))) {
          writer.println(lineItem.toString)
        }
      }
    } catch {
      case error: Exception => log.error("Error while writing XFP campaigns to file ", error)
    } finally {
      fileWriter.close()
      writer.close()
    }
  }

  def writeToDiscrepancyReport(data: List[DFPCampaign]) = {
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

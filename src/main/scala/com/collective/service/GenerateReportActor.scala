package com.collective.service

import java.io._

import akka.actor.Actor
import com.collective.models.{DFPCampaign, AppnexusCampaign}
import com.collective.utils.{ServicesConfig, Logging}
import com.norbitltd.spoiwo.model.CellStyle
import com.norbitltd.spoiwo.model.Font
import com.norbitltd.spoiwo.model.Row
import com.norbitltd.spoiwo.model.Row
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.immutable.TreeMap
import scala.collection.mutable
import scala.concurrent.{Future, Promise}

/**
 * Created by anand on 05/08/15.
 */
class GenerateReportActor extends Actor with Logging{

  def receive = {
    case "GENERATE_REPORT" => readDiscrepancyReportSourceData()
  }

  def readDiscrepancyReportSourceData() = {

    val results = for {
      appnexusData <- readAppnexusData()
      xfpData <- readXfpData()
    } yield (xfpData, appnexusData)

    import util.control.Breaks._
    import com.norbitltd.spoiwo.model.{Column, Sheet, Font, CellStyle, Row}
    import com.norbitltd.spoiwo.natures.xlsx.Model2XlsxConversions._

    results.map {
      result =>
        new File(ServicesConfig.appnexusConfig("discrepancy.report.output.file.path")).delete
        //val fileWriter: FileWriter = new FileWriter(ServicesConfig.appnexusConfig("discrepancy.report.output.file.path"), true)
        //val writer: PrintWriter = new PrintWriter(fileWriter, true)
        val sheetData: ListBuffer[Row] = ListBuffer[Row]()
        val headerStyle = CellStyle(font = Font(bold = true))
        //writer.println(s"XFP LineItemName,Appnexus Campaign,XFP Imps,XFP Booked Imps,Appnexus Imps,Appnexus Booked Imps")
        sheetData += Row(style = headerStyle).withCellValues("XFP LineItemName", "XFP Start Date", "XFP End Date", "Appnexus Campaign", "XFP Imps", "XFP Booked Imps", "Appnexus Imps", "Appnexus Booked Imps", "Discrepancy %", "Discrepancy Number", "TB updated on Exchange")
        try {
          result._1 foreach {
            case (xfpCamp, xfpVal) => {
              result._2 foreach {
                case (appnxsCamp, appnxsVal) => {
                  breakable {
                    if (appnxsCamp.contains(xfpCamp)) {
                      if (xfpVal.impressionsDelivered != appnxsVal.deliveredImps) {
                        val discrepancyPerc: Double = (appnxsVal.deliveredImps - xfpVal.impressionsDelivered) * 1.0/appnxsVal.deliveredImps
                        var discrepancyNumber:Option[Double] = None
                        var toBeUpdatedOnExchange: Option[Double] = None
                        val lineItemName = xfpVal.lineItemName
                        if(!lineItemName.contains("^Mob") && !lineItemName.contains("^Tab") && !lineItemName.contains("^MOB") && !lineItemName.contains("^TAB")) {
                          discrepancyNumber = Some((xfpVal.bookedImps - xfpVal.impressionsDelivered) * (1 + discrepancyPerc))
                          toBeUpdatedOnExchange = Some(discrepancyNumber.get + appnxsVal.deliveredImps)
                        } else {
                          discrepancyNumber = Some(discrepancyPerc * xfpVal.bookedImps)
                          toBeUpdatedOnExchange =Some(discrepancyNumber.get + xfpVal.bookedImps)
                        }
                        sheetData += Row().withCellValues(xfpVal.lineItemName,xfpVal.startDate, xfpVal.endDate,appnxsVal.campaignName,xfpVal.impressionsDelivered,xfpVal.bookedImps,appnxsVal.deliveredImps,appnxsVal.lifeTimeBudgetImps, roundAt(2)(discrepancyPerc*100.0), discrepancyNumber.get.round, toBeUpdatedOnExchange.get.round)
                      }
                      break()
                    }
                  }
                }
              }
            }
          }
          val discrepancyReportSheet = Sheet(name = s"discrepancy_report").withRows(sheetData).withColumns(Column(index = 0, autoSized = true), Column(index = 1, autoSized = true), Column(index = 2, autoSized = true))
          val filePath = ServicesConfig.appnexusConfig("discrepancy.report.output.file.path")
          discrepancyReportSheet.saveAsXlsx(filePath)
          log.info("Report data exported...")
        } catch {
          case ex: Exception => log.error("Exception ", ex)
        }
    }

  }

  def roundAt(p: Int)(n: Double): Double = { val s = math pow (10, p); (math round n * s) / s }

  def readAppnexusData(): Future[TreeMap[String, AppnexusCampaign]] = {
    val appnexusDataPromise = Promise[TreeMap[String, AppnexusCampaign]]()
    Future {
      val appnexusReader = new BufferedReader(new FileReader(ServicesConfig.appnexusConfig("appnexus.output.file.path")))
      val appnexusData = try {
        val appnexusMap = new mutable.HashMap[String, AppnexusCampaign]
        var line: Option[String] = Option(appnexusReader.readLine())
        log.info("Reading appnexus file...")

        while (line != None) {
          //log.info("line = " + line)
          val appnexusData = line.getOrElse("").split("~")
          appnexusMap += (appnexusData(0) -> AppnexusCampaign(appnexusData(1).toLong, appnexusData(0), appnexusData(2).toLong, appnexusData(3).toLong, appnexusData(4).toLong))
          line = Option(appnexusReader.readLine())
        }
        appnexusDataPromise.success(TreeMap(appnexusMap.toArray: _*))
      } catch {
        case error: Exception => log.error("Error during file read")
          throw error
      } finally {
        appnexusReader.close()
      }
    }
    appnexusDataPromise.future
  }

  def readXfpData(): Future[TreeMap[String, DFPCampaign]] = {
    val xfpDataPromise = Promise[TreeMap[String, DFPCampaign]]()
    Future {
      val xfpReader = new BufferedReader(new FileReader(ServicesConfig.appnexusConfig("xfp.output.file.path")))
      val xfpData = try {
        val xfpMap = new mutable.HashMap[String, DFPCampaign]
        var line: Option[String] = Option(xfpReader.readLine())
        log.info("Reading xfp file...")
        while (line != None) {
          //log.info("line = " + line)
          val xfpData = line.getOrElse("").split("~")
          xfpMap += (xfpData(0) -> DFPCampaign(xfpData(1).toLong, xfpData(0), xfpData(2).toLong, xfpData(3).toLong, xfpData(4), xfpData(5)))
          line = Option(xfpReader.readLine())
        }
        xfpDataPromise.success(TreeMap(xfpMap.toArray: _*))
      } catch {
        case error: Exception => log.error("Error during file read")
          throw error
      } finally {
        xfpReader.close()
      }
    }
    xfpDataPromise.future
  }

}

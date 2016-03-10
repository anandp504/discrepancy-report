package com.collective.service

import java.io._
import java.text.SimpleDateFormat
import java.util.Calendar

import akka.actor.Actor
import com.collective.models.{DFPCampaign, AppnexusCampaign}
import com.collective.utils.{ServicesConfig, Logging}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{Days, DateTimeZone, DateTime}

//import com.norbitltd.spoiwo.model.CellStyle
//import com.norbitltd.spoiwo.model.Font
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
        val sheetData: ListBuffer[Row] = ListBuffer[Row]()
        val headerStyle = CellStyle(font = Font(bold = true))
        val dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd")
        val timezone = DateTimeZone.forID("America/New_York")
        val today = new DateTime(timezone)
        sheetData += Row(style = headerStyle).withCellValues("Agency Prefix", "XFP LineItemName", "XFP Start Date", "XFP End Date", "Appnexus Campaign ID", "Appnexus Campaign", "XFP Imps", "XFP Booked Imps", "Appnexus Delivered Imps", "Appnexus Booked Imps", "Appnexus Daily Cap", "Lifetime Pacing", "Discrepancy %", "Discrepancy Number", "TB updated on Exchange", "New Daily Cap")
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
                          toBeUpdatedOnExchange = Some(discrepancyNumber.get + xfpVal.bookedImps)
                        }
                        val xfpStartDate = dateFormat.withZone(DateTimeZone.forID("America/New_York")).parseDateTime(xfpVal.startDate)
                        val xfpEndDate = dateFormat.withZone(DateTimeZone.forID("America/New_York")).parseDateTime(xfpVal.endDate)
                        val daysInBetween = Days.daysBetween(today.toLocalDate, xfpEndDate.toLocalDate).getDays
                        val newDailyCap = if (daysInBetween != 0) ((toBeUpdatedOnExchange.get.round - appnxsVal.deliveredImps)/daysInBetween) * 1.03 else 0L// 3 % buffer for daily_budget_imps field
                        sheetData += Row().withCellValues(appnxsVal.agencyPrefix, xfpVal.lineItemName, dateFormat.print(xfpStartDate.toLocalDate.toDate.getTime),
                          dateFormat.print(xfpEndDate.toLocalDate.toDate.getTime), appnxsVal.campaignId, appnxsVal.campaignName, xfpVal.impressionsDelivered,
                          xfpVal.bookedImps,appnxsVal.deliveredImps,appnxsVal.lifeTimeBudgetImps,appnxsVal.dailyBudgetImps,appnxsVal.lifetimePacing,
                          roundAt(2)(discrepancyPerc*100.0), discrepancyNumber.get.round, toBeUpdatedOnExchange.get.round, newDailyCap.round)
                      }
                      break()
                    }
                  }
                }
              }
            }
          }
          //val discrepancyReportSheet = Sheet(name = s"discrepancy_report").withRows(sheetData).withColumns(Column(index = 0, autoSized = true), Column(index = 1, autoSized = true), Column(index = 2, autoSized = true))
          val discrepancyReportSheet = Sheet(name = s"discrepancy_report").withRows(sheetData)
          val sdf = new SimpleDateFormat("yyyyMMdd")
          val calendar = Calendar.getInstance()
          val filePath = s"${ServicesConfig.appnexusConfig("discrepancy.report.output.file.path")}-${sdf.format(calendar.getTime)}.xlsx"
          discrepancyReportSheet.saveAsXlsx(filePath)
          log.info("Report data exported...")
        } catch {
          case ex: Exception => log.error("Error when generating the discrepancy report ", ex)
            throw ex
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
          val appnexusData = line.getOrElse("").split("~")
          appnexusMap += (appnexusData(0) -> AppnexusCampaign(appnexusData(5), appnexusData(1).toLong, appnexusData(0), appnexusData(2).toLong, appnexusData(3).toLong, appnexusData(4).toLong, appnexusData(6).toBoolean))
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

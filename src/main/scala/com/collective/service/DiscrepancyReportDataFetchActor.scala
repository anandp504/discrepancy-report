package com.collective.service

import java.io.File

import akka.actor.{ActorSystem, Props, Actor}
import akka.util.Timeout
import com.collective.models.{DFPCampaign, AppnexusCampaign}
import com.collective.utils.{AppnexusFileWriterActor, Logging, ServicesConfig}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.duration._

import scala.util.{Failure, Success}


/**
 * Created by anand on 31/07/15.
 */
class DiscrepancyReportDataFetchActor extends Actor with Logging {

  implicit val system = ActorSystem("DiscrepancyReport")
  implicit val timeout = Timeout(50.minute)

  def receive = {
    case "FETCH_XFP_REPORT_DATA" => {
      val campaigns = fetchXfpData()
    }
    case "FETCH_APPNEXUS_REPORT_DATA" => {
      val campaigns = fetchAppnexusData()
    }
    case "UPDATE_APPNEXUS_CAMPAIGNS" => {
      updateAppnexusCampaigns()
    }
  }

  def fetchAppnexusData() = Future {

    implicit lazy val jsonFormats = org.json4s.DefaultFormats
    lazy val appnexusReportService = system.actorOf(Props[AppnexusReportService].withDispatcher("appnexus-fetch-dispatcher"), "appnexus-actor")
    lazy val appnexusFileWriterActor = system.actorOf(Props[AppnexusFileWriterActor])
    val agenciesPrefix = ServicesConfig.appnexusConfig("reach.agencies.prefix").split(",")
    val processingCount = ServicesConfig.appnexusConfig("campaign.processing.count").toInt
    new File(ServicesConfig.appnexusConfig("appnexus.output.file.path")).delete

    agenciesPrefix.map { agencyPrefix =>

      val appnexusCampaignsCount = (appnexusReportService ? AppnexusReportService.FetchAppnexusCampaignCount(agencyPrefix.trim)).mapTo[Int]
      appnexusCampaignsCount.map(count => {
        val callCounts: Int = count / processingCount
        for (a <- 0 to callCounts) {
          val appnexusCampaigns = (appnexusReportService ? AppnexusReportService.FetchAppnexusCampaigns(agencyPrefix.trim, a * processingCount, processingCount)).mapTo[List[AppnexusCampaign]]

          appnexusCampaigns.onComplete {
            case Success(campaigns) => {
              log.info("Retrieved " + campaigns.size + " campaigns for " + agencyPrefix)
              appnexusFileWriterActor ? AppnexusFileWriterActor.AppnexusFileData(campaigns)
            }
            case Failure(error) => log.error("Error in Appnexus Future ", error)
          }
        }
      })
    }
  }

  def fetchXfpData() = Future {
    lazy val googleDfpService = system.actorOf(Props[GoogleXFPService].withDispatcher("appnexus-fetch-dispatcher"), "xfp-actor")
    lazy val appnexusFileWriterActor = system.actorOf(Props[AppnexusFileWriterActor])

    val agenciesPrefix = ServicesConfig.appnexusConfig("reach.agencies.prefix").split(",")
    new File(ServicesConfig.appnexusConfig("xfp.output.file.path")).delete

    agenciesPrefix.map { agencyPrefix =>
      val xfpLineItems = (googleDfpService ? GoogleXFPService.XFPLineItems(agencyPrefix.trim)).mapTo[List[DFPCampaign]]
      xfpLineItems.onComplete {
        case Success(lineItems) =>
          log.info("Retrieved " + lineItems.size + " lineItems for " + agencyPrefix)
          appnexusFileWriterActor ? AppnexusFileWriterActor.XFPFileData(lineItems)
        case Failure(error) => log.error("Error in XFP Future ", error)
      }
    }
  }

  def updateAppnexusCampaigns() = Future {
    lazy val appnexusReportService = system.actorOf(Props[AppnexusReportService].withDispatcher("appnexus-fetch-dispatcher"), "appnexus-actor")
    val agenciesPrefix = ServicesConfig.appnexusConfig("reach.agencies.prefix").split(",")
    try {
      val discrepancyDataByAgency = (appnexusReportService ? "READ_DISCREPANCY_DATA").mapTo[mutable.HashMap[String, List[(Long, String, Long, Long, Boolean)]]]
      discrepancyDataByAgency.map {
        discrepancyData =>
          agenciesPrefix.map {
            agencyPrefix =>
              val agencyData = discrepancyData.getOrElse(agencyPrefix.trim, List[(Long, String, Long, Long, Boolean)]())
              log.info(s"Total number of campaigns to be updated for Agency $agencyPrefix = ${agencyData.size}")
              if (agencyData.size > 0) {
                for (data: (Long, String, Long, Long, Boolean) <- agencyData) {
                  val appnexusCampaignId = data._1
                  val computedBookedImps = data._3
                  val computedDailyCap = data._4
                  val lifetimePacing = data._5
                  if (computedDailyCap > 0) {
                    appnexusReportService ? AppnexusReportService.UpdateAppnexusCampaign(agencyPrefix, appnexusCampaignId, computedBookedImps, computedDailyCap, lifetimePacing)
                  }
                }
              }
          }
      }
    } catch {
      case ex: Exception => log.error("Error during Appnexus update ", ex)
        throw ex
    }
  }

}

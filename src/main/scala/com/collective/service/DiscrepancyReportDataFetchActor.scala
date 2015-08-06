package com.collective.service

import java.io.File

import akka.actor.{ActorSystem, Props, Actor}
import akka.util.Timeout
import com.collective.models.{DFPCampaign, AppnexusCampaign}
import com.collective.utils.{AppnexusFileWriterActor, Logging, ServicesConfig}
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

import scala.util.{Failure, Success}


/**
 * Created by anand on 31/07/15.
 */
class DiscrepancyReportDataFetchActor extends Actor with Logging {

  implicit val system = ActorSystem("DiscrepancyReport")
  implicit val timeout = Timeout(50.minute)

  def receive = {
    case "FETCH_REPORT_DATA" => {
      val campaigns = generateDiscrepancyReport()
      sender ! campaigns
    }
  }

  def generateDiscrepancyReport() = {

    val dataFetchComplete = for {
      appnexusFetchComplete <- fetchAppnexusData()
      _ = log.info("Started Appnexus Campaign Data Fetch...")
      xfpFetchComplete <- fetchXfpData()
      _ = log.info("Started XFP Lineitem Data Fetch...")
    } yield "done"
  }

  def fetchAppnexusData() = Future {

    implicit lazy val jsonFormats = org.json4s.DefaultFormats
    val appnexusReportService = system.actorOf(Props[AppnexusReportService])
    val appnexusFileWriterActor = system.actorOf(Props[AppnexusFileWriterActor])
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
    val googleDfpService = system.actorOf(Props[GoogleXFPService])
    val appnexusFileWriterActor = system.actorOf(Props[AppnexusFileWriterActor])

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

}

package com.collective.api

import akka.actor.{ActorSystem, Actor, Props}
import akka.util.Timeout
import com.collective.service.{AppnexusReportService, DiscrepancyReportDataFetchActor, GenerateReportActor}
import spray.routing._
import com.collective.utils.{Logging, ServicesConfig}

import scala.concurrent.duration._
import scala.language.postfixOps
import akka.pattern.ask

class CampaignFetcherActor extends Actor with CampaignFetcherService {

  implicitly[RoutingSettings](RoutingSettings.default(context))
  implicit val routingSettings = RoutingSettings.default(context)

  def actorRefFactory = context
  def receive = runRoute(segmentFetcherRoute)
}


trait CampaignFetcherService extends HttpService with Logging {

  implicit val timeout = Timeout(50 seconds)
  implicit val system = ActorSystem("service-actory-system")

  lazy val generateReportActor = system.actorOf(Props[GenerateReportActor])
  lazy val reportDataFetchActor = system.actorOf(Props[DiscrepancyReportDataFetchActor])
  lazy val appnexusReportService = system.actorOf(Props[AppnexusReportService].withDispatcher("appnexus-fetch-dispatcher"))

  val segmentFetcherRoute = {

      pathSingleSlash {
        complete("root")
      } ~
      path("discrepancy-report") {
        val appnexusCampaignsFile = ServicesConfig.appnexusConfig("appnexus.output.file.path")
        getFromFile(appnexusCampaignsFile)
      } ~
      path("fetch-xfp-data") {
        get {
          complete {
            reportDataFetchActor ? "FETCH_XFP_REPORT_DATA"
            "XFP Data fetch initiated..."
          }
        }
      } ~
      path("fetch-appnexus-data") {
        get {
          complete {
            reportDataFetchActor ? "FETCH_APPNEXUS_REPORT_DATA"
            "Appnexus Data fetch initiated..."
          }
        }
      } ~
      path("generate-discrepancy-report") {
        get {
          complete {
            generateReportActor ? "GENERATE_REPORT"
            "Discrepancy report will be generated and emailed"
          }
        }
      } ~
      path("update-appnexus") {
        get {
          complete {
            reportDataFetchActor ? "UPDATE_APPNEXUS_CAMPAIGNS"
            "Update appnexus initialized..."
          }
        }
      }
  }
}
package com.collective.api

import akka.actor.{ActorSystem, Actor, Props}
import akka.util.Timeout
import com.collective.service.GoogleXFPService.XFPLineItems
import com.collective.service.{DiscrepancyReportDataFetchActor, GenerateReportActor, GoogleXFPService}
import spray.routing._
import com.collective.utils.{Logging, ServicesConfig}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.pattern.ask
import spray.http.MediaTypes._

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

  val segmentFetcherRoute = {

      pathSingleSlash {
        complete("root")
      } ~
      path("discrepancy-report") {
        log.info("calling discrepancy report path....")
        val appnexusCampaignsFile = ServicesConfig.appnexusConfig("appnexus.output.file.path")
        getFromFile(appnexusCampaignsFile)
      } ~
      path("fetchdata") {
        get {
          log.info("calling discrepancy report data fetch path....")
          complete {
            reportDataFetchActor ? "FETCH_REPORT_DATA"
            "Discrepancy report data fetch initiated"
          }
        }
      } ~
      path("generate-discrepancy-report") {
        get {
          log.info("executing generate report...")
          complete {
            generateReportActor ? "GENERATE_REPORT"
            "Discrepancy report will be generated and emailed"
          }
        }
      }
  }
}
package com.collective.service

import java.io.PrintWriter
import akka.actor.{PoisonPill, ActorSystem, Props}
import akka.io.IO
import akka.util.Timeout
import com.collective.api.CampaignFetcherActor
import com.collective.models.AppnexusCampaign
import org.json4s.jackson.Serialization
import spray.can.Http
import com.collective.utils.{Logging, ServicesConfig}

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.ask
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Failure, Success}

object Boot extends App with Logging {

  implicit val system = ActorSystem("DiscrepancyReport")
  val segmentFetcherService = system.actorOf(Props[CampaignFetcherActor], "segment-fetcher-service")
  //val discrepancyReportActor = system.actorOf(Props[DiscrepancyReportDataFetchActor], "discrepancy-report-actor")
  implicit val timeout = Timeout(50.minute)
  IO(Http) ! Http.Bind(segmentFetcherService, interface = "localhost", port = 10080)

  val scheduler = system.scheduler
  val schedulerInterval = ServicesConfig.scheduledConfig("segment.fetcher.schedule").toInt
  implicit lazy val jsonFormats = org.json4s.DefaultFormats

  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run() = {
      log.info("shutting down system")
      IO(Http) ? Http.Unbind
      system.shutdown()
    }
  })

  //scheduler.schedule(60.minutes, schedulerInterval.minutes, discrepancyReportActor, "FETCH_REPORT_DATA")
}

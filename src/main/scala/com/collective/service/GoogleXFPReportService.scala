package com.collective.service

import java.util.concurrent.Executors
import akka.actor.Actor
import akka.pattern.pipe
import akka.util.Timeout
import com.collective.models.DFPCampaign
import com.collective.service.GoogleXFPService.XFPLineItems
import com.google.api.ads.dfp.axis.factory.DfpServices
import com.google.api.ads.dfp.axis.utils.v201411.{DateTimes, StatementBuilder}
import com.google.api.ads.dfp.axis.v201411._
import org.joda.time.{Duration, Instant}
import com.collective.utils.Logging

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Promise, Await, ExecutionContext, Future}
import scala.language.postfixOps

/**
 * Created by anand on 04/12/14.
 */
object GoogleXFPService {

  case class XFPLineItems(agencyNamePrefix: String)

  def dfpSession = {
    DfpSessionFactory.getDfpSession()
  }

}

class GoogleXFPService extends Actor with Logging {

  implicit val system = context.system
  system.dispatcher

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(50))
  implicit val timeout: Timeout = Timeout(30 minutes)

  def receive = {
    case XFPLineItems(agencyNamePrefix) => {
      val xfpSegments = getXFPLineItems(agencyNamePrefix)
      xfpSegments pipeTo sender
    }
  }

  def getXFPLineItems(agencyNamePrefix: String): Future[List[DFPCampaign]] = {
    val xfpLineItemsPromise = Promise[List[DFPCampaign]]()
    Future {
      val lineItems = getLineItems(agencyNamePrefix)
      xfpLineItemsPromise.success(lineItems)
    }
    xfpLineItemsPromise.future
  }

  def getLineItems(agencyNamePrefix: String): List[DFPCampaign] = {
    //log.info("Fetching Google XFP LineItems...")
    val dfpServices: DfpServices = new DfpServices()
    val googleXfpLineItems = ListBuffer[DFPCampaign]()
    var resultSetSize = 0
    //val dfpSession = DfpSessionFactory.getDfpSession()

    try {
      val lineItemService: LineItemServiceInterface = dfpServices.get(GoogleXFPService.dfpSession, classOf[LineItemServiceInterface])
      val lineItemStmtBuilder: StatementBuilder = new StatementBuilder()
      lineItemStmtBuilder.where( s"""endDateTime >= :endDate AND (name like '$agencyNamePrefix%App%' OR name like '$agencyNamePrefix%APP%' OR name like '$agencyNamePrefix%^MOB%' OR name like '$agencyNamePrefix%^TAB%') AND status = 'DELIVERING'""").limit(1500)
      //lineItemStmtBuilder.where( s"""endDateTime >= :endDate AND (name like 'RE AM%App%' OR name like 'RE AM%APP%') AND status = 'DELIVERING'""").limit(1500)
        .withBindVariableValue("endDate", DateTimes.toDateTime(Instant.now().minus(Duration.standardDays(1L)), "America/New_York"))

      do {
        val lineItemPage: Option[LineItemPage] = Option(lineItemService.getLineItemsByStatement(lineItemStmtBuilder.toStatement))
        lineItemPage match {
          case Some(lineItemPage) => {
            resultSetSize = lineItemPage.getTotalResultSetSize
            //log.info("Lineitem size = " + resultSetSize)
            if (resultSetSize > 0) {
              googleXfpLineItems ++= getLineItemDetails(lineItemPage.getResults)
            }
          }
          case None => log.debug("LineItem ResultSet empty")
        }
        lineItemStmtBuilder.increaseOffsetBy(1500)
      } while (lineItemStmtBuilder.getOffset < resultSetSize)
      //log.info("result set size = " + googleXfpLineItems.toList.size)
    } catch {
      case ex: Exception => log.error("Exception when fetch XFP data for agency " + agencyNamePrefix, ex)
        throw ex;
    }
    googleXfpLineItems.toList
  }

  def getLineItemDetails(lineItems: Array[LineItem]): List[DFPCampaign] = {
    val xfpLineItems = ListBuffer[DFPCampaign]()
    for(lineItem <- lineItems) {
      val stats: Stats = lineItem.getStats
      if(stats != null) {
        //log.info(DFPCampaign(lineItem.getId, lineItem.getName, stats.getImpressionsDelivered).toString)
        xfpLineItems += DFPCampaign(lineItem.getId, lineItem.getName, stats.getImpressionsDelivered, lineItem.getContractedUnitsBought)
      }
    }
    xfpLineItems.toList
  }

}

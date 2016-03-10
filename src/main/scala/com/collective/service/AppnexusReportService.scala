package com.collective.service

import java.io.{FileInputStream, File}
import java.text.SimpleDateFormat
import java.util.Calendar
import akka.actor.Actor
import akka.pattern.pipe
import akka.util.Timeout
import com.collective.models.{AppnexusCampaignData, AppnexusCampaign}
import com.collective.service.AppnexusReportService.{UpdateAppnexusCampaign, FetchAppnexusCampaignCount, FetchAppnexusCampaigns}
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.{XSSFSheet, XSSFWorkbook}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import spray.http._
import com.collective.utils._

import scala.collection.immutable.StringOps
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.concurrent.{Promise, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * Created by anand on 03/11/14.
 */
object AppnexusReportService {

  private var authToken = None: Option[String]
  private val AppnexusBaseUrl = ServicesConfig.appnexusConfig("appnexus.host")
  private val AppnexusUserName = ServicesConfig.appnexusConfig("appnexus.username")
  private val AppnexusPassword = ServicesConfig.appnexusConfig("appnexus.password")
  private val AuthJsonPayload = new StringOps("""{"auth": {"username" : "%s", "password" : "%s"}}""").format(AppnexusUserName, AppnexusPassword)

  case class FetchAppnexusCampaigns(agencyNamePrefix: String, startElement: Int, numberOfElements: Int)
  case class FetchAppnexusCampaignCount(agencyNamePrefix: String)
  case class UpdateAppnexusCampaign(agencyPrefix: String, campaignId: Long, computedBookedImps: Long, computedDailyCap: Long, lifetimePacing: Boolean)
}

class AppnexusReportService extends Actor with Logging {

  implicit lazy val jsonFormats = org.json4s.DefaultFormats
  implicit val system = context.system

  implicit val ec = system.dispatchers.lookup("appnexus-fetch-dispatcher")
  implicit val timeout: Timeout = Timeout(30 minutes)

  def receive = {
    case FetchAppnexusCampaigns(agencyNamePrefix, startElement, numberOfElements) => {
      val campaigns = fetchAppnexusCampaign(agencyNamePrefix, startElement, numberOfElements)
      campaigns pipeTo sender
    }
    case FetchAppnexusCampaignCount(agencyNamePrefix) => {
      val campaignCount = fetchAppnexusCampaignCount(agencyNamePrefix)
      campaignCount pipeTo sender
    }
    case "READ_DISCREPANCY_DATA" => {
      val discrepancyData = readComputedDiscrepancyData()
      discrepancyData pipeTo sender
    }
    case UpdateAppnexusCampaign(agencyPrefix, campaignId, computedBookedImps, computedDailyCap, lifetimePacing) => {
      updateAppnexusCampaign(agencyPrefix, campaignId, computedBookedImps, lifetimePacing, Some(computedDailyCap))
    }
  }

  def fetchAppnexusCampaign(searchAgencyTerm: String, startElements: Int, numberOfElements: Int): Future[List[AppnexusCampaign]] = {
    val campaignsPromise = Promise[List[AppnexusCampaign]]()
    Future {
      try {
        val campaignHttpResponse = getCampaigns(searchAgencyTerm, startElements, numberOfElements)
        campaignHttpResponse.map {
          response =>
            campaignsPromise.success(JsonUtils.parseCampaigns(searchAgencyTerm, response.entity.asString))
        }
        //campaignsPromise.success(JsonUtils.parseCampaigns(campaignHttpResponse.entity.asString))
      } catch {
        case error: Exception => log.error(s"Error when fetching Appnexus campaign data for Agency $searchAgencyTerm ", error)
          throw error
      }
    }
    campaignsPromise.future
  }

  def fetchAppnexusCampaignCount(searchAgencyTerm: String): Future[Int] = {
    val campaignsPromise = Promise[Int]()
    Future {
      try {
        val campaignHttpResponse = getCampaigns(searchAgencyTerm, 0, 1)
        campaignHttpResponse.map {
          response =>
            campaignsPromise.success(JsonUtils.parseCampaignCount(response.entity.asString).getOrElse(0))
        }
        //campaignsPromise.success(JsonUtils.parseCampaignCount(campaignHttpResponse.entity.asString).getOrElse(0))
      } catch {
        case error: Exception => log.error(s"Error when fetching Appnexus campaign count for Agency $searchAgencyTerm ", error)
          throw error
      }
    }
    campaignsPromise.future
  }

  /**
   * Read the generated discrepancy report with the new computed lifetime budget impressions and daily cap.
   * Also read if the campaign has lifetime_pacing enabled
   * @return
   */
  def readComputedDiscrepancyData(): Future[mutable.HashMap[String, List[AppnexusCampaignData]]] = Future {
    import util.control.Breaks._
    val sdf = new SimpleDateFormat("yyyyMMdd")
    val calendar = Calendar.getInstance()
    val formatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
    try {
      val filePath = s"${ServicesConfig.appnexusConfig("discrepancy.report.output.file.path")}-${sdf.format(calendar.getTime)}.xlsx"
      val fis: FileInputStream = new FileInputStream(new File(filePath))
      val workbook: XSSFWorkbook = new XSSFWorkbook(fis)
      val sheet: XSSFSheet = workbook.getSheet("discrepancy_report")
      val rowIterator = sheet.iterator()
      val discrepancyDataByAgency = new mutable.HashMap[String, List[AppnexusCampaignData]]
      var agencyPrefix: String = ""
      val agencyData = new ListBuffer[AppnexusCampaignData]()
      while (rowIterator.hasNext) {
        val row: Row = rowIterator.next()
        breakable {
          if (row.getRowNum == 0) {
            break()
          } else {
            if (agencyPrefix != row.getCell(0).toString) {
              if (agencyPrefix != "") {
                discrepancyDataByAgency += (agencyPrefix.trim -> agencyData.toList)
                agencyData.clear()
              }
              agencyPrefix = row.getCell(0).toString
            }
            val startDate = formatter.parseDateTime(row.getCell(2).toString)
            val endDate = formatter.parseDateTime(row.getCell(3).toString)
            val appnexusCampId = row.getCell(4).getNumericCellValue.toLong
            val appnexusCampName = row.getCell(5).toString
            val currentLifeTimeImps = row.getCell(9).getNumericCellValue.toLong
            val newLifeTimeImps = row.getCell(14).getNumericCellValue.toLong
            val newDailyCap = row.getCell(15).getNumericCellValue.toLong
            val lifetimePacing = row.getCell(11).getBooleanCellValue
            val currentDailyCap = row.getCell(10).getNumericCellValue.toLong
            agencyData += AppnexusCampaignData(appnexusCampId, appnexusCampName, currentLifeTimeImps, newLifeTimeImps, currentDailyCap, newDailyCap, lifetimePacing, startDate, endDate)
          }
        }
      }
      /* This is needed to add the reuslts of last agency to the HashMap */
      discrepancyDataByAgency += (agencyPrefix.trim -> agencyData.toList)
      discrepancyDataByAgency
    } catch {
      case ex: Exception => log.error("Exception = ", ex)
      new mutable.HashMap[String, List[AppnexusCampaignData]]()
    }
  }

  def updateAppnexusCampaign(agencyPrefix: String, campaignId: Long, computedBookedImps: Long, lifetimePacing: Boolean, computedDailyCap: Option[Long] = None) = {
    try {
      val updateCampaignFuture =  updateCampaign(agencyPrefix, campaignId, computedBookedImps, lifetimePacing, computedDailyCap)
      updateCampaignFuture onComplete {
        case Success(response) => log.debug(s"Appnexus Campaign ID $campaignId updated successfully with Lifetime Budget Imps = $computedBookedImps")
        case Failure(error) => log.error(s"Appnexus Campaign ID $campaignId updated failed. Error: " + error)
      }
    } catch {
      case ex: Exception => log.error("Exception = ", ex)
    }
  }

  /**
   * This method updates each Appnexus Campaign with the new computed Lifetime Budget Impressions
   * @param campaignId
   * @param lifeTimeImps
   * @param dailyCap
   * @return Future[HttpResponse]
   */
  def updateCampaign(agencyPrefix: String, campaignId: Long, lifeTimeImps: Long, lifetimePacing: Boolean, dailyCap: Option[Long]) : Future[HttpResponse] = {
    val queryParams = Map("id" -> campaignId.toString)
    val jsonPayLoad = if (agencyPrefix == "RE VA" || lifetimePacing) {
      new StringOps( """{"campaign": {"lifetime_budget_imps": %s}}""").format(lifeTimeImps)
    } else {
      new StringOps( """{"campaign": {"lifetime_budget_imps": %s, "daily_budget_imps": %s}}""").format(lifeTimeImps, dailyCap.get)
    }
    val campaignHttpResponse = HttpUtils.put("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))), jsonPayLoad)

    campaignHttpResponse.map {
      response =>
        val errorId = JsonUtils.parseErrorId(response.entity.asString)
        if (errorId == Some("NOAUTH")) {
          auth().map {
            auth =>
              HttpUtils.put("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))), jsonPayLoad)
          }.flatMap(r => r)
        }
        else {
          log.debug("Auth Token was valid. No need to authenticate again...")
          Future(response)
        }
    }.flatMap(r => r)
  }

  /**
   * This method authenticates (in case an auth token has expired or has not been set) and sets the
   * auth token to be used for subsequent Appnexus requests
   */
  private def auth(): Future[Boolean] = {
    val httpResponse = HttpUtils.post("/auth", AppnexusReportService.AuthJsonPayload)
    //setAuthToken(httpResponse.entity.asString)
    val authPromise = Promise[Boolean]
    httpResponse.map {
      response =>
      setAuthToken(response.entity.asString)
      authPromise.success(true)
    }
    authPromise.future
  }

  /**
   * Fetches all the active campaigns for a given advertiser_id
   * @param lineItemId
   * @return HttpResponse
   */
  private def getCampaigns(lineItemId: Int): Future[HttpResponse] = {

    val queryParams = Map("line_item_id" -> lineItemId.toString, "state" -> "active", "stats" -> "true", "interval" -> "lifetime")
    val campaignHttpResponse = HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
    /*
    val errorId = JsonUtils.parseErrorId(campaignHttpResponse.entity.asString)
    errorId match {
      case Some("NOAUTH") => {
        auth()
        campaignHttpResponse = HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
      }
      case _ => log.debug("Auth Token was valid. No need to authenticate again...")
    }
    log.debug("Campaign data fetch complete for AdvertiserID " + lineItemId)
    campaignHttpResponse
    */
    campaignHttpResponse.map {
      response =>
        val errorId = JsonUtils.parseErrorId(response.entity.asString)
        if (errorId == Some("NOAUTH")) {
          auth().map {
            auth =>
              HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
          }.flatMap(r => r)
        }
        else {
          log.debug("Auth Token was valid. No need to authenticate again...")
          Future(response)
        }
      //}
      //log.debug("Campaign data fetch complete for AdvertiserID " + lineItemId)
      //campaignHttpResponse
    }.flatMap(r => r)
  }

  /**
   * Fetches all the active campaigns for a given advertiser_id
   * @param searchAgencyTerm
   * @return HttpResponse
   */
  private def getCampaigns(searchAgencyTerm: String, startElement: Int, numberOfElements: Int): Future[HttpResponse] = {

    val queryParams = Map("search" -> searchAgencyTerm, "state" -> "active", "stats" -> "true", "interval" -> "lifetime", "start_element" -> startElement.toString, "num_elements" -> numberOfElements.toString)
    val campaignHttpResponse = HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
    /*
    val errorId = JsonUtils.parseErrorId(campaignHttpResponse.entity.asString)
    errorId match {
      case Some("NOAUTH") => {
        auth()
        campaignHttpResponse = HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
      }
      case _ => log.debug("Auth Token was valid. No need to authenticate again...")
    }
    log.debug("Campaign data fetch complete for AdvertiserID " + searchAgencyTerm)
    campaignHttpResponse
    */

    campaignHttpResponse.map {
      response =>
        val errorId = JsonUtils.parseErrorId(response.entity.asString)
        if (errorId == Some("NOAUTH")) {
          auth().map {
            auth =>
              HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
          }.flatMap(r => r)
        }
        else {
          log.debug("Auth Token was valid. No need to authenticate again...")
          Future(response)
        }
      //}
      //log.debug("Campaign data fetch complete for AdvertiserID " + lineItemId)
      //campaignHttpResponse
    }.flatMap(r => r)
  }

  /**
   * Sets the Auth Token to be reused for subsequent requests to Appnexus services
   * @param response - HttpResponse String from Appnexus Authentication request
   */
  private def setAuthToken(response: String) = {
    val parsedResponse: JValue = parse(response)
    AppnexusReportService.authToken = (parsedResponse \ "response" \ "token").extractOpt[String]
  }

}



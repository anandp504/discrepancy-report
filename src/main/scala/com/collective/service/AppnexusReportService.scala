package com.collective.service

import java.io.File
import java.util.concurrent.Executors
import akka.actor.{Props, Actor}
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import com.collective.models.AppnexusCampaign
import com.collective.service.AppnexusReportService.{FetchAppnexusCampaignCount, FetchAppnexusCampaigns}
import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.jackson.JsonMethods._
import spray.http._
import com.collective.utils._

import scala.collection.immutable.StringOps
import scala.concurrent.duration._
import scala.concurrent.{Promise, ExecutionContext, Future}
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
  }

  def fetchAppnexusCampaign(searchAgencyTerm: String, startElements: Int, numberOfElements: Int): Future[List[AppnexusCampaign]] = {
    val campaignsPromise = Promise[List[AppnexusCampaign]]()
    Future {
      try {
        val campaignHttpResponse = getCampaigns(searchAgencyTerm, startElements, numberOfElements)
        campaignsPromise.success(JsonUtils.parseCampaigns(campaignHttpResponse.entity.asString))
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
        campaignsPromise.success(JsonUtils.parseCampaignCount(campaignHttpResponse.entity.asString).getOrElse(0))
      } catch {
        case error: Exception => log.error(s"Error when fetching Appnexus campaign count for Agency $searchAgencyTerm ", error)
          throw error
      }
    }
    campaignsPromise.future
  }

  /**
   * This method authenticates (in case an auth token has expired or has not been set) and sets the
   * auth token to be used for subsequent Appnexus requests
   */
  private def auth() = {
    val httpResponse = HttpUtils.post("/auth", AppnexusReportService.AuthJsonPayload)
    setAuthToken(httpResponse.entity.asString)
  }

  /**
   * Fetches all the active campaigns for a given advertiser_id
   * @param lineItemId
   * @return HttpResponse
   */
  private def getCampaigns(lineItemId: Int): HttpResponse = {

    val queryParams = Map("line_item_id" -> lineItemId.toString, "state" -> "active", "stats" -> "true", "interval" -> "lifetime")
    var campaignHttpResponse = HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
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
  }

  /**
   * Fetches all the active campaigns for a given advertiser_id
   * @param searchAgencyTerm
   * @return HttpResponse
   */
  private def getCampaigns(searchAgencyTerm: String, startElement: Int, numberOfElements: Int): HttpResponse = {

    val queryParams = Map("search" -> searchAgencyTerm, "state" -> "active", "stats" -> "true", "interval" -> "lifetime", "start_element" -> startElement.toString, "num_elements" -> numberOfElements.toString)
    var campaignHttpResponse = HttpUtils.get("/campaign", queryParams, List(HttpHeaders.RawHeader("Authorization", AppnexusReportService.authToken.getOrElse(None.toString))))
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



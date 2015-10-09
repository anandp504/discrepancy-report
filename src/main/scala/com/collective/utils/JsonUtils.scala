package com.collective.utils

import com.collective.models.AppnexusCampaign
import com.fasterxml.jackson.databind.JsonMappingException
import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods._

/**
 * Created by anand on 12/11/14.
 */
object JsonUtils extends Logging {

  implicit lazy val jsonFormats = org.json4s.DefaultFormats
  /**
   * parse the error_id from Appnexus JSON response
   * @param response - HttpResponse string from any Appnexus service HttpRequest
   * @return Option[String] - Returns an error_id if found in the HttpResponse, else returns None
   */
  def parseErrorId(response: String): Option[String] = {
    val parsedResponse: JValue = parse(response)
    (parsedResponse \ "response" \ "error_id").extractOpt[String]
  }

  /**
   *
   * @param response - HttpResponse string from Appnexus advertiser service HttpRequest
   * @return List[BigInt] - Returns a List of Advertiser IDs from the Appnexus HttpResponse
   */
  def parseAdvertiserIds(response: String, nodeName: String): List[BigInt] = {
    try{
      val parsedResponse = parse(response)
      val searchNode = compact(render(parsedResponse \ "response" \ s"$nodeName"))
      val idList = for {
        JObject(nodes) <- parse(searchNode)
        JField("id", JInt(id)) <- nodes
      } yield id
      idList
    } catch {
        case jsonMapex: JsonMappingException => {
          log.error("Parsing nodes for ids failed: ", jsonMapex)
          List()
        }
    }
  }

  def parseCampaigns(agencyPrefix: String, response: String): List[AppnexusCampaign] = {
    try {
      val parsedResponse = parse(response)
      for {
        JObject(campaigns) <- parsedResponse
        JField("id", JInt(campaignId)) <- campaigns
        JField("start_date", JString(startDate)) <- campaigns
        JField("end_date", JString(endDate)) <- campaigns
        JField("lifetime_budget_imps", JInt(lifetimeBudgetImps)) <- campaigns
        JField("lifetime_pacing", JBool(lifetimePacing)) <- campaigns
        JField("daily_budget_imps", JInt(dailyBudgetImps)) <- campaigns
        JField("name", JString(name)) <- campaigns
        JField("stats", JObject(stats)) <- campaigns
        imps = for {
          JField("imps", JInt(imps)) <- stats
        } yield imps
        if stats != null
      } yield AppnexusCampaign(agencyPrefix, campaignId.toLong, name, imps(0).toLong, lifetimeBudgetImps.toLong, dailyBudgetImps.toLong, lifetimePacing)
    } catch {
      case ex: JsonMappingException => {
        log.error("Parsing campaign data failed: ", ex)
        List()
      }
    }
  }

  def parseCampaignCount(response: String): Option[Int] = {
    try {
      val parsedResponse = parse(response)
      (parsedResponse \ "response" \ "count").extractOpt[Int]
    } catch {
      case ex: JsonMappingException => {
        log.error("Parsing campaign count data failed: ", ex)
        Some(0)
      }
    }
  }

}

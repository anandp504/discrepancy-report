package com.collective.models

import org.joda.time.DateTime

/**
 * Created by anand on 31/07/15.
 */
case class AppnexusCampaign(agencyPrefix: String, campaignId: Long, campaignName: String, deliveredImps: Long, lifeTimeBudgetImps: Long, dailyBudgetImps: Long, lifetimePacing: Boolean) extends Ordered[AppnexusCampaign] {

  override def toString: String = {
    s"$campaignName~$campaignId~$deliveredImps~$lifeTimeBudgetImps~$dailyBudgetImps~$agencyPrefix~$lifetimePacing"
  }

  override def compare(that: AppnexusCampaign): Int = {
    this.campaignName compare that.campaignName
  }
}

case class DFPCampaign(lineItemId: Long, lineItemName: String, impressionsDelivered: Long, bookedImps: Long, startDate: String, endDate: String) extends Ordered[DFPCampaign] {
  override def toString: String = {
    s"$lineItemName~$lineItemId~$impressionsDelivered~$bookedImps~$startDate~$endDate"
  }

  override def compare(that: DFPCampaign): Int = {
    this.lineItemName compare that.lineItemName
  }
}

case class DiscrepancyReportOutput(lineItemName: String, xfpDeliveredImps: Long, xfpBookedImps: Long, appnexusCampaign: String, apnxsDeliveredImps: Long, anxsBookedImps: Long)

case class AppnexusCampaignData(campaignId: Long, campaignName: String, currentLifeTimeImps: Long, newLifeTimeimps: Long, currentDailyCap: Long, newDailyCap: Long, lifetimePacing: Boolean, startDate: DateTime, endDate: DateTime) {
  override def toString: String = {
    s"$campaignId,$campaignName,$currentLifeTimeImps,$newLifeTimeimps,$currentDailyCap,$newDailyCap,$lifetimePacing,$startDate,$endDate"
  }
}

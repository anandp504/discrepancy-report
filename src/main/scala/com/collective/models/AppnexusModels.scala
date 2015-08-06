package com.collective.models

/**
 * Created by anand on 31/07/15.
 */
case class AppnexusCampaign(campaignId: Long, campaignName: String, deliveredImps: Long, lifeTimeBudgetImps: Long, dailyBudgetImps: Long) extends Ordered[AppnexusCampaign] {

  override def toString: String = {
    s"$campaignName~$campaignId~$deliveredImps~$lifeTimeBudgetImps~$dailyBudgetImps"
  }

  override def compare(that: AppnexusCampaign): Int = {
    this.campaignName compare that.campaignName
  }
}

case class DFPCampaign(lineItemId: Long, lineItemName: String, impressionsDelivered: Long, bookedImps: Long) extends Ordered[DFPCampaign] {
  override def toString: String = {
    s"$lineItemName~$lineItemId~$impressionsDelivered~$bookedImps"
  }

  override def compare(that: DFPCampaign): Int = {
    this.lineItemName compare that.lineItemName
  }
}

case class DiscrepancyReportOutput(lineItemName: String, xfpDeliveredImps: Long, xfpBookedImps: Long, appnexusCampaign: String, apnxsDeliveredImps: Long, anxsBookedImps: Long)

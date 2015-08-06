package com.collective.utils

import java.util.{HashMap => JHashMap}

import com.typesafe.config.ConfigFactory

/**
 * Created by anand on 03/11/14.
 */
object ServicesConfig {

  def appnexusConfig: Map[String, String] = {
    appnexusConnConfig.getOrElse("appnexus", Map.empty)
  }

  def scheduledConfig: Map[String, String] = {
    appnexusConnConfig.getOrElse("scheduler", Map.empty)
  }

  private[this] final lazy val appnexusConnConfig: Map[String, Map[String, String]] = {
    import scala.collection.JavaConversions._
    ConfigFactory.load("appnexus-connection.conf").root.unwrapped.asInstanceOf[JHashMap[String, JHashMap[String, String]]].toMap.mapValues(_.toMap[String, String])
  }
}

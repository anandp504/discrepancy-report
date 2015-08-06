package com.collective.utils

import org.slf4j.{Logger, LoggerFactory}

/**
 * Created by anand on 03/11/14.
 */
trait Logging {
  lazy val log: Logger = LoggerFactory.getLogger(getClass.getName)
}

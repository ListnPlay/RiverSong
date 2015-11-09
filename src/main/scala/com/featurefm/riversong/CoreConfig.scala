package com.featurefm.riversong

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by ymeymann on 03/07/15.
 */
trait CoreConfig {

  /**
   * Use this method to build up the configuration to use with this system
   * @param config
   * @return
   */
  def getConfig(config: Option[Config] = None): Config = {

    val sysConfig = System.getProperty("config.file") match {
      // If we were not passed a Config then check to see if a config file
      // exists within a conf directory under the application context
      case null if new File("conf/application.conf").exists => ConfigFactory.load("conf/application.conf")
      // Load the default
      case null => ConfigFactory.load()
      // If there is a system property for the file then use that
      case f => ConfigFactory.parseFile(new File(f))
    }

    (config match {
      case Some(conf) => conf.withFallback(sysConfig)
      case None => sysConfig
    }).withFallback(ConfigFactory.load()).resolve()
  }

}

object CoreConfig extends CoreConfig

package com.featurefm.riversong

/**
 * Created by ymeymann on 03/07/15.
 */
trait Configurable {
  lazy val config =  CoreConfig.getConfig()
}

package com.featurefm.riversong

import akka.Done

import scala.concurrent.Future

/**
  * Created by yardena on 4/25/16.
  */
trait InitBeforeUse {
  def initialize(): Future[Done]
}

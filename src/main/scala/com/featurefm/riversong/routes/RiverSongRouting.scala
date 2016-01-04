package com.featurefm.riversong.routes

/**
 * Created by yardena on 1/4/16.
 */
trait RiverSongRouting {
  def routes: akka.http.scaladsl.server.Route
}

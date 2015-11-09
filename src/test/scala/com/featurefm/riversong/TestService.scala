package com.featurefm.riversong

import akka.http.scaladsl.server.Route

/**
 * Created by yardena on 11/9/15.
 */
object TestService extends MainService {
  override def assembly: ServiceAssembly = new ServiceAssembly() {
    override def routes: Route = lifecycle.routes
  }
}

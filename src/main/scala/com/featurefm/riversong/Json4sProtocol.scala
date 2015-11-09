package com.featurefm.riversong

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._

/**
 * Created by yardena on 9/20/15.
 */
trait Json4sProtocol extends Json4sSupport {
  implicit val serialization = jackson.Serialization
  implicit def json4sJacksonFormats: Formats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
}

object Json4sProtocol extends Json4sProtocol
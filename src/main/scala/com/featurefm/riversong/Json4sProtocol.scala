package com.featurefm.riversong

import org.json4s.{DefaultFormats, Formats}
import spray.httpx.Json4sJacksonSupport

/**
 * Created by yardena on 9/20/15.
 */
object Json4sProtocol extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
}

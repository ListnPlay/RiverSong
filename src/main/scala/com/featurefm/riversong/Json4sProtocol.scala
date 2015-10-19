package com.featurefm.riversong

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

/**
 * Created by yardena on 9/20/15.
 */
object Json4sProtocol extends Json4sSupport {
  implicit def json4sJacksonFormats: Formats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all
}

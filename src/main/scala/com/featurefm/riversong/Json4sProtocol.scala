package com.featurefm.riversong

import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s._

/**
 * Created by yardena on 9/20/15.
 */
trait Json4sProtocol extends Json4sSupport {
  implicit val serialization = jackson.Serialization
  def frameworkFormats: Formats = DefaultFormats ++ org.json4s.ext.JodaTimeSerializers.all + org.json4s.ext.UUIDSerializer
  implicit val json4sJacksonFormats: Formats = frameworkFormats
}

object Json4sProtocol extends Json4sProtocol
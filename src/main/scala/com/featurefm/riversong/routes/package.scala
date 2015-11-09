package com.featurefm.riversong

import org.json4s.JsonAST
import org.json4s.JsonAST.JValue
import org.json4s.JsonDSL._

/**
 * Created by yardena on 11/9/15.
 */
package object routes {

  private[routes] def matchAny(num: Any): JValue = {
    try {
      (num: Any) match {
        case z: Boolean => boolean2jvalue(z)
        case b: Byte => int2jvalue(b.toInt)
        case c: Char => int2jvalue(c.toInt)
        case s: Short => int2jvalue(s.toInt)
        case i: Int => int2jvalue(i)
        case j: Long => long2jvalue(j)
        case f: Float => float2jvalue(f)
        case d: Double => bigdecimal2jvalue(d)
        case st: String => string2jvalue(st)
        case r: AnyRef => JsonAST.JNull
      }
    } catch {
      case e: Throwable =>
        string2jvalue("Error evaluating the value: " + e.getMessage)
    }
  }


}

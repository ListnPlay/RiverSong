package com.featurefm.riversong.money

import org.joda.money.Money
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JDouble, JField, JObject, JString}
import com.github.nscala_money.money.Imports._

/**
  * Created by yardena on 11/25/16.
  */
class MoneySerializer extends CustomSerializer[Money](format => (
  {
    case JString(s) => s.toMoney
  },
  {
    case x: Money => JString(x.toString)
  }
  )
)

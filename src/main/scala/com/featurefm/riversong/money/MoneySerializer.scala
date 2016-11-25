package com.featurefm.riversong.money

import com.github.nscala_money.money.Imports._
import org.json4s.JsonAST._
import org.json4s.CustomSerializer

/**
  * Created by yardena on 11/25/16.
  */
class MoneySerializer extends CustomSerializer[MoneyAmount](format => (
  {
    case JObject(JField("visual", JDouble(v)) :: JField("currency", JString(c)) :: JField("usd", JDouble(d)) :: Nil) =>
      MoneyAmount(v, c, d)
  },
  {
    case x: MoneyAmount =>
      JObject(
        JField("visual",   JDouble(x.amount.getAmount.doubleValue())) ::
        JField("currency", JString(x.amount.currencyUnit.toString)) ::
        JField("usd",      JDouble(x.usd_amount.getAmount.doubleValue())) ::
        Nil
      )
  }

  )
)
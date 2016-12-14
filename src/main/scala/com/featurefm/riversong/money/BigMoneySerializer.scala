package com.featurefm.riversong.money

import org.joda.money.{BigMoney, Money}
import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

/**
  * Created by yardena on 12/5/16.
  */
class BigMoneySerializer extends CustomSerializer[BigMoney](format =>
  ({ case JString(s) => BigMoney.parse(s) }, { case x: BigMoney => JString(x.toString)})
){

}

package com.featurefm.riversong.money

import org.joda.money.Money
import com.github.nscala_money.money.Imports._
import java.math.{BigDecimal, MathContext, RoundingMode}

/**
  * Created by yardena on 11/24/16.
  */
case class MoneyAmount(amount: Money, usd_amount: Money) {

  def doubleAmount: Double = amount.getAmount.doubleValue()

  def doubleUSDAmount: Double = usd_amount.getAmount.doubleValue()
  
  def + (x: MoneyAmount): MoneyAmount = copy(amount + x.amount, usd_amount + x.usd_amount)
  
  def - (x: MoneyAmount): MoneyAmount = copy(amount - x.amount, usd_amount - x.usd_amount)
  
  def usdConversionRate: BigDecimal = amount.getAmount.divide(usd_amount.getAmount)

  def addAmount(d: Double): MoneyAmount =
    this + MoneyAmount.amountWithMultiplier(d, amount.currencyUnit, usdConversionRate.doubleValue())

  def subtractAmount(d: Double): MoneyAmount =
    this - MoneyAmount.amountWithMultiplier(d, amount.currencyUnit, usdConversionRate.doubleValue())

  def addUSDAmount(d: Double): MoneyAmount =
    this + MoneyAmount(Money.of(amount.currencyUnit, d / usdConversionRate.doubleValue(), RoundingMode.HALF_UP), MoneyAmount.usd(d))

  def subtractUSDAmount(d: Double): MoneyAmount =
    this - MoneyAmount(Money.of(amount.currencyUnit, d / usdConversionRate.doubleValue(), RoundingMode.HALF_UP), MoneyAmount.usd(d))

}

object MoneyAmount {
  
  val USD = "USD".toCurrency

  def money(d: Double, currency: String): Money = money(d, currency.toCurrency)

  def money(d: Double, currency: CurrencyUnit): Money = Money.of(
    currency,
    new BigDecimal(d, MathContext.UNLIMITED),
    RoundingMode.HALF_UP
  )

  def usd(d: Double): Money = money(d, USD)

  def apply(a: Double, currency: String, d: Double): MoneyAmount = MoneyAmount(money(a, currency), usd(d))
  
  def amountWithMultiplier(amt: Double, currency: CurrencyUnit, usdConversionMultiplier: Double): MoneyAmount =
    moneyWithMultiplier(money(amt, currency), usdConversionMultiplier)

  def moneyWithMultiplier(amount: Money, usdConversionMultiplier: Double): MoneyAmount = MoneyAmount(
    amount, amount.convertedTo(USD, new BigDecimal(usdConversionMultiplier, MathContext.UNLIMITED), RoundingMode.HALF_UP)
  )
  
  def zero(currency: String): MoneyAmount = MoneyAmount(Money.zero(currency.toCurrency), Money.zero(USD))
}
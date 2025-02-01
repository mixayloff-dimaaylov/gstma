// -*- coding: utf-8 -*-
// ---
// jupyter:
//   jupytext:
//     text_representation:
//       extension: .scala
//       format_name: light
//       format_version: '1.5'
//       jupytext_version: 1.16.6
//   kernelspec:
//     display_name: Scala 2.12
//     language: scala
//     name: scala2.12
// ---

// Digital Filters on Scala
// ========================

// + [markdown] jp-MarkdownHeadingCollapsed=true
// ## LICENSE
// -

// Copyright 2023 mixayloff-dimaaylov at github dot com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ## Введение

// Интерактивный вариант расчетов, используемых в кластере

// ## Импорты
//
// Plotly для отрисовки графиков как алтернативна Matplotlib в Python-блокнотах.
//
// Предварительно необходимо установить расширение `jupyterlab-plotly`. Подробности смотрите
// [на странице проекта](https://github.com/alexarchambault/plotly-scala#jupyterlab).

// +
import $ivy.`org.plotly-scala::plotly-almond:0.8.4`
import plotly._, plotly.element._, plotly.layout._, plotly.Almond._

// if you want to have the plots available without an internet connection:
// init(offline=true)

// restrict the output height to avoid scrolling in output cells
repl.pprinter() = repl.pprinter().copy(defaultHeight = 3)
// -

// Numsca -- легковесная замена Numpy

// +
import $ivy.`be.botkop::numsca:0.1.5`
import botkop.{numsca => ns}

import math.sin
// -

// ## Расчеты
//
// Сымитируем тестовыю ситуацию с мелко- и крупно-масштабными возмущениями:

val xs = ns.linspace(0.0, 60.0, 100000).data.toSeq
val res = xs.map(x => 2.0 + 0.3 * sin(x / 35) + 0.1 * sin(x * 500))

plot(Seq(Scatter(xs, res)))

// ### Фильтры Баттерворта (импорт из проекта)

object DigitalFilters extends Serializable {
  def avgNt(nt: Seq[Double], avgNt: Seq[Double]): Double = {
    val b = Seq(
      0.00000004863987500780838,
      0.00000029183925004685027,
      0.00000072959812511712565,
      0.00000097279750015616753,
      0.00000072959812511712565,
      0.00000029183925004685027,
      0.00000004863987500780838
    )

    val a = Seq(
      -5.5145351211661655,
      12.689113056515138,
      -15.593635210704097,
      10.793296670485379,
      -3.9893594042308829,
      0.6151231220526282
    )

    butterworthFilter(b, a, nt, avgNt)
  }

  def delNt(nt: Seq[Double], delNt: Seq[Double]): Double = {
    val b = Seq(
      0.076745906902313671,
      0,
      -0.23023772070694101,
      0,
      0.23023772070694101,
      0,
      -0.076745906902313671
    )

    val a = Seq(
      -3.4767608600037727,
      5.0801848641096203,
      -4.2310052826910152,
      2.2392861745041328,
      -0.69437337677433475,
      0.084273573849621822
    )

    butterworthFilter(b, a, nt, delNt)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def butterworthFilter(b: Seq[Double], a: Seq[Double], bInputSeq: Seq[Double], aInputSeq: Seq[Double]): Double = {
    if (b.length !== bInputSeq.length) throw
      new IllegalArgumentException(s"The length of b must be equal to bInputSeq length")

    if (a.length !== aInputSeq.length) throw
      new IllegalArgumentException(s"The length of a must be equal to aInputSeq length")

    (b, bInputSeq).zipped.map((x, y) => x * y).sum - (a, aInputSeq).zipped.map((x, y) => x * y).sum
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class AnyOps[A](self: A) {
    def !==(other: A): Boolean = self != other
  }
}

// ### Расчеты фильтров по проекту

// +
import scala.collection.mutable

val filterOrder = 6
val zero: Double = 0

val zeroSeq = mutable.Seq.fill[Double](filterOrder)(zero)
val NTSeq = zeroSeq.padTo(filterOrder, zero) ++ res
var avgNTSeq = zeroSeq.padTo(filterOrder + xs.length, zero)
var delNTSeq = zeroSeq.padTo(filterOrder + xs.length, zero)
// -

for (i <- filterOrder until NTSeq.length) {
  val nt7 =
      for (j <- 0 until (filterOrder + 1))
        yield NTSeq(i - j)

  avgNTSeq(i) = DigitalFilters.avgNt(nt7, avgNTSeq.slice(i - 6, i - 1 + 1).reverse)
  delNTSeq(i) = DigitalFilters.delNt(nt7, delNTSeq.slice(i - 6, i - 1 + 1).reverse)
}

plot(
    Seq(
        Scatter(xs, avgNTSeq, "avgNT"),
        Scatter(xs, delNTSeq, "delNT")))

// ### Фильтры Баттерворта (фабрика фильтров)
//
// Переписанная под фабричный метод генерации фильтров реализация. Теперь фильтры -- это объекты с состояниями. Удобный интерфейс и самодостаточность. Реализация в виде функционального объекта позволяет получать результат без лишних движений.

// +
import scala.collection.mutable

class DigitalFilter(val order: Int,
                    val b: Seq[Double],
                    val a: Seq[Double]) extends Serializable {

  if (order < 1) throw
    new IllegalArgumentException(s"The filter order must be positive number")

  if (b.length != (order + 1) || a.length != order) throw
    new IllegalArgumentException(s"The a's and b's lengths must be satisfy to filter order")

  private val zero: Double = 0
  private val zeroSeq = mutable.Seq.fill[Double](order)(zero)
  var bInputSeq = zeroSeq
  var aInputSeq = zeroSeq

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  private def filt(bInputSeq: Seq[Double], aInputSeq: Seq[Double]): Double = {
    if (b.length !== bInputSeq.length) throw
      new IllegalArgumentException(s"The length of b must be equal to bInputSeq length")

    if (a.length !== aInputSeq.length) throw
      new IllegalArgumentException(s"The length of a must be equal to aInputSeq length")

    ((b, bInputSeq).zipped.map((x, y) => x * y).sum
     - (a, aInputSeq).zipped.map((x, y) => x * y).sum)
  }

  def apply(input: Seq[Double]): Seq[Double] = {
    val iSeq = bInputSeq.padTo(order, zero) ++ input
    var oSeq = aInputSeq.padTo(order + input.length, zero)

    for (i <- order until iSeq.length) {
      oSeq(i) =
        filt(
          iSeq.slice(i - order, i - 1 + 2).reverse,
          oSeq.slice(i - order, i - 1 + 1).reverse)
    }

    bInputSeq = iSeq.takeRight(order)
    aInputSeq = oSeq.takeRight(order)
    oSeq.slice(0, oSeq.length - order).toSeq
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class AnyOps[A](self: A) {
    def !==(other: A): Boolean = self != other
  }
}
// -

// Так же фильтры не привязаны к порядку и своим коэффициентам и могут генерироваться из фабрики.
// Например, можно определить стандартные фабрики, используемые в проекте (и подобные образом можно их множить в дальнейшем):

object DigitalFilters extends Serializable {
  def avgNt(): DigitalFilter = {
    val b = Seq(
      0.00000004863987500780838,
      0.00000029183925004685027,
      0.00000072959812511712565,
      0.00000097279750015616753,
      0.00000072959812511712565,
      0.00000029183925004685027,
      0.00000004863987500780838
    )

    val a = Seq(
      -5.5145351211661655,
      12.689113056515138,
      -15.593635210704097,
      10.793296670485379,
      -3.9893594042308829,
      0.6151231220526282
    )

    new DigitalFilter(6, b, a)
  }

  def delNt(): DigitalFilter = {
    val b = Seq(
      0.076745906902313671,
      0,
      -0.23023772070694101,
      0,
      0.23023772070694101,
      0,
      -0.076745906902313671
    )

    val a = Seq(
      -3.4767608600037727,
      5.0801848641096203,
      -4.2310052826910152,
      2.2392861745041328,
      -0.69437337677433475,
      0.084273573849621822
    )

    new DigitalFilter(6, b, a)
  }
}

// Так же, подобная реализация позволяет посмотреть характеристики фильтров, которые являются их неизменяемыми свойствами:

DigitalFilters.avgNt.order
DigitalFilters.avgNt.b
DigitalFilters.avgNt.a

DigitalFilters.delNt.order
DigitalFilters.delNt.b
DigitalFilters.delNt.a

// #### Бесшовное сшитие значений из двух выборок
//
// Проверим работу внутреннего состояния фильтров, разбив исходный сигнал на два равных промежутка.
//
// Инициализация фильтров:

val avgNTFilter = DigitalFilters.avgNt
val delNTFilter = DigitalFilters.delNt

// Начальное состояние фильтров:

// +
avgNTFilter.order
avgNTFilter.b
avgNTFilter.a
avgNTFilter.bInputSeq
avgNTFilter.aInputSeq

delNTFilter.order
delNTFilter.b
delNTFilter.a
delNTFilter.bInputSeq
delNTFilter.aInputSeq
// -

// Теперь проверим, как работает бесшовное сшитие на основе внутреннего состояния фильтров:

val avgNTSeq2 = avgNTFilter(res.slice(0, res.length/2)) ++ avgNTFilter(res.slice(res.length/2, res.length))
val delNTSeq2 = delNTFilter(res.slice(0, res.length/2)) ++ delNTFilter(res.slice(res.length/2, res.length))

plot(
    Seq(
        Scatter(xs, avgNTSeq2, "avgNT"),
        Scatter(xs, delNTSeq2, "delNT")))

// #### Бесшовное сшитие штучных значений
//
// Так же предложенную реализацию фильтра можно использовать для иттерирования входных значений по-одному:
//
// Инициализация фильтров:

val avgNTFilter = DigitalFilters.avgNt
val delNTFilter = DigitalFilters.delNt

// Начальное состояние фильтров:

// +
avgNTFilter.order
avgNTFilter.b
avgNTFilter.a
avgNTFilter.bInputSeq
avgNTFilter.aInputSeq

delNTFilter.order
delNTFilter.b
delNTFilter.a
delNTFilter.bInputSeq
delNTFilter.aInputSeq
// -

val avgNTSeq3 = res.flatMap(x => avgNTFilter(Seq(x)))
val delNTSeq3 = res.flatMap(x => delNTFilter(Seq(x)))

plot(
    Seq(
        Scatter(xs, avgNTSeq3, "avgNT"),
        Scatter(xs, delNTSeq3, "delNT")))

// В итоге, результат полученный обоими методами равны:

avgNTSeq2 == avgNTSeq3

delNTSeq2 == delNTSeq3

// + active=""
// Какой подход использовать? Зависит от задачи!

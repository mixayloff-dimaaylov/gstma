// -*- coding: utf-8 -*-
// ---
// jupyter:
//   jupytext:
//     text_representation:
//       extension: .scala
//       format_name: light
//       format_version: '1.5'
//       jupytext_version: 1.16.6
// ---

// ## plotly-scala

// - LaTeX-формулы не отрисовываются!
// - Нельзя сделать подписи дочерних графиков!
// - Нельзя сделать раздельные легенды!

import $ivy.`org.plotly-scala::plotly-almond:0.8.5`
import plotly._, plotly.element._, plotly.layout._, plotly.Almond._

import $ivy.`be.botkop::numsca:0.1.5`
import botkop.{numsca => ns}
import math.{Pi, sin, cos, sqrt, pow, log, exp, random}

val f_0s = ns.linspace(0.1, 10, 1000) * 1e9
val l_ss = ns.array(200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0)
val hm = 300 * 1e3
val z_e = 500 * 1e3
val z1 = hm - (z_e / 2)
val C = 299792458.0

// +
// old
def d1_2_old(f_0s: botkop.numsca.Tensor, z: Double, z_e: Double, l_s: Double): botkop.numsca.Tensor = {
  (3 * (z * z) - 3 * z * z_e + (z_e * z_e)) /
    (6 * ns.power(2 * Pi * f_0s / C, 2)) * (pow(l_s, (-4)) / 8)
}


// modified
def d1_2_modified(f_0: botkop.numsca.Tensor, z: Double, z_e: Double, l_s: Double): botkop.numsca.Tensor = {
  (3 * (z * z) - 3 * z * z_e + (z_e * z_e)) /
    (6 * ns.power(2 * Pi * f_0 / C, 2)) * 32 * pow(l_s, -4)
}

{{
    val lay = Layout()
        .withGrid(
            Grid(Some(1), Some(2), Some(Pattern.Independent), None, 
                None))
        .withXaxis1(Axis("""f_0, Гц"""))
        .withXaxis2(Axis("""f_0, Гц"""))
        .withYaxis1(Axis("""l_s, м""").withType(AxisType.Log))
        .withYaxis2(Axis("""l_s, м""").withType(AxisType.Log))

    {{
        var data = l_ss.data.map((l_s) => {
            Scatter(
                f_0s.data.toSeq,
                d1_2_old(f_0s, z1, z_e, l_s).data.toSeq,
                name = """%.3s""".format(l_s),
                xaxis = AxisReference.X1,
                yaxis = AxisReference.Y1
            )
        })

        data = data ++ l_ss.data.map((l_s) => {
            Scatter(
                f_0s.data.toSeq,
                d1_2_modified(f_0s, z1, z_e, l_s).data.toSeq,
                name = """%.3s""".format(l_s),
                xaxis = AxisReference.X2,
                yaxis = AxisReference.Y2
            )
        })
        
        plot(data, lay.withTitle("d_{1, мод}^2(f_0)"))
    }}
}}

// ax[0].set_title(r"$d_1^2(f_0)$")
// ax[1].set_title(r"$d_{1, мод}^2(f_0)$")
// -

// ## matplotlib4j

import $ivy.`com.github.sh0nk::matplotlib4j:0.5.0`

// +
import com.github.sh0nk.matplotlib4j._
import math.{Pi, sin, cos, sqrt, pow, log, exp, random}
import scala.collection.JavaConverters._

val x = NumpyUtils.linspace(-3, 3, 100).asScala.toList
val y = x.map(xi => sin(xi) + random()).map(Double.box)

val plt = Plot.create()
plt.plot().add(x.asJava, y.asJava, "o")
plt.title("scatter")
plt.show()
// -

// - Отображение в JupyterLab не поддерживается!

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

// Графики помехоусточивости
// =========================
//
// Графики из монографии Маслов-Пашинцев.

// ## Импорты

import $ivy.`be.botkop::numsca:0.1.5`
import botkop.{numsca => ns}

// +
import $ivy.`org.plotly-scala::plotly-almond:0.8.5`
import plotly._, plotly.element._, plotly.layout._, plotly.Almond._

// if you want to have the plots available without an internet connection:
// init(offline=true)

// restrict the output height to avoid scrolling in output cells
repl.pprinter() = repl.pprinter().copy(defaultHeight = 3)
// -

// ## Исходные данные

// +
// Марк:
// - 10^13 .. 10^14 эл/м^2 для нормальной ионосферы без возмущений
// - 10^14 .. 10^15 эл/м^2 для нормальной ионосферы со слабыми естественными
//   возмущениями
// - 10^15 .. 10^16 эл/м^2 для нормальной ионосферы с сильными возмущениями
val sigma_d_nts = ns.array(1.0, 10.0, 50.0, 100.0, 150.0, 1000.0) * 1e13

// Марк: 0.44 Ггц, но больший диапазон интереснее
// f_0s = np.array([0.44, 0.5, 0.55, 0.6, 0.7]) * 1e9
val f_0s = ns.linspace(0.1, 10, 1000) * 1e9

val l_ss = ns.array(200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0)

// Марк: высота максимума ионизации, м
val hm = 300 * 1e3
val z_e = 500 * 1e3
val z1 = hm - (z_e / 2)
val z = z1 + z_e
// -

// ## СКО флуктуаций фазового фронта волны  на выходе неоднородного слоя $\sigma_{\varphi}$
//
// $$\sigma_{\phi} = {
// {
//   80.8
//     \cdot \pi^{5/4}
//     \cdot \left(
//             l_s
//               \cdot z_{э}
//               \cdot \sec{\theta_0}
//           \right) ^{1/2}
//     \cdot \sigma_{\Delta N}
// }
//   \over
// {
//   c \cdot f_0
// }
// }$$
//
// $$\sigma_{\phi} = {
//   \left( {80.8 \cdot \pi} \over {C} \right)
//     \cdot \left( \sigma_{\Delta N_t} \over f_0 \right)
//     \cdot \sec{\theta_0}
// }$$

import math.{Pi, sin, cos, sqrt, pow, log, exp, random}

val C = 299792458.0
val theta_0 = (90.0).toRadians

def sigma_phi(f_0s: botkop.numsca.Tensor, sigma_d_nt: Double, theta_0: Double): botkop.numsca.Tensor = {
    (80.8 * Pi / C) * (sigma_d_nt / f_0s) * sqrt(1.0 / cos(theta_0) )
}

{{
    val data = sigma_d_nts.data.map((s) => {
        Scatter(
            f_0s.data.toSeq,
            sigma_phi(f_0s, s * (f_0s.shape(1)), theta_0).data.toSeq,
            name = """%.2g""".format(s)
        )
    }).toSeq

    var lay = Layout(width = 600, height = 500)
        .withTitle("""sigma_phi(f_0), Рад""")
        .withXaxis(Axis("""f_0, Гц"""))
        .withYaxis(Axis("""sigma_phi, Рад""")
                  .withType(AxisType.Log))

    plot(data, lay)
}}

// ## Коэффициент нарастания дифракционных эффектов во фронте волны внутри ионосферы и за ней до точки приёма $d_1^2$

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
// -

{{
    val lay = Layout(width = 600, height = 500)
        .withXaxis(Axis("""f_0, Гц"""))
        .withYaxis(Axis("""l_s, м""").withType(AxisType.Log))

    {{
        var data = l_ss.data.map((l_s) => {
            Scatter(
                f_0s.data.toSeq,
                d1_2_old(f_0s, z1, z_e, l_s).data.toSeq,
                name = """%.3s""".format(l_s)
            )
        })

        plot(data, lay.withTitle("d_1^2(f_0)"))
    }}

    {{
        var data = l_ss.data.map((l_s) => {
            Scatter(
                f_0s.data.toSeq,
                d1_2_modified(f_0s, z1, z_e, l_s).data.toSeq,
                name = """%.3s""".format(l_s)
            )
        })

        plot(data, lay.withTitle("d_{1, мод}^2(f_0)"))
    }}
}}

// ## Традиционный интервал частотной корреляции замираний в однолучевой ДКМ радиолинии $F_{k_0}$

// +
// old
def F_k0_old(f_0s: botkop.numsca.Tensor, sigma_d_nt: Double, theta_0: Double, z: Double, z_e: Double, l_s: Double): botkop.numsca.Tensor = {
  val sqr = ns.sqrt(2 + d1_2_old(f_0s, z, z_e, l_s))
  val s = sigma_phi(f_0s, sigma_d_nt, theta_0)

  f_0s / (s * sqr)
}


// modified
def F_k0_modified(f_0s: botkop.numsca.Tensor, sigma_d_nt: Double, theta_0: Double, z: Double, z_e: Double, l_s: Double): botkop.numsca.Tensor = {
  val sqr = ns.sqrt(2 + d1_2_modified(f_0s, z, z_e, l_s))
  val s = sigma_phi(f_0s, sigma_d_nt, theta_0)

  f_0s / (s * sqr)
}

// fig, ax = plt.subplots(1, 2, figsize=(9, 4))
val theta_0 = (90.0).toRadians
val l_s = 380
// -

// TODO:
{{
    var lay = Layout(width = 600, height = 500)
        .withXaxis(Axis("""f_0, Гц""").withType(AxisType.Log))
        .withYaxis(Axis("""F_k_0, Гц""").withType(AxisType.Log))

    {{
        var data = sigma_d_nts.data.map((s) => {
            Scatter(
                f_0s.data.toSeq,
                F_k0_old(f_0s, s, theta_0, z, z_e, l_s).data.toSeq,
                name = """%.2g""".format(s)
            )
        })

        plot(data, lay.withTitle("F_{k_0}(f_0), Гц"))
    }}

    {{
        var data = sigma_d_nts.data.map((s) => {
            Scatter(
                f_0s.data.toSeq,
                F_k0_modified(f_0s, s, theta_0, z, z_e, l_s).data.toSeq,
                name = """%.2g""".format(s)
            )
        })

        plot(data, lay.withTitle("F_{k_{0}, мод.}(f_0), Гц"))
    }}
}}

// ## Понижающий коэффициент $\Delta F_{k_0}$

// +
// new
def dF_k0(f_0s: botkop.numsca.Tensor, sigma_d_nt: Double, theta_0: Double): botkop.numsca.Tensor = {
    val s = sigma_phi(f_0s, sigma_d_nt, theta_0)
    val s2 = s * s
    val v = 1 - ns.exp(-s2) + ns.exp(1 - s2)
    val lg = ns.log(v)

    ns.sqrt(1 - lg)
}

// fig, ax = plt.subplots()
val theta_0 = (90.0).toRadians
// -

// TODO
{{
    var data = sigma_d_nts.data.map((s) => {
        Scatter(
            f_0s.data.toSeq,
            dF_k0(f_0s, s, theta_0).data.toSeq,
            name = """%.2g""".format(s)
        )
    })

    var lay = Layout(width = 600, height = 500)
        .withTitle("""delta F_k_0(f_0), Гц""")
        .withXaxis(Axis("""f_0, Гц"""))
        .withYaxis(Axis("""delta F_k_0, Гц"""))

    plot(data, lay)
}}

// ## Интервал частотной корреляции замираний в однолучевой ДКМ радиолинии $F_k$

// +
// old
def F_k_old(f_0s: botkop.numsca.Tensor, sigma_d_nt: Double, theta_0: Double, z: Double, z_e: Double, l_s: Double): botkop.numsca.Tensor = {
    val _F_k0 = F_k0_old(f_0s, sigma_d_nt, theta_0, z, z_e, l_s)

    _F_k0
}


// modified
def F_k_modified(f_0s: botkop.numsca.Tensor, sigma_d_nt: Double, theta_0: Double, z: Double, z_e: Double, l_s: Double): botkop.numsca.Tensor = {
    val _F_k0 = F_k0_modified(f_0s, sigma_d_nt, theta_0, z, z_e, l_s)
    val _dF_k0 = dF_k0(f_0s, sigma_d_nt, theta_0)

    _F_k0 * _dF_k0
}

val l_s = 380
val theta_0 = (90.0).toRadians

// +
// TODO:

{{
    var lay = Layout(width = 600, height = 500)
        .withXaxis(Axis("""f_0, Гц""").withType(AxisType.Log))
        .withYaxis(Axis("""F_k_0, Гц""").withType(AxisType.Log))

    {{
        var data = sigma_d_nts.data.map((s) => {
            Scatter(
                f_0s.data.toSeq,
                F_k_old(f_0s, s, theta_0, z, z_e, l_s).data.toSeq,
                name = """%.2g""".format(s)
            )
        })
        plot(data, lay.withTitle("""F_k(f_0), Гц"""))
    }}

    {{
        var data = sigma_d_nts.data.map((s) => {
            Scatter(
                f_0s.data.toSeq,
                F_k_modified(f_0s, s, theta_0, z, z_e, l_s).data.toSeq,
                name = """%.2g""".format(s)
            )
        })
        plot(data, lay.withTitle("""F_k_мод(f_0), Гц"""))
    }}
}}
// -

// ## Зависимость интервала частотной корреляции $F_k$ замираний и понижающего коэффициента $\Delta F_{k_0}$ от отношения $f_0 / f_m$ при $\beta_{и}$

// +
// F_k_F_k_0 (модифицированная)
val theta_0 = (90.0).toRadians

val f_m = (15 * 1e6)
val f_0s = ns.linspace(0.2, 1.0, 1000) * f_m
val f_0m = f_0s / f_m

// TODO:
val l_s = 380
val s = 1e13 // sigma_d_nt

plot(
    Seq(
        Scatter(f_0s.data.toSeq, F_k_modified(f_0s, s, theta_0, z, z_e, l_s).data.toSeq, name = "мод. $F_k$"),
        Scatter(f_0s.data.toSeq, F_k0_modified(f_0s, s, theta_0, z, z_e, l_s).data.toSeq, name = "мод. $F_{k_0}$"),
        Scatter(f_0s.data.toSeq, dF_k0(f_0s, s, theta_0).data.toSeq, name = """F_k_0""")
    ),
    Layout(width = 600, height = 500)
        .withTitle("""F_k(f_0), F_k_0, Гц""")
        .withXaxis(Axis("""f_0 / f_m, n"""))
        .withYaxis(Axis("""F_k(f_0), F_k_0, Гц"""))
)
// -

// ## Какой интеграл Френеля взять
//
// Интеграл Френеля в книге отличается от того, что предлагает Scipy.

import $ivy.`org.scalanlp::breeze:2.1.0`
import breeze.integrate._
import breeze.numerics._

// +
def fresnel_C(z: ns.Tensor): ns.Tensor = {
    ns.Tensor(z.data.map((z_i) => {
        trapezoid((t) => cos(Pi * t * t / 2), 0, z_i, 1000)
    }))
}

def Fp(x: ns.Tensor): ns.Tensor = {
    def fp(_x: Double): Double = {
        cos(_x) / sqrt(_x)
    }

    ns.Tensor(x.data.map((i) => {
        val _v = trapezoid(fp, 0.0, i, 1000)
        (1.0 / (2.0 * Pi)) * _v
    }))
}

{{
    val xs = ns.linspace(0.1, 10.0, 1000)

    val C = fresnel_C(xs)
    val Cp = Fp(xs)
    val Cpm = 2.5 * Fp(xs * Pi)
    var handles = Seq()

    {{
        val plots = (Seq(C, Cp, Cpm)
          zip Seq(raw"Из Scipy", raw"Из книги", raw"Из книги (модифицированная)")).map({
            case (d, l) => {
              Scatter(xs.data.toSeq, d.data.toSeq, l) // ax[0].semilogx(xs, d, label=l)
          }})

        plot(plots, Layout(width = 600, height = 500))
    }}

    val FF = ns.linspace(0.1, 6.0, 1000)
    val eta_d = ((Pi / (2.0 * (FF ** 2))) * ns.power(fresnel_C(FF ** 2) ** 2, 2.0))
    val eta_dp = ((Pi / (2.0 * (FF ** 2))) * ns.power(Fp(FF ** 2), 2.0))

    {{
        val plots = (Seq(eta_d, eta_dp, eta_d)
          zip Seq(raw"Из Scipy", raw"Из книги", raw"Из книги (модифицированная)")).map({
            case (d, l) => {
              Scatter(FF.data.toSeq, d.data.toSeq, l) // ax[1].semilogx(FF, d, label=l)
          }})

        plot(plots, Layout(width = 600, height = 500))
    }}
}}
// -

// ## Оценка помехоусточивости
//
// ### Исходные данные
//
// **Параметры ионосферы:**
//
// -   Максимум средней ЭК ${\bar{N_m}}$, $эл/м^3$;
// -   Интенсивность неоднородностей $\beta$;
// -   СКО флуктуаций ЭК ${\sigma_{\Delta N}}$, $эл/м^3$.

case class IonParams(
    label: String,
    N_m: Double, // эл/м^3
    betta: Double, // betta * N_m
    sigma_delta_N: Double, // эл/м^3,
    var sigma_d_nt: Double
)

var ion_params = Seq(
    IonParams("nigth", 2.4 * 1e11,  1e-2,       2.4 * 1e9, 0),
    IonParams("day",   1.4 * 1e12,  3 * 1e-3,   4.2 * 1e9, 0),
    IonParams("evi",   1.4 * 1e12,  3.6 * 1e-2, 5.0 * 1e10, 0),
    IonParams("ivil",  1.0 * 1e13,  5.0 * 1e-1, 5.0 * 1e12, 0),
    IonParams("ivib",  5.0 * 1e13,  1.0,        5.0 * 1e13, 0)
)

// +
val l_s = 200

def calc_sigma_d_nt(l_s: Double, z_e: Double, sigma_d_n: Double): Double = {
    sqrt(sqrt(Pi) * l_s * z_e) * sigma_d_n
}

ion_params = ion_params.map((row) => {
    row.sigma_d_nt = calc_sigma_d_nt(l_s, z_e, row.sigma_delta_N)
    row
})
// -

// **Параметры сигналов:**
//
// -   Несущая частота $f_0$, $Гц$;
// -   Скорость передачи $R_T = 1 / T_S$, $бит/с$;
// -   Период передачи 1 бит $T_S$, $с$;
// -   Ширина спектра $F_0 = B_S / T_S$, $Гц$;
// -   Угол возвышения $\theta_0$, $градусы$.

// +
val f_0 = 400 * 1e6 // Гц
val R_T = 2.7 * 1e3 // бит/с
val T_S = 1.0 / R_T // c/бит
val theta_0 = (60.0).toRadians // NumPy использует радианы по-умолчанию

val B_S = 1.0       // простые сигналы
val F_0 = B_S / T_S // ширина спектра

val sigma_d_nts = ns.Tensor(ion_params.map((r) => r.sigma_d_nt).toArray)
val sigma_phis = ns.Tensor(sigma_d_nts.data.map((s) => sigma_phi(ns.Tensor(f_0), s, theta_0).squeeze))
// -

// ### Зависимость СКО флуктуаций фазового фронта волны $\sigma_\varphi$ от sigma_d_nts

plot(
    Seq(Scatter(sigma_d_nts.data.toSeq, sigma_phis.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("""sigma_phi(sigma_Delta N_t), Рад""")
        .withXaxis(Axis("""sigma_Delta N_t, эл/м^2"""))
        .withYaxis(Axis("""sigma_phi, Рад"""))
)

// ### Глубина общих БЗ $\gamma^2$
//
// $$\gamma^2 = {{1} \over {\exp{\sigma_{\phi}^2} - 1}}$$

// +
def gamma2(sigma_phi: ns.Tensor): ns.Tensor = {
    1.0 / (ns.exp(sigma_phi ** 2) - 1)
}

val gamma_2s = gamma2(sigma_phis)

plot(
    Seq(Scatter(sigma_phis.data.toSeq, gamma_2s.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("""gamma^2(sigma_phi})""")
        .withXaxis(Axis("""sigma_phi, Рад""").withType(AxisType.Log))
        .withYaxis(Axis("""gamma^2"""))
)
// -

// ### Полоса когерентности F_К
//
// $$F_{К} = {
//   {f_0^2 \cdot c}
//     \over
//   {
//     80.8
//       \cdot \pi^{5/4}
//       \cdot \left(
//               2 \cdot l_s \cdot z_{э} \cdot \sec{\theta_0}
//             \right) ^{1/2}
//       \cdot \sigma_{\Delta N}
//       \cdot Д_1
//   }
// }$$
//
// $$Д_1 = {1+d_1^2 \over 2}$$
//
// $$d_1^2 = {
//   {
//     \left(
//       3 \cdot z^2 - 3 \cdot z \cdot z_{э} + z_{э}^2
//     \right)
//     \cdot c^2
//     \cdot \sec{\theta_0}^2
//   }
//     \over
//   { 192 \cdot \pi^2 \cdot f_0^2 \cdot l_s^4 }
// }$$
//
// $$F_{К} = {
//   {f_0}
//     \over
//   {
//     \sigma_{\phi} \cdot \sqrt{2 + d_1^2}
//   }
// }$$
//
// $$d_1^2 = {
//   {{
//     \left(
//       3 \cdot z^2 - 3 \cdot z \cdot z_{э} + z_{э}^2
//     \right)
//     \cdot c^2
//     \cdot \sec{\theta_0}^2
//   }
//     \over
//   { 6 \cdot (\pi \cdot f_0 / C)^2}}
//   \cdot (l_s^{-4} / 8)
// }$$

// +
val F_ks = ns.Tensor(sigma_d_nts.data.map((s) => F_k_old(ns.Tensor(f_0), s, theta_0, z, z_e, l_s).squeeze))

plot(
    Seq(
        Scatter(sigma_d_nts.data.toSeq, F_ks.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("""F_k(sigma_Delta N_t), Гц""")
        .withXaxis(Axis("""sigma_Delta N_t, эл/м^2""").withType(AxisType.Log))
        .withYaxis(Axis("""F_k, Гц"""))
)
// -

// ### Полоса диспертности $F_{Д}$
//
// $$F_{д} = {
//   \left(
//     {c \cdot f_{0}^3}
//       \over
//     {
//       80.8
//         \cdot \pi
//         \cdot \bar{N_m}
//         \cdot z_{э}
//         \cdot \sec{\theta_0}
//     }
//   \right) ^{1/2}
// }$$
//
// $$F_{Д} = {
//   \sqrt{
//     {C \cdot f_{0}^3}
//       \over
//     {
//       80.8 \cdot \pi \cdot \bar{N_m} \cdot z_{э} \cdot \sec{\theta_0}
//     }
//   }
// }$$

// +
def F_d(f_0: Double, N_m: Double, z_e: Double, theta_0: Double): Double = {
    sqrt(C * (f_0 * f_0 * f_0) / (80.8 * Pi * N_m * z_e * (1.0 / cos(theta_0))))
}

val N_ms = ns.Tensor(ion_params.map((r) => r.N_m).toArray)
val F_ds = ns.Tensor(N_ms.data.map((n) => F_d(f_0, n, z_e, theta_0)))

plot(
    Seq(Scatter(N_ms.data.toSeq, F_ds.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("""F_d(N_m), Гц""")
        .withXaxis(Axis("""N_m"""))
        .withYaxis(Axis("""F_d, Гц"""))
)
// -

// ### F_0 / F_k

val F_0_over_F_k = F_0 / F_ks

// ### F_0 / F_d

val F_0_over_F_d = F_0 / F_ds

// ### Степень МСИ $\eta_{м}$
//
// $$\eta_{м} = {
//   {{1} \over {2 \cdot \pi^2}}
//     \cdot \left( {{1} \over {T_S \cdot F_K}} \right)^2
//     \cdot erf \left( {\pi \cdot T_S \cdot F_K} \right) -
//   {{1} \over {\pi \cdot \sqrt{\pi}}}
//     \cdot \left( {{1} \over {T_S \cdot F_K}} \right)
//     \cdot \exp(- (\pi \cdot T_S \cdot F_K)^2)
// }$$

// +
// Зависи от 1 / T_s F_k
def eta_m(_T_S: Double, _F_K: ns.Tensor): ns.Tensor = {
    val _v = 1.0 / (_T_S * _F_K)
    val _t = Pi * _T_S * _F_K

    (1.0 / (2 * Pi * Pi) * (_v * _v)
             * ns.Tensor(_t.data.map((x: Double) => erf(x)))
           - 1.0 / (Pi * sqrt(Pi)) * _v * ns.exp(-1.0 * (_t ** 2)))
}

val eta_ms = eta_m(T_S, F_ks)

plot(
    Seq(Scatter(F_ks.data.toSeq, eta_ms.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("""eta_m(F_k)""")
        .withXaxis(Axis("""F_k, Гц""").withType(AxisType.Log))
        .withYaxis(Axis("""eta_m"""))
)
// -

// ### Степень ЧСЗ $\eta_{ч}$
//
// $$\eta_{ч} = {
//   \left[ 1 + {{1} \over {2 \cdot \pi^2 }} \cdot \left( F_0 \over F_k \right) ^2 \right]
//     \cdot erf \left({ {\pi \cdot F_k} \over {F_0}} \right) -
//   {{1} \over {\pi \cdot \sqrt{\pi}}}
//     \cdot \left( F_0 \over F_{к} \right)
//     \cdot \left( 2 - \exp \left(- { \left( {{\pi \cdot F_{к}} \over {F_0}} \right)}^2 \right) \right)
// }$$

// +
// Зависит от F_0, F_k, но это тоже функции
def eta_ch(_F_0: Double, _F_k: ns.Tensor): ns.Tensor = {
    val _v = Pi * _F_k / _F_0

    ((1.0 + (1 / 2 * Pi * Pi) * ns.power(_F_0 / _F_k, 2))
             * ns.Tensor(_v.data.map((x: Double) => erf(x)))
             - 1.0 / (Pi * sqrt(Pi)) * (_F_0 / _F_k)
             * (2.0 - ns.exp(-(_v * _v))))
}

val eta_chs = eta_ch(F_0, F_ks)

plot(
    Seq(Scatter(F_ks.data.toSeq, eta_chs.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("$eta_ch(F_k)")
        .withXaxis(Axis("F_k, Гц").withType(AxisType.Log))
        .withYaxis(Axis("eta_ch"))
)
// -

// ### Степень ДИ $\eta_{д}$
//
// $$\eta_{д} = {
//   {\pi \cdot \left[ C_2 \left( F_0 \over F_{д} \right)^2 \right]^2 }
//     \over {2 \cdot \left( F_0 \over F_{д} \right)^2}
// }$$

// зависит от F_0, F_d
def eta_d(_F_0: Double, _F_d: ns.Tensor): ns.Tensor = {
    val _v = ns.power(_F_0 / _F_d, 2)
    val _C_2 = fresnel_C(ns.sqrt(2 * _v / Pi))
    (Pi * (_C_2 * _C_2)) / (2.0 * _v)
}

// Зависимость коэффициента энергетических потерь $\eta_{д}$ при НК обработке от степени их ДИ (Рис. 4.10., с. 253).

// +
val F_ds = ns.linspace(10 * F_0, 0.01 * F_0, 1000)
val eta_ds = eta_d(F_0, F_ds)

plot(
    Seq(Scatter((F_0 / F_ds).data.toSeq, eta_ds.data.toSeq)),
    Layout(width = 600, height = 500)
        .withTitle("""eta_d(F_d)""")
        .withXaxis(Axis("""F_0 / F_d""")
                    .withType(AxisType.Log))
        .withYaxis(Axis("""eta_d"""))
)
// -

// ### Построение графиков вероятности ошибки
//
// $$P_{ош} = {
//   0.25 \cdot \left( P_{111} + P_{110} + P_{011} + P_{010} \right)
// }$$

case class SimParams(
    sigma_d_nt: Double,
    sigma_phi: Double,
    gamma_2: Double,
    F_k: Double,
    F_d: Double,
    F_0_over_F_k: Double,
    F_0_over_F_d: Double,
    eta_m: Double,
    eta_ch: Double,
    eta_d: Double
)

// Зависит от h2, gamma2, eta_ч, eta_м, eta_d
// Либо Зависит от h2, f_0, F_0, T_s, N_m, sigma_delta_nt
def P_err(_h2s: ns.Tensor, _gamma2: Double, _eta_ms: Double, _eta_chs: Double, _eta_ds: Double)
  : ns.Tensor = {
    val _g = _gamma2
    val _g_1 = _g + 1
    val _p = (_w: ns.Tensor) => (_g_1) / (_w + 2.0 * _g_1) * ns.exp(-1.0 * _g * _w / (_w + 2.0 * _g_1))

    val W111 = _h2s * _eta_ds * _eta_chs
    val W110 = (_h2s * _eta_ds * _eta_chs - _h2s * _eta_ds * _eta_ms) / (1.0 + _h2s * _eta_ds * _eta_ms)
    val W011 = W110
    val W010 = (_h2s * _eta_ds * _eta_chs - 2 * _h2s * _eta_ds * _eta_ms) / (1.0 + 2.0 * _h2s * _eta_ds * _eta_ms)

    val P111 = _p(W111)
    val P110 = _p(W110)
    val P011 = _p(W011)
    val P010 = _p(W010)

    0.25 * (P111 + P110 + P011 + P010)
}

// +
def logspace(start: Double, stop: Double, step: Int): ns.Tensor = {
    val y = ns.linspace(start, stop, step)
    ns.Tensor(y.data.map(x => math.pow(10, x)))
}

var PerrLay = Layout(width = 600, height = 500)
        .withTitle("""P_err(h^2)""")
        .withXaxis(Axis("""h^2""")
                   .withType(AxisType.Log))
        .withYaxis(Axis("""P_err""")
                   .withRange(-6.0, 0.0)
                   .withType(AxisType.Log))
// -

{{
    val sim_params = (0 to 4).map((i) => {
        SimParams(
            sigma_d_nts(0, i).squeeze,
            sigma_phis(0, i).squeeze,
            gamma_2s(0, i).squeeze,
            F_ks(0, i).squeeze,
            F_ds(0, i).squeeze,
            F_0_over_F_k(0, i).squeeze,
            F_0_over_F_d(0, i).squeeze,
            eta_ms(0, i).squeeze,
            eta_chs(0, i).squeeze,
            eta_ds(0, i).squeeze
        )
    })

    val h2s = logspace(0.0, 6.0, 100)
    val P_errs = sim_params.map((r) => {
        P_err(
            h2s.reshape(-1, 1).T,
            r.gamma_2,
            r.eta_m,
            r.eta_ch,
            r.eta_d)
    })

    val plots = (P_errs zip ion_params).map({
        case (i, l) => {
          Scatter(h2s.data.toSeq, i.data.toSeq, l.label)
      }})

    plot(plots, PerrLay)
}}

// ## Сравнение графиков вероятностей

case class SigParams(
    var f_0: Double,
    var R_T: Double,
    var T_S: Double,
    var theta_0: Double,
    var B_S: Double,
    var F_0: Double
)

// +
def calc_sigma_d_nt(l_s: Double, z_e: Double, sigma_d_n: Double): Double = {
    sqrt(sqrt(Pi) * l_s * z_e) * sigma_d_n
}

def P_err_vect(ion_params: Seq[IonParams], sig_params: SigParams, l_s: Double)
  : (ns.Tensor, IndexedSeq[ns.Tensor], Seq[SimParams]) = {
    val f_0 =     sig_params.f_0
    val R_T =     sig_params.R_T
    val T_S =     sig_params.T_S
    val theta_0 = sig_params.theta_0
    // val theta_0 = sig_params.theta_0
    val B_S =     sig_params.B_S
    val F_0 =     sig_params.F_0

    val sigma_d_nts = ns.Tensor(ion_params.map((r) => r.sigma_d_nt).toArray)
    val sigma_phis = ns.Tensor(sigma_d_nts.data.map((s) => sigma_phi(ns.Tensor(f_0), s, theta_0).squeeze))
    val gamma_2s = gamma2(sigma_phis)
    val F_ks = ns.Tensor(sigma_d_nts.data.map((s) => F_k_old(ns.Tensor(f_0), s, theta_0, z, z_e, l_s).squeeze))
    val N_ms = ns.Tensor(ion_params.map((r) => r.N_m).toArray)
    val F_ds = ns.Tensor(N_ms.data.map((n) => F_d(f_0, n, z_e, theta_0)))
    val F_0_over_F_k = F_0 / F_ks
    val F_0_over_F_d = F_0 / F_ds
    val eta_ms = eta_m(T_S, F_ks)
    val eta_chs = eta_ch(F_0, F_ks)
    val eta_ds = eta_d(F_0, F_ds)

    val sim_params = (0 to 4).map((i) => {
        SimParams(
            sigma_d_nts(0, i).squeeze,
            sigma_phis(0, i).squeeze,
            gamma_2s(0, i).squeeze,
            F_ks(0, i).squeeze,
            F_ds(0, i).squeeze,
            F_0_over_F_k(0, i).squeeze,
            F_0_over_F_d(0, i).squeeze,
            eta_ms(0, i).squeeze,
            eta_chs(0, i).squeeze,
            eta_ds(0, i).squeeze
        )
    })

    val h2s = logspace(0.0, 6.0, 100)
    val P_errs = sim_params.map((r) => {
        P_err(
            h2s.reshape(-1, 1).T,
            r.gamma_2,
            r.eta_m,
            r.eta_ch,
            r.eta_d)
    })

    (h2s, P_errs, sim_params)
}
// -

// ### Случай 1

// +
var ion_params = Seq(
    IonParams("nigth", 2.4 * 1e11, 1e-2,       2.4 * 1e9, 0),
    IonParams("day",   1.4 * 1e12, 3 * 1e-3,   4.2 * 1e9, 0),
    IonParams("evi",   1.4 * 1e12, 3.6 * 1e-2, 5.0 * 1e10, 0),
    IonParams("ivil",  1.0 * 1e13, 5.0 * 1e-1, 5.0 * 1e12, 0),
    IonParams("ivib",  5.0 * 1e13, 1.0,        5.0 * 1e13, 0)
)

val l_s = 200

ion_params.map((r) => {
    r.sigma_d_nt = calc_sigma_d_nt(l_s, z_e, r.sigma_delta_N)
    r
})

ion_params

// +
var sig_params = SigParams(
    f_0 = 400 * 1e6,          // Гц
    R_T = 2.7 * 1e3,          // бит/с
    theta_0 = (60).toRadians, // NumPy использует радианы по-умолчанию
    B_S = 1.0,                // простые сигналы
    T_S = 0,
    F_0 = 0
)

sig_params.T_S     = 1.0 / sig_params.R_T            // c/бит
sig_params.F_0     = sig_params.B_S / sig_params.T_S // ширина спектра

sig_params
// -

{{
    val (h2s, p_errs, sim_params) = P_err_vect(
        ion_params, sig_params, l_s)

    val plots = (p_errs zip ion_params).map({
        case (i, l) => {
          Scatter(h2s.data.toSeq, i.data.toSeq, l.label)
      }})

    plot(plots, PerrLay)
}}

// ### Случай 2

// +
var ion_params = Seq(
    IonParams("nigth", 2.4 * 1e11, 1e-2,       2.4 * 1e9, 0),
    IonParams("day",   1.4 * 1e12, 3 * 1e-3,   4.2 * 1e9, 0),
    IonParams("evi",   1.4 * 1e12, 3.6 * 1e-2, 5.0 * 1e10, 0),
    IonParams("ivil",  1.0 * 1e13, 5.0 * 1e-1, 5.0 * 1e12, 0),
    IonParams("ivib",  5.0 * 1e13, 1.0,        5.0 * 1e13, 0)
)

val l_s = 200

ion_params.map((r) => {
    r.sigma_d_nt = calc_sigma_d_nt(l_s, z_e, r.sigma_delta_N)
    r
})

ion_params

// +
var sig_params = SigParams(
    f_0 = 400 * 1e6,          // Гц
    R_T = 9.6 * 1e3,          // бит/с
    theta_0 = (60).toRadians, // NumPy использует радианы по-умолчанию
    B_S = 1.0,                // простые сигналы
    T_S = 0,
    F_0 = 0
)

sig_params.T_S     = 1.0 / sig_params.R_T            // c/бит
sig_params.F_0     = sig_params.B_S / sig_params.T_S // ширина спектра

sig_params
// -

{{
    val (h2s, p_errs, sim_params) = P_err_vect(
        ion_params, sig_params, l_s)

    val plots = (p_errs zip ion_params).map({
        case (i, l) => {
          Scatter(h2s.data.toSeq, i.data.toSeq, l.label)
      }})

    plot(plots, PerrLay)
}}

// ### Случай 3

// +
var ion_params = Seq(
    IonParams("nigth", 2.4 * 1e11, 1e-2,       2.4 * 1e9, 0),
    IonParams("day",   1.4 * 1e12, 3 * 1e-3,   4.2 * 1e9, 0),
    IonParams("evi",   1.4 * 1e12, 3.6 * 1e-2, 5.0 * 1e10, 0),
    IonParams("ivil",  1.0 * 1e13, 5.0 * 1e-1, 5.0 * 1e12, 0),
    IonParams("ivib",  5.0 * 1e13, 1.0,        5.0 * 1e13, 0)
)

val l_s = 200

ion_params.map((r) => {
    r.sigma_d_nt = calc_sigma_d_nt(l_s, z_e, r.sigma_delta_N)
    r
})

ion_params

// +
var sig_params = SigParams(
    f_0 = 400 * 1e6,          // Гц
    R_T = 64.0 * 1e3,         // бит/с
    theta_0 = (60).toRadians, // NumPy использует радианы по-умолчанию
    B_S = 1.0,                // простые сигналы
    T_S = 0,
    F_0 = 0
)

sig_params.T_S     = 1.0 / sig_params.R_T            // c/бит
sig_params.F_0     = sig_params.B_S / sig_params.T_S // ширина спектра
// -

{{
    val (h2s, p_errs, sim_params) = P_err_vect(
        ion_params, sig_params, l_s)

    val plots = (p_errs zip ion_params).map({
        case (i, l) => {
          Scatter(h2s.data.toSeq, i.data.toSeq, l.label)
      }})

    plot(plots, PerrLay)
}}

def Perr_fdfk(h2: Double, gamma2: Double, F_d: Double, F_k: Double): ns.Tensor = {
      val R_T = 2.7 * 1e3
      val T_S = 1.0 / R_T
      val B_S = 1.0
      val F_0 = B_S / T_S
    
      P_err(
        ns.Tensor(h2),
        gamma2,
        eta_m(T_S, ns.Tensor(F_k)).data(0),
        eta_ch(F_0, ns.Tensor(F_k)).data(0),
        eta_d(F_0, ns.Tensor(F_d)).data(0)
      )
}

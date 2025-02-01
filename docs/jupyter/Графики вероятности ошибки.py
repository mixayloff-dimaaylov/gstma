# ---
# jupyter:
#   jupytext:
#     text_representation:
#       extension: .py
#       format_name: light
#       format_version: '1.5'
#       jupytext_version: 1.16.6
#   kernelspec:
#     display_name: Python 3 (ipykernel)
#     language: python
#     name: python3
# ---

# Графики помехоусточивости
# =========================
#
# Графики из монографии Маслов-Пашинцев.

# ## Импорты

# +
from math import pi, cos, sqrt
from scipy.special import erf, fresnel
from scipy.integrate import quad
import numpy as np
import pandas as pd

import matplotlib.pyplot as plt
# -

# ## Исходные данные

# +
# %matplotlib ipympl

# Марк:
# - 10^13 .. 10^14 эл/м^2 для нормальной ионосферы без возмущений
# - 10^14 .. 10^15 эл/м^2 для нормальной ионосферы со слабыми естественными
#   возмущениями
# - 10^15 .. 10^16 эл/м^2 для нормальной ионосферы с сильными возмущениями
sigma_d_nts = np.array([1.0, 10.0, 50.0, 100.0, 150.0, 1000.0]) * 1e13

# Марк: 0.44 Ггц, но больший диапазон интереснее
# f_0s = np.array([0.44, 0.5, 0.55, 0.6, 0.7]) * 1e9
f_0s = np.linspace(0.1, 10, 1000) * 1e9

l_ss = np.array([200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0])

# Марк: высота максимума ионизации, м
hm = 300 * 1e3
z_e = 500 * 1e3
z1 = hm - (z_e / 2)
z = z1 + z_e
# -

# ## СКО флуктуаций фазового фронта волны  на выходе неоднородного слоя $\sigma_{\varphi}$
#
# $$\sigma_{\phi} = {
# {
#   80.8
#     \cdot \pi^{5/4}
#     \cdot \left(
#             l_s
#               \cdot z_{э}
#               \cdot \sec{\theta_0}
#           \right) ^{1/2}
#     \cdot \sigma_{\Delta N}
# }
#   \over
# {
#   c \cdot f_0
# }
# }$$
#
# $$\sigma_{\phi} = {
#   \left( {80.8 \cdot \pi} \over {C} \right)
#     \cdot \left( \sigma_{\Delta N_t} \over f_0 \right)
#     \cdot \sec{\theta_0}
# }$$

# +
C = 299792458.0


def sigma_phi(f_0, sigma_d_nt, theta_0):
    return (80.8 * pi / C) * (sigma_d_nt / f_0) * np.sqrt(1.0 / np.cos(theta_0))


fig, ax = plt.subplots()
theta_0 = np.radians(90.0)

for s in np.nditer(sigma_d_nts):
    sigma_phis = sigma_phi(f_0s, s * len(f_0s), theta_0)
    ax.semilogy(f_0s, sigma_phis,
                label="$\\sigma_{\\Delta N_t}$ = %.2g, $эл/м^2$" % s)

ax.set_title(r"$\sigma_{\phi}(f_0)$, $Рад$")
ax.set_xlabel(r"$f_0$, $Гц$")
ax.set_ylabel(r"$\sigma_{\phi}$, $Рад$")
ax.legend()


# -

# ## Коэффициент нарастания дифракционных эффектов во фронте волны внутри ионосферы и за ней до точки приёма $d_1^2$

# +
# old
def d1_2_old(f_0, z, z_e, l_s):
    return (3 * (z ** 2) - 3 * z * z_e + (z_e ** 2)) / \
             (6 * pow(2 * pi * f_0 / C, 2)) * (l_s ** (-4) / 8)


# modified
def d1_2_modified(f_0, z, z_e, l_s):
    return (3 * (z ** 2) - 3 * z * z_e + (z_e ** 2)) / \
             (6 * pow(2 * pi * f_0 / C, 2)) * 32 * (l_s ** (-4))


fig, ax = plt.subplots(1, 2, figsize=(9, 4))
for l_s in np.nditer(l_ss):
    d1_2s_old = d1_2_old(f_0s, z1, z_e, l_s)
    d1_2s_modified = d1_2_modified(f_0s, z1, z_e, l_s)

    ax[0].semilogy(f_0s, d1_2s_old, label="$l_s$ = %.3g, $м$" % l_s)
    ax[1].semilogy(f_0s, d1_2s_modified, label="мод., $l_s$ = %.3g, $м$" % l_s)

ax[0].set_title(r"$d_1^2(f_0)$")
ax[1].set_title(r"$d_{1, мод}^2(f_0)$")

for axes in ax:
    axes.set_xlabel(r"$f_0$, $Гц$")
    axes.set_ylabel(r"$l_s$, $м$")
    axes.legend()

fig.tight_layout()


# -

# ## Традиционный интервал частотной корреляции замираний в однолучевой ДКМ радиолинии $F_{k_0}$

# +
# old
def F_k0_old(f_0, sigma_d_nt, theta_0, z, z_e, l_s):
    sqr = np.sqrt(2 + d1_2_old(f_0, z, z_e, l_s))
    s = sigma_phi(f_0, sigma_d_nt, theta_0)

    return f_0 / (s * sqr)


# modified
def F_k0_modified(f_0, sigma_d_nt, theta_0, z, z_e, l_s):
    sqr = np.sqrt(2 + d1_2_modified(f_0, z, z_e, l_s))
    s = sigma_phi(f_0, sigma_d_nt, theta_0)

    return f_0 / (s * sqr)


fig, ax = plt.subplots(1, 2, figsize=(9, 4))
theta_0 = np.radians(90.0)

# TODO:
l_s = 380
for s in np.nditer(sigma_d_nts):
    F_k0s_old = F_k0_old(f_0s, s, theta_0, z, z_e, l_s)
    F_k0s_modified = F_k0_modified(f_0s, s, theta_0, z, z_e, l_s)

    ax[0].loglog(f_0s, F_k0s_old,
            label="$\\sigma_{\\Delta N_t}$ = %.2g, $эл/м^2$" % s)
    ax[1].loglog(f_0s, F_k0s_modified,
            label="$\\sigma_{\\Delta N_t}$ = %.2g, $эл/м^2$" % s)

ax[0].set_title(r"$F_{k_0}(f_0)$, $Гц$")
ax[1].set_title(r"$F_{k_{0}, мод.}(f_0)$, $Гц$")

for axes in ax:
    axes.set_xlabel(r"$f_0$, $Гц$")
    axes.set_ylabel(r"$F_{k_0}$, $Гц$")
    axes.legend()

fig.tight_layout()


# -

# ## Понижающий коэффициент $\Delta F_{k_0}$

# +
# new
def dF_k0(f_0, sigma_d_nt, theta_0):
    s = sigma_phi(f_0, sigma_d_nt, theta_0)
    s2 = s ** 2
    v = 1 - np.exp(-s2) + np.exp(1 - s2)
    lg = np.log(v)

    return np.sqrt(1 - lg)


fig, ax = plt.subplots()
theta_0 = np.radians(90.0)

# TODO
for s in np.nditer(sigma_d_nts):
    dF_k0s = dF_k0(f_0s, s, theta_0)

    ax.plot(f_0s, dF_k0s,
            label="$\\sigma_{\\Delta N_t}$ = %.2g, $эл/м^2$" % s)

ax.set_title(r"$\delta F_{k_0}(f_0)$, $Гц$")
ax.set_xlabel(r"$f_0$, $Гц$")
ax.set_ylabel(r"$\delta F_{k_0}$, $Гц$")
ax.legend()


# -

# ## Интервал частотной корреляции замираний в однолучевой ДКМ радиолинии $F_k$

# +
# old
def F_k_old(f_0, sigma_d_nt, theta_0, z, z_e, l_s):
    _F_k0 = F_k0_old(f_0, sigma_d_nt, theta_0, z, z_e, l_s)

    return _F_k0


# modified
def F_k_modified(f_0, sigma_d_nt, theta_0, z, z_e, l_s):
    _F_k0 = F_k0_modified(f_0, sigma_d_nt, theta_0, z, z_e, l_s)
    _dF_k0 = dF_k0(f_0, sigma_d_nt, theta_0)

    return _F_k0 * _dF_k0


fig, ax = plt.subplots(1, 2, figsize=(9, 4))
theta_0 = np.radians(90.0)

# TODO:
l_s = 380
for s in np.nditer(sigma_d_nts):
    F_ks_old = F_k_old(f_0s, s, theta_0, z, z_e, l_s)
    F_ks_modified = F_k_modified(f_0s, s, theta_0, z, z_e, l_s)

    ax[0].loglog(f_0s, F_ks_old,
            label="$\\sigma_{\\Delta N_t}$ = %.2g, $эл/м^2$" % s)
    ax[1].loglog(f_0s, F_ks_modified,
            label="$\\sigma_{\\Delta N_t}$ = %.2g, $эл/м^2$" % s)

ax[0].set_title(r"$F_k(f_0)$, $Гц$")
ax[1].set_title(r"$F_{k, мод.}(f_0)$, $Гц$")

for axes in ax:
    axes.set_xlabel(r"$f_0$, $Гц$")
    axes.set_ylabel(r"$F_k$, $Гц$")
    axes.legend()

fig.tight_layout()
# -

# ## Зависимость интервала частотной корреляции $F_k$ замираний и понижающего коэффициента $\Delta F_{k_0}$ от отношения $f_0 / f_m$ при $\beta_{и}$

# +
# F_k_F_k_0 (модифицированная)
theta_0 = np.radians(90.0)

f_m = (15 * 1e6)
f_0s = np.linspace(0.2, 1.0, 1000) * f_m
f_0m = f_0s / f_m

# TODO:
l_s = 380
s = 1e13 # sigma_d_nt

fig, ax = plt.subplots()

ax.plot(f_0m, F_k_modified(f_0s, s, theta_0, z, z_e, l_s), label=r"мод. $F_k$")
ax.plot(f_0m, F_k0_modified(f_0s, s, theta_0, z, z_e, l_s), label=r"мод. $F_{k_0}$")
ax.plot(f_0m, dF_k0(f_0s, s, theta_0), label=r"$F_{k_0}$")

ax.set_title(r"$F_k(f_0)$, $F_{k_0}$, $Гц$")
ax.set_xlabel(r"$f_0 / f_m$, $n$")
ax.set_ylabel(r"$F_k(f_0)$, $F_{k_0}$, $Гц$")
ax.legend()


# -

# ## Какой интеграл Френеля взять
#
# Интеграл Френеля в книге отличается от того, что предлагает Scipy.

# +
def Fp(x):
    def fp(_x):
        return cos(_x) / sqrt(_x)

    results = []

    for i in x:
        _v = quad(fp, 0.0, i)[0]
        results.append((1.0 / (2.0 * pi)) * _v)

    return np.array(results)


def _show():
    xs = np.linspace(0.1, 10.0, 1000)

    S, C = fresnel(xs)
    Cp = Fp(xs)
    Cpm = 2.5 * Fp(xs * pi)
    handles = []

    fig, ax = plt.subplots(1, 2, figsize=(8.5, 5), layout='constrained')
    for d, l in zip((C, Cp, Cpm),
                    (r"Из Scipy", r"Из книги", r"Из книги (модифицированная)")):
        ax[0].semilogx(xs, d, label=l)

    FF = np.linspace(0.1, 6.0, 1000)
    eta_d = (pi / (2.0 * FF ** 2)) * np.power(fresnel(FF ** 2)[1] ** 2, 2.0)
    eta_dp = (pi / (2.0 * FF ** 2)) * np.power(Fp(FF ** 2), 2.0)

    for d, l in zip((eta_d, eta_dp, eta_d),
                    (r"Из Scipy", r"Из книги", r"Из книги (модифицированная)")):
        ax[1].semilogx(FF, d, label=l)

    handles, _ = ax[0].get_legend_handles_labels()

    fig.legend(loc='upper center', ncols=3, handles=handles)
    fig.tight_layout()


_show()
# -

# ## Оценка помехоусточивости
#
# ### Исходные данные
#
# **Параметры ионосферы:**
#
# -   Максимум средней ЭК ${\bar{N_m}}$, $эл/м^3$;
# -   Интенсивность неоднородностей $\beta$;
# -   СКО флуктуаций ЭК ${\sigma_{\Delta N}}$, $эл/м^3$.

# +
ion_params = pd.DataFrame(
    columns=["label", "N_m", "betta", "sigma_delta_N"],
    data=[
                      # эл/м^3 #           # = betta * N_m, эл/м^3
        ("nigth", 2.4 * 1e11,  1e-2,       2.4 * 1e9),
        ("day",   1.4 * 1e12,  3 * 1e-3,   4.2 * 1e9),
        ("evi",   1.4 * 1e12,  3.6 * 1e-2, 5.0 * 1e10),
        ("ivil",  1.0 * 1e13,  5.0 * 1e-1, 5.0 * 1e12),
        ("ivib",  5.0 * 1e13,  1.0,        5.0 * 1e13)])

l_s = 200


def calc_sigma_d_nt(l_s, z_e, sigma_d_n):
    return np.sqrt(np.sqrt(pi) * l_s * z_e) * sigma_d_n


ion_params["sigma_d_nt"] = calc_sigma_d_nt(l_s, z_e, ion_params.sigma_delta_N)

ion_params
# -

# **Параметры сигналов:**
#
# -   Несущая частота $f_0$, $Гц$;
# -   Скорость передачи $R_T = 1 / T_S$, $бит/с$;
# -   Период передачи 1 бит $T_S$, $с$;
# -   Ширина спектра $F_0 = B_S / T_S$, $Гц$;
# -   Угол возвышения $\theta_0$, $градусы$.

# +
f_0 = 400 * 1e6 # Гц
R_T = 2.7 * 1e3 # бит/с
T_S = 1.0 / R_T # c/бит
theta_0 = 60.0  # градусов
theta_0 = np.radians(theta_0) # NumPy использует радианы по-умолчанию

B_S = 1.0       # простые сигналы
F_0 = B_S / T_S # ширина спектра

sigma_d_nts = ion_params.sigma_d_nt
sigma_phis = sigma_phi(f_0, sigma_d_nts, theta_0)
# -

# ### Зависимость СКО флуктуаций фазового фронта волны $\sigma_\varphi$ от sigma_d_nts

# +
fig, ax = plt.subplots()

ax.plot(sigma_d_nts, sigma_phis)
ax.set_title(r"$\sigma_{\phi}(\sigma_{\Delta N_t})$, $Рад$")
ax.set_xlabel(r"$\sigma_{\Delta N_t}$, $эл/м^2$")
ax.set_ylabel(r"$\sigma_{\phi}$, $Рад$")


# -

# ### Глубина общих БЗ $\gamma^2$
#
# $$\gamma^2 = {{1} \over {\exp{\sigma_{\phi}^2} - 1}}$$

# +
def gamma2(sigma_phi):
    return 1.0 / (np.exp(np.power(sigma_phi, 2)) - 1)


gamma_2s = gamma2(sigma_phis)

fig, ax = plt.subplots()
ax.semilogx(sigma_phis, gamma_2s)

ax.set_title(r"$\gamma^2(\sigma_{\phi})$")
ax.set_xlabel(r"$\sigma_{\phi}$, $Рад$")
ax.set_ylabel(r"$\gamma^2$")
# -

# ### Полоса когерентности F_К
#
# $$F_{К} = {
#   {f_0^2 \cdot c}
#     \over
#   {
#     80.8
#       \cdot \pi^{5/4}
#       \cdot \left(
#               2 \cdot l_s \cdot z_{э} \cdot \sec{\theta_0}
#             \right) ^{1/2}
#       \cdot \sigma_{\Delta N}
#       \cdot Д_1
#   }
# }$$
#
# $$Д_1 = {1+d_1^2 \over 2}$$
#
# $$d_1^2 = {
#   {
#     \left(
#       3 \cdot z^2 - 3 \cdot z \cdot z_{э} + z_{э}^2
#     \right)
#     \cdot c^2
#     \cdot \sec{\theta_0}^2
#   }
#     \over
#   { 192 \cdot \pi^2 \cdot f_0^2 \cdot l_s^4 }
# }$$
#
# $$F_{К} = {
#   {f_0}
#     \over
#   {
#     \sigma_{\phi} \cdot \sqrt{2 + d_1^2}
#   }
# }$$
#
# $$d_1^2 = {
#   {{
#     \left(
#       3 \cdot z^2 - 3 \cdot z \cdot z_{э} + z_{э}^2
#     \right)
#     \cdot c^2
#     \cdot \sec{\theta_0}^2
#   }
#     \over
#   { 6 \cdot (\pi \cdot f_0 / C)^2}}
#   \cdot (l_s^{-4} / 8)
# }$$

# +
F_ks = F_k_old(f_0, sigma_d_nts, theta_0, z, z_e, l_s)

fig, ax = plt.subplots()
ax.semilogx(sigma_d_nts, F_ks)

ax.set_title(r"$F_k(\sigma_{\Delta N_t})$, $Гц$")
ax.set_xlabel(r"$\sigma_{\Delta N_t}$, $эл/м^2$")
ax.set_ylabel(r"$F_k$, $Гц$")


# -

# ### Полоса диспертности $F_{Д}$
#
# $$F_{д} = {
#   \left(
#     {c \cdot f_{0}^3}
#       \over
#     {
#       80.8
#         \cdot \pi
#         \cdot \bar{N_m}
#         \cdot z_{э}
#         \cdot \sec{\theta_0}
#     }
#   \right) ^{1/2}
# }$$
#
# $$F_{Д} = {
#   \sqrt{
#     {C \cdot f_{0}^3}
#       \over
#     {
#       80.8 \cdot \pi \cdot \bar{N_m} \cdot z_{э} \cdot \sec{\theta_0}
#     }
#   }
# }$$

# +
def F_d(f_0, N_m, z_e, theta_0):
    return np.sqrt(C * np.power(f_0, 3) / (80.8 * pi * N_m * z_e * (1.0 / np.cos(theta_0))))


N_ms = ion_params.N_m
F_ds = F_d(f_0, N_ms, z_e, theta_0)

fig, ax = plt.subplots()
ax.plot(N_ms, F_ds)

ax.set_title(r"$F_d(N_m)$, $Гц$")
ax.set_xlabel(r"$N_m$")
ax.set_ylabel(r"$F_d$, $Гц$")
# -

# ### F_0 / F_k

F_0_over_F_k = F_0 / F_ks

# ### F_0 / F_d

F_0_over_F_d = F_0 / F_ds


# ### Степень МСИ $\eta_{м}$
#
# $$\eta_{м} = {
#   {{1} \over {2 \cdot \pi^2}}
#     \cdot \left( {{1} \over {T_S \cdot F_K}} \right)^2
#     \cdot erf \left( {\pi \cdot T_S \cdot F_K} \right) -
#   {{1} \over {\pi \cdot \sqrt{\pi}}}
#     \cdot \left( {{1} \over {T_S \cdot F_K}} \right)
#     \cdot \exp(- (\pi \cdot T_S \cdot F_K)^2)
# }$$

# +
# Зависи от 1 / T_s F_k
def eta_m(_T_S, _F_K):
    _v = 1.0 / (_T_S * _F_K)
    _t = pi * _T_S * _F_K
    return 1.0 / (2 * pi * pi) * np.power(_v, 2) \
             * erf(_t) \
           - 1.0 / (pi * np.sqrt(pi)) * _v * np.exp(-1.0 * _t ** 2)


eta_ms = eta_m(T_S, F_ks)

fig, ax = plt.subplots()
ax.semilogx(F_ks, eta_ms)

ax.set_title(r"$\eta_{m}(F_k)$")
ax.set_xlabel(r"$F_k$, $Гц$")
ax.set_ylabel(r"$\eta_{m}$")


# -

# ### Степень ЧСЗ $\eta_{ч}$
#
# $$\eta_{ч} = {
#   \left[ 1 + {{1} \over {2 \cdot \pi^2 }} \cdot \left( F_0 \over F_k \right) ^2 \right]
#     \cdot erf \left({ {\pi \cdot F_k} \over {F_0}} \right) -
#   {{1} \over {\pi \cdot \sqrt{\pi}}}
#     \cdot \left( F_0 \over F_{к} \right)
#     \cdot \left( 2 - \exp \left(- { \left( {{\pi \cdot F_{к}} \over {F_0}} \right)}^2 \right) \right)
# }$$

# +
# Зависит от F_0, F_k, но это тоже функции
def eta_ch(_F_0, _F_k):
    _v = pi * _F_k / _F_0

    return (1.0 + (1 / 2 * pi ** 2) * np.power(_F_0 / _F_k, 2)) \
             * erf(_v) \
             - 1.0 / (pi * np.sqrt(pi)) * (_F_0 / _F_k) \
             * (2.0 - np.exp(-np.power(_v, 2)))


eta_chs = eta_ch(F_0, F_ks)

fig, ax = plt.subplots()
ax.semilogx(F_ks, eta_chs)

ax.set_title(r"$\eta_{ch}(F_k)$")
ax.set_xlabel(r"$F_k$, $Гц$")
ax.set_ylabel(r"$\eta_{ch}$")


# -

# ### Степень ДИ $\eta_{д}$
#
# $$\eta_{д} = {
#   {\pi \cdot \left[ C_2 \left( F_0 \over F_{д} \right)^2 \right]^2 }
#     \over {2 \cdot \left( F_0 \over F_{д} \right)^2}
# }$$

# зависит от F_0, F_d
def eta_d(_F_0, _F_d):
    _v = np.power(_F_0 / _F_d, 2)
    _C_2 = fresnel(np.sqrt(2 * _v / pi))[1]
    return (pi * np.power(_C_2, 2)) / (2.0 * _v)


# Зависимость коэффициента энергетических потерь $\eta_{д}$ при НК обработке от степени их ДИ (Рис. 4.10., с. 253).

# +
_F_ds = np.linspace(10 * F_0, 0.01 * F_0, 100000)
_eta_ds = eta_d(F_0, _F_ds)

fig, ax = plt.subplots(figsize=(8, 4))
ax.semilogx(F_0 / _F_ds, _eta_ds)
ax.grid(True, which="both", ls="--", lw=0.6)

ax.set_title(r"$\eta_{d}(F_0 / F_d)$")
ax.set_xlabel(r"$F_0 / F_d$")
ax.set_ylabel(r"$\eta_{d}$")
# -

# Для дальнейших расчетов:

eta_ds = eta_d(F_0, F_ds)

# ### Построение графиков вероятности ошибки
#
# $$P_{ош} = {
#   0.25 \cdot \left( P_{111} + P_{110} + P_{011} + P_{010} \right)
# }$$

type(F_ds)

# +
sim_params = np.vstack((
    sigma_d_nts.to_numpy().reshape(1, 5),
    sigma_phis.to_numpy().reshape(1, 5),
    gamma_2s.to_numpy().reshape(1, 5),
    F_ks.to_numpy().reshape(1, 5),
    F_ds.to_numpy().reshape(1, 5),
    F_0_over_F_k.to_numpy().reshape(1, 5),
    F_0_over_F_d.to_numpy().reshape(1, 5),
    eta_ms.to_numpy().reshape(1, 5),
    eta_chs.to_numpy().reshape(1, 5),
    eta_ds.to_numpy().reshape(1, 5)))

sim_params


# +
# Зависит от h2, gamma2, eta_ч, eta_м, eta_d
# Либо Зависит от h2, f_0, F_0, T_s, N_m, sigma_delta_nt
def P_err(_h2s, _gamma2, _eta_ms, _eta_chs, _eta_ds):
    _g = _gamma2
    _g_1 = _g + 1
    _p = lambda _w: (_g_1) / (_w + 2.0 * _g_1) * np.exp(-1.0 * _g * _w / (_w + 2.0 * _g_1))

    W111 = _h2s * _eta_ds * _eta_chs
    W110 = (_h2s * _eta_ds * _eta_chs - _h2s * _eta_ds * _eta_ms) / (1.0 + _h2s * _eta_ds * _eta_ms)
    W011 = W110
    W010 = (_h2s * _eta_ds * _eta_chs - 2 * _h2s * _eta_ds * _eta_ms) / (1.0 + 2.0 * _h2s * _eta_ds * _eta_ms)

    P111 = _p(W111)
    P110 = _p(W110)
    P011 = _p(W011)
    P010 = _p(W010)

    return 0.25 * (P111 + P110 + P011 + P010)


h2s = np.logspace(0.0, 6.0, 1000)
P_errs = P_err(h2s.reshape(-1, 1).T,
               sim_params[2].reshape(-1, 1),
               sim_params[7].reshape(-1, 1),
               sim_params[8].reshape(-1, 1),
               sim_params[9].reshape(-1, 1))

fig, ax = plt.subplots()

for i, l in zip(P_errs, ion_params.label):
    ax.loglog(h2s, i, label=l)

ax.set_ylim(top=1e0, bottom=1e-6)

ax.set_title(r"$P_{err}(h^2)$")
ax.set_xlabel(r"$h^2$")
ax.set_ylabel(r"$P_{err}$")
ax.grid(True, which="both", ls="--", lw=0.6)
ax.legend()


# -

# ## Сравнение графиков вероятностей

# +
def calc_sigma_d_nt(l_s, z_e, sigma_d_n):
    return np.sqrt(np.sqrt(pi) * l_s * z_e) * sigma_d_n


def P_err_vect(ion_params, sig_params, l_s):
    f_0 =     sig_params.f_0[0]
    R_T =     sig_params.R_T[0]
    T_S =     sig_params.T_S[0]
    theta_0 = sig_params.theta_0[0]
    theta_0 = sig_params.theta_0[0]
    B_S =     sig_params.B_S[0]
    F_0 =     sig_params.F_0[0]

    sigma_d_nts = ion_params[:, 3]
    sigma_phis = sigma_phi(f_0, sigma_d_nts, theta_0)
    gamma_2s = gamma2(sigma_phis)
    F_ks = F_k_old(f_0, sigma_d_nts, theta_0, z, z_e, l_s)
    N_ms = ion_params[:, 0]
    F_ds = F_d(f_0, N_ms, z_e, theta_0)
    F_0_over_F_k = F_0 / F_ks
    F_0_over_F_d = F_0 / F_ds
    eta_ms = eta_m(T_S, F_ks)
    eta_chs = eta_ch(F_0, F_ks)
    eta_ds = eta_d(F_0, F_ds)

    sim_params = np.vstack((
        sigma_d_nts.reshape(1, 5),
        sigma_phis.reshape(1, 5),
        gamma_2s.reshape(1, 5),
        F_ks.reshape(1, 5),
        F_ds.reshape(1, 5),
        F_0_over_F_k.reshape(1, 5),
        F_0_over_F_d.reshape(1, 5),
        eta_ms.reshape(1, 5),
        eta_chs.reshape(1, 5),
        eta_ds.reshape(1, 5)))

    h2s = np.logspace(0.0, 6.0, 1000)
    P_errs = P_err(h2s.reshape(-1, 1).T,
                   sim_params[2].reshape(-1, 1),
                   sim_params[7].reshape(-1, 1),
                   sim_params[8].reshape(-1, 1),
                   sim_params[9].reshape(-1, 1))

    return (h2s, P_errs, sim_params)


# -

# ### Случай 1

# +
ion_params = pd.DataFrame(
    columns=["label", "N_m", "betta", "sigma_delta_N"],
    data=[
                  # эл/м^3    #           # = betta * N_m, эл/м^3
        ("nigth", 2.4 * 1e11, 1e-2,       2.4 * 1e9),
        ("day",   1.4 * 1e12, 3 * 1e-3,   4.2 * 1e9),
        ("evi",   1.4 * 1e12, 3.6 * 1e-2, 5.0 * 1e10),
        ("ivil",  1.0 * 1e13, 5.0 * 1e-1, 5.0 * 1e12),
        ("ivib",  5.0 * 1e13, 1.0,        5.0 * 1e13)])

l_s = 200

ion_params["sigma_d_nt"] = calc_sigma_d_nt(l_s, z_e, ion_params.sigma_delta_N)

ion_params

# +
sig_params = pd.DataFrame()
sig_params["f_0"]     = [400 * 1e6]                     # Гц
sig_params["R_T"]     = [2.7 * 1e3]                     # бит/с
sig_params["T_S"]     = 1.0 / sig_params.R_T            # c/бит
sig_params["theta_0"] = np.radians([60])                # NumPy использует радианы по-умолчанию
sig_params["B_S"]     = [1.0]                           # простые сигналы
sig_params["F_0"]     = sig_params.B_S / sig_params.T_S # ширина спектра

sig_params

# +
h2s, P_errs, sim_params = P_err_vect(
    ion_params.drop(columns=["label"]).to_numpy(), sig_params, l_s)

fig, ax = plt.subplots()

for i, l in zip(P_errs, ion_params.label):
    ax.loglog(h2s, i, label=l)

ax.set_ylim(top=1e0, bottom=1e-6)
ax.set_title(r"$P_{err}(h^2)$")
ax.set_xlabel(r"$h^2$")
ax.set_ylabel(r"$P_{err}$")
ax.grid(True, which="both", ls="--", lw=0.6)
ax.legend()
# -

# ### Случай 2

# +
ion_params = pd.DataFrame(
    columns=["label", "N_m", "betta", "sigma_delta_N"],
    data=[
                  # эл/м^3    #           # = betta * N_m, эл/м^3
        ("nigth", 2.4 * 1e11, 1e-2,       2.4 * 1e9),
        ("day",   1.4 * 1e12, 3 * 1e-3,   4.2 * 1e9),
        ("evi",   1.4 * 1e12, 3.6 * 1e-2, 5.0 * 1e10),
        ("ivil",  1.0 * 1e13, 5.0 * 1e-1, 5.0 * 1e12),
        ("ivib",  5.0 * 1e13, 1.0,        5.0 * 1e13)])

l_s = 200

ion_params["sigma_d_nt"] = calc_sigma_d_nt(l_s, z_e, ion_params.sigma_delta_N)

ion_params

# +
sig_params = pd.DataFrame()
sig_params["f_0"]     = [400 * 1e6]                     # Гц
sig_params["R_T"]     = [9.6 * 1e3]                     # бит/с
sig_params["T_S"]     = 1.0 / sig_params.R_T            # c/бит
sig_params["theta_0"] = np.radians([60])                # NumPy использует радианы по-умолчанию
sig_params["B_S"]     = [1.0]                           # простые сигналы
sig_params["F_0"]     = sig_params.B_S / sig_params.T_S # ширина спектра

sig_params

# +
h2s, P_errs, sim_params = P_err_vect(
    ion_params.drop(columns=["label"]).to_numpy(), sig_params, l_s)

fig, ax = plt.subplots()

for i, l in zip(P_errs, ion_params.label):
    ax.loglog(h2s, i, label=l)

ax.set_ylim(top=1e0, bottom=1e-6)
ax.set_title(r"$P_{err}(h^2)$")
ax.set_xlabel(r"$h^2$")
ax.set_ylabel(r"$P_{err}$")
ax.grid(True, which="both", ls="--", lw=0.6)
ax.legend()
# -

# ### Случай 3

# +
ion_params = pd.DataFrame(
    columns=["label", "N_m",   "betta", "sigma_delta_N"],
    data=[
                # эл/м^3    #           # = betta * N_m, эл/м^3
      ("nigth", 2.4 * 1e11, 1e-2,       2.4 * 1e9),
      ("day",   1.4 * 1e12, 3 * 1e-3,   4.2 * 1e9),
      ("evi",   1.4 * 1e12, 3.6 * 1e-2, 5.0 * 1e10),
      ("ivil",  1.0 * 1e13, 5.0 * 1e-1, 5.0 * 1e12),
      ("ivib",  5.0 * 1e13, 1.0,        5.0 * 1e13)])

l_s = 200

ion_params["sigma_d_nt"] = calc_sigma_d_nt(l_s, z_e, ion_params["sigma_delta_N"])

ion_params

# +
sig_params = pd.DataFrame()
sig_params["f_0"]     = [400 * 1e6]                     # Гц
sig_params["R_T"]     = [64.0 * 1e3]                    # бит/с
sig_params["T_S"]     = 1.0 / sig_params.R_T            # c/бит
sig_params["theta_0"] = np.radians([60])                # NumPy использует радианы по-умолчанию
sig_params["B_S"]     = [1.0]                           # простые сигналы
sig_params["F_0"]     = sig_params.B_S / sig_params.T_S # ширина спектра

sig_params

# +
h2s, P_errs, sim_params = P_err_vect(
    ion_params.drop(columns=["label"]).to_numpy(), sig_params, l_s)

fig, ax = plt.subplots()

for i, l in zip(P_errs, ion_params.label):
    ax.loglog(h2s, i, label=l)

ax.set_ylim(top=1e0, bottom=1e-6)
ax.set_title(r"$P_{err}(h^2)$")
ax.set_xlabel(r"$h^2$")
ax.set_ylabel(r"$P_{err}$")
ax.grid(True, which="both", ls="--", lw=0.6)
ax.legend()

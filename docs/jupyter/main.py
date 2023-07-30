#!/usr/bin/env python

from math import pi
import numpy as np

import matplotlib.pyplot as plt


C = 299792458.0


def sigma_phi(f_0, sigma_d_nt):
    return (80.8 * pi / C) * (sigma_d_nt / f_0)


def d1_2(f_0, z, z_e, l_s):
    return (3 * (z ** 2) - 3 * z * z_e + (z_e ** 2)) / \
             (6 * pow(2 * pi * f_0 / C, 2)) * 32 * (l_s ** (-4))


def F_k0(f_0, sigma_d_nt, z, z_e, l_s):
    sqr = np.sqrt(2 + d1_2(f_0, z, z_e, l_s))
    s = sigma_phi(f_0, sigma_d_nt)

    return f_0 / (s * sqr)


def dF_k0(f_0, sigma_d_nt):
    s = sigma_phi(f_0, sigma_d_nt) ** 2
    s2 = s ** 2
    v = 1 - np.exp(-s2) + np.exp(1 - s2)
    lg = np.log(v)

    return np.sqrt(1 - lg)


def F_k(f_0, sigma_d_nt, z, z_e, l_s):
    _F_k0 = F_k0(f_0, sigma_d_nt, z, z_e, l_s)
    _dF_k0 = dF_k0(f_0, sigma_d_nt)

    return _F_k0 * _dF_k0


# def gamma2(sigma_phi):
#     return 1.0 / (np.exp(np.pow(sigma_phi, 2)) - 1)
#
#
# def eta_ч():
#     _F_0 = F_0() # TODO: что такое
#     _F_k = F_k()
#     _v = pi * _F_k / _F_0
#
#     return (1 + (1 / 2 * pi ** 2) * np.pow(_F_0 / _F_k, 2)) \
#              * np.erf(_v) \
#              - 1 / (pi * sqrt(pi)) * (_F_0 / _F_k) \
#              * (2 - np.exp(-np.pow(_v, 2)))
#
#
# def eta_м():
#     _T_s =
#     _F_k = F_k()
#
#     return 1 / (2 pi * pi) *


def plot_sigma_phi(fig, ax):
    fname = r"static/sigma_phi.png"

    for s in np.nditer(sigma_d_nts):
        sigma_phis = sigma_phi(f_0s, s * len(f_0s))

        ax.plot(f_0s, sigma_phis,
                label="при $\\sigma_{\\Delta N_t}$ = %s, $Рад$" % s)

    ax.set_title(r"$\sigma_{\phi}(f_0)$, $Рад$")
    ax.set_xlabel(r"$f_0$, $Гц$")
    ax.set_ylabel(r"$\sigma_{\phi}$, $эл/м^2$")
    ax.legend()

    fig.set_size_inches(10, 10)
    plt.savefig(fname)


def plot_d1_2(fig, ax):
    fname = r"static/d_1-2.png"

    for l_s in np.nditer(l_ss):
        d1_2s = d1_2(f_0s, z1, z_e, l_s)

        ax.plot(f_0s, d1_2s, label="при $l_s$ = %s, $м$" % l_s)

    ax.set_title(r"$d_1^2(f_0)$")
    ax.set_xlabel(r"$f_0$, $Гц$")
    ax.set_ylabel(r"$l_s$, $м$")
    ax.legend()

    fig.set_size_inches(10, 10)
    plt.savefig(fname)


def plot_F_k0(fig, ax):
    fname = r"static/F_k_0.png"

    # TODO:
    l_s = 380
    for s in np.nditer(sigma_d_nts):
        F_k0s = F_k0(f_0s, s, z, z_e, l_s)

        ax.plot(f_0s, F_k0s,
                label="при $\\sigma_{\\Delta N_t}$ = %s, $Рад$" % s)

    ax.set_title(r"$F_{k_0}(f_0)$, $Гц$")
    ax.set_xlabel(r"$f_0$, $Гц$")
    ax.set_ylabel(r"$F_{k_0}$, $Гц$")
    ax.legend()

    fig.set_size_inches(10, 10)
    plt.savefig(fname)


def plot_dF_k0(fig, ax):
    fname = r"static/delta_F_k_0.png"

    # TODO
    for s in np.nditer(sigma_d_nts):
        dF_k0s = dF_k0(f_0s, s)

        ax.plot(f_0s, dF_k0s,
                label="при $\\sigma_{\\Delta N_t}$ = %s, $Рад$" % s)

    ax.set_title(r"$\delta F_{k_0}(f_0)$, $Гц$")
    ax.set_xlabel(r"$f_0$, $Гц$")
    ax.set_ylabel(r"$\delta F_{k_0}$, $Гц$")
    ax.legend()

    fig.set_size_inches(10, 10)
    plt.savefig(fname)


def plot_F_k(fig, ax):
    fname = r"static/F_k.png"

    # TODO:
    l_s = 380
    for s in np.nditer(sigma_d_nts):
        F_ks = F_k(f_0s, s, z, z_e, l_s)

        ax.plot(f_0s, F_ks,
                label="при $\\sigma_{\\Delta N_t}$ = %s, $Рад$" % s)

    ax.set_title(r"$F_k(f_0)$, $Гц$")
    ax.set_xlabel(r"$f_0$, $Гц$")
    ax.set_ylabel(r"$F_k$, $Гц$")
    ax.legend()

    fig.set_size_inches(10, 10)
    plt.savefig(fname)


def plot_F_k_F_k_0(fig, ax):
    fname = r"static/F_k-F_k_0.png"

    f_m = (15 * 1e6)
    f_0s = np.linspace(0.2, 1.0, 20) * f_m
    f_0m = f_0s / f_m

    # TODO:
    l_s = 380
    s = 1e13 # sigma_d_nt

    ax.plot(f_0m, F_k(f_0s, s, z, z_e, l_s), label=r"$F_k$")
    ax.plot(f_0m, F_k0(f_0s, s, z, z_e, l_s), label=r"$F_{k_0}$")
    ax.plot(f_0m, dF_k0(f_0s, s), label=r"$F_{k_0}$")

    ax.set_title(r"$F_k(f_0)$, $F_{k_0}$, $Гц$")
    ax.set_xlabel(r"$f_0 / f_m$, $n$")
    ax.set_ylabel(r"$F_k(f_0)$, $F_{k_0}$, $Гц$")
    ax.legend()

    fig.set_size_inches(10, 10)
    plt.savefig(fname)


if __name__ == '__main__':
    # plt.rcParams.update({'font.size': 16})

    # Марк:
    # - 10^13 .. 10^14 эл/м^2 для нормальной ионосферы без возмущений
    # - 10^14 .. 10^15 эл/м^2 для нормальной ионосферы со слабыми естественными
    #   возмущениями
    # - 10^15 .. 10^16 эл/м^2 для нормальной ионосферы с сильными возмущениями
    sigma_d_nts = np.array([1.0, 10.0, 50.0, 100.0, 150.0, 1000.0]) * 1e13

    # Марк: 0.44 Ггц, но больший диапазон интереснее
    # f_0s = np.array([0.44, 0.5, 0.55, 0.6, 0.7]) * 1e9
    f_0s = np.linspace(0.2, 5.0) * 1e9

    l_ss = np.array([200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0])

    # Марк: высота максимума ионизации, м
    hm = 300 * 1e3
    z_e = 500 * 1e3
    z1 = hm - (z_e / 2)
    z = z1 + z_e

    # Sigma Phi
    fig, ax = plt.subplots()
    plot_sigma_phi(fig, ax)

    # d1^2
    fig, ax = plt.subplots()
    plot_d1_2(fig, ax)

    # F_k0
    fig, ax = plt.subplots()
    plot_F_k0(fig, ax)

    # dF_k0
    fig, ax = plt.subplots()
    plot_dF_k0(fig, ax)

    # F_k
    fig, ax = plt.subplots()
    plot_F_k(fig, ax)

    # F_k_F_k_0
    fig, ax = plt.subplots()
    plot_F_k_F_k_0(fig, ax)

    plt.show()

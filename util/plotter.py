#!/usr/bin/env python3

import os
import glob
import re
import csv
import statistics
import datetime as dt

import matplotlib.pyplot as plt
import numpy as np

C = 299792458.0


def waveLength(f):
    return C / f


def multiplier(f1, f2):
    f1_2 = f1 * f1
    f2_2 = f2 * f2

    return ((1e-16 * f1_2 * f2_2) / (40.308 * (f1_2 - f2_2)))


def adr_adr(adr1, adr2, f1, f2):
    return adr2 * waveLength(f2) - adr1 * waveLength(f1)


def k(adr1, adr2, f1, f2, psr1, psr2):
    return (psr2 - psr1) - adr_adr(adr1, adr2, f1, f2)


def DNT(adr1, adr2, f1, f2, psr1, psr2, length):
    return multiplier(f1, f2) * \
           statistics.mean(k(adr1, adr2, f1, f2, psr1, psr2))


def perf_cal(file_range, file_ismrawtec, file_satxyz2):
    file_a_sat = re.search('^range_([^\._]*)_[0-9]*_[0-9]*\.csv$', file_range).group(1)
    file_a_sat_system = re.search('^([A-Z]+)[0-9]+$', file_a_sat).group(1)

    if file_a_sat_system == 'GPS':
        f1 = 1575.42e6  # L1CA
        f2 = 1227.60e6  # L2C or L2P
        f5 = 1176.45e6  # L5Q

        # TODO: даже после добавления развилки не считает
        RDCB_L1L2 = 0.0  # 34.577472687 # L1CAL2C
        RDCB_L1L5 = 0.0  # 12.218264580 # L1CAL5
    elif file_a_sat_system == 'GLONASS':
        f1 = 1602.0e6 + -3 * 0.5625e6  # L1 -1
        f2 = 1246.0e6 + -3 * 0.4375e6  # L2 -1
        f5 = 1246.0e6 + -3 * 0.4375e6  # L2P -1

        RDCB_L1L2 = 0.0  # 14.522434235  # L1CAL2CA
        RDCB_L1L5 = 0.0  # 23.634117126  # L1CAL2P
    else:
        print("Неопределенный тип спутниковой системы.")
        exit(1)

    values_range = []

    with open(file_range) as csvfile:
        csvreader = csv.reader(csvfile, delimiter=',')
        for row in csvreader:
            values_range.append(row)

    values_ismrawtec = []

    with open(file_ismrawtec) as csvfile:
        csvreader = csv.reader(csvfile, delimiter=',')
        for row in csvreader:
            values_ismrawtec.append(row)

    # RANGE
    times_unix, psr1, psr2, psr5, adr1, adr2, adr5, sat = zip(*values_range)

    times = np.array([dt.datetime.fromtimestamp(int(ts)/1000) for ts in times_unix]).astype(dt.datetime)
    psr1 = np.array(psr1).astype(float)
    psr2 = np.array(psr2).astype(float)
    psr5 = np.array(psr5).astype(float)
    adr1 = np.array(adr1).astype(float)
    adr2 = np.array(adr2).astype(float)
    adr5 = np.array(adr5).astype(float)
    sat = np.array(sat).astype(str)

    # ISMRAWTEC
    ism_times_unix, ism_tec, ism_sat = zip(*values_ismrawtec)

    ism_times = np.array([dt.datetime.fromtimestamp(int(ts)/1000) for ts in ism_times_unix]).astype(dt.datetime)
    ism_tec = np.array(ism_tec).astype(float)
    ism_sat = np.array(ism_sat).astype(str)

    # Расчеты

    k12 = k(adr1, adr2, f1, f2, psr1, psr2)
    k15 = k(adr1, adr5, f1, f5, psr1, psr5)
    p12 = psr2 - psr1
    p15 = psr5 - psr1

    _DNT12 = statistics.mean(k12)
    _DNT15 = statistics.mean(k15)
    DNT12 = multiplier(f1, f2) * _DNT12
    DNT15 = multiplier(f1, f5) * _DNT15

    print("DNT12: " + str(DNT12))
    print("DNT15: " + str(DNT15))

    # --- drawings ---
    # fig, ax = plt.subplots()
    # ax.plot(times, [DNT12] * len(k12), label="DNT12(adr1, adr2, psr1, psr2")
    # ax.plot(times, [DNT15] * len(k15), label="DNT15(adr1, adr5, psr1, psr5")
    # ax.plot(times, p12, label="P1-P2")
    # ax.plot(times, p15, label="P1-P5")
    # ax.legend()

    NT12psr = multiplier(f1, f2) * p12 + RDCB_L1L2
    NT15psr = multiplier(f1, f5) * p15 + RDCB_L1L5

    # print(p12, p15)

    NT12adr_wo_DNT = multiplier(f1, f2) * adr_adr(adr1, adr2, f1, f2)
    NT15adr_wo_DNT = multiplier(f1, f5) * adr_adr(adr1, adr5, f1, f5)
    NT12adr = NT12adr_wo_DNT + DNT12 + RDCB_L1L2
    NT15adr = NT15adr_wo_DNT + DNT15 + RDCB_L1L5

    # plt.show()

    # dict_key = file_a_sat + '_' + str(times_unix[0]) + ' ' + str(times_unix[-1])
    result = {'sat': file_a_sat,
              'times': times,
              'ism_times': ism_times,
              'ism_tec': ism_tec,
              'NT12psr': NT12psr,
              'NT15psr': NT15psr,
              'NT12adr': NT12adr,
              'NT15adr': NT15adr}

    return result


def plot_build(sat):
    # --- drawings2 ---
    # TODO: странная поправка NT12psr -5
    fig, ax = plt.subplots()
    ax.set_title(f"ПЭСы спутника {sat['sat']}")
    ax.plot(sat['times'], sat['NT12psr'], label="NT(P1-P2)")
    ax.plot(sat['times'], sat['NT15psr'], label="NT(P1-P5)")
    ax.plot(sat['times'], sat['NT12adr'], label="NT(adr1 - adr2)")
    ax.plot(sat['times'], sat['NT15adr'], label="NT(adr1 - adr5)")
    ax.plot(sat['ism_times'], sat['ism_tec'], label="ISMRAWTEC's TEC")
    ax.legend()
    plt.title(f"ПЭСы спутника {sat['sat']}")
    plt.savefig(f"ПЭСы спутника {sat['sat']}")
    # fig.suptitle(f"ПЭСы спутника {sat['sat']}")

    # графики можно будет показать все разом


if __name__ == '__main__':
    # find files
    os.chdir('./rawdump/')
    files_range = glob.glob("range_*.csv")
    files_ismrawtec = glob.glob("ismrawtec_*.csv")
    files_satxyz2 = glob.glob("satxyz2_*.csv")

    for files in zip(files_range, files_ismrawtec, files_satxyz2):
        plot_build(perf_cal(*files))

    plt.show()

#!/usr/bin/env python
# coding: utf-8

# Пример взаимодействия с ClickHouse
# ==================================
# 
# На [основе][altinity].
# 
# [altinity]: https://altinity.com/blog/2019/2/25/clickhouse-and-python-jupyter-notebooks

# ## Исходная программа

# In[ ]:


#!/usr/bin/env python3

import os
import glob
import re
import csv
import statistics
import scipy.signal as sc
import datetime as dt

import matplotlib.pyplot as plt
import numpy as np

from math import pi

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


aa = [1.0,
      -5.5145351211661655,
      12.689113056515138,
      -15.593635210704097,
      10.793296670485379,
      -3.9893594042308829,
      0.6151231220526282]


ab = [0.00000004863987500780838,
      0.00000029183925004685027,
      0.00000072959812511712565,
      0.00000097279750015616753,
      0.00000072959812511712565,
      0.00000029183925004685027,
      0.00000004863987500780838]


da = [1.0,
      -3.4767608600037727,
      5.0801848641096203,
      -4.2310052826910152,
      2.2392861745041328,
      -0.69437337677433475,
      0.084273573849621822]


db = [0.076745906902313671,
      0.0,
      -0.23023772070694101,
      0.0,
      0.23023772070694101,
      0.0,
      -0.076745906902313671]


def avgNT(NT):
    # filter settings
    return sc.lfilter(ab, aa, NT)


def delNT(NT):
    # filter settings
    return sc.lfilter(db, da, NT)


def sigNT(dnt):
    v = np.lib.stride_tricks.sliding_window_view(dnt, 60)
    return v.std(axis=-1)


def sigPhi(sigNT, f):
    return 1e16 * 80.8 * pi * sigNT / (C * f)


def csvReadAsDict(file):
    columns = {}

    with open(file) as csvfile:
        csvreader = csv.reader(csvfile, delimiter=',')

        # read header
        headers = next(csvreader, None)

        if headers is None:
            print(f'File {file} is empty.')
            return columns

        for h in headers:
            columns[h] = []

        # fill header fields
        for row in csvreader:
            for h, v in zip(headers, row):
                columns[h].append(v)

    return columns


# Searches CSV files in ./rawdump/ dir and returns them as list of tuples, for
# each (satellite, from, to, secondaryfreq)
def read_csvs():
    # glob SHOULD sort them in synchronous order
    files_range = glob.glob("./rawdump/rawdata_range_*.csv")
    files_ismrawtec = glob.glob("./rawdump/rawdata_ismrawtec_*.csv")
    files_satxyz2 = glob.glob("./rawdump/rawdata_satxyz2_*.csv")

    return ({"range": csvReadAsDict(r),
             "ismrawtec": csvReadAsDict(rt),
             "satxyz2": csvReadAsDict(xyz)}
            for r, rt, xyz in zip(files_range, files_ismrawtec, files_satxyz2))


def perf_cal(values):
    values_range = values['range']
    values_ismrawtec = values['ismrawtec']
    values_satxyz2 = values['satxyz2']

    file_a_sat = values_range['sat'][0]
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

    # RANGE
    times = np.array([dt.datetime.fromtimestamp(int(ts)/1000) for ts in values_range['time']]).astype(dt.datetime)
    psr1 = np.array(values_range['psr1']).astype(float)
    psr2 = np.array(values_range['psr2']).astype(float)
    psr5 = np.array(values_range['psr5']).astype(float)
    adr1 = np.array(values_range['adr1']).astype(float)
    adr2 = np.array(values_range['adr2']).astype(float)
    adr5 = np.array(values_range['adr5']).astype(float)
    sat = np.array(values_range['sat']).astype(str)

    # ISMRAWTEC
    ism_times_unix, ism_tec, ism_sat = zip(*values_ismrawtec)

    ism_times = np.array([dt.datetime.fromtimestamp(int(ts)/1000) for ts in values_ismrawtec['time']]).astype(dt.datetime)
    ism_tec = np.array(values_ismrawtec['tec']).astype(float)
    ism_sat = np.array(values_ismrawtec['sat']).astype(str)

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

    avgNT12 = avgNT(NT12adr)
    avgNT15 = avgNT(NT15adr)

    delNT12 = delNT(NT12adr)
    delNT15 = delNT(NT15adr)

    sigNT12 = sigNT(delNT12)
    sigNT15 = sigNT(delNT15)

    sigPhi12 = sigPhi(sigNT12, f2)
    sigPhi15 = sigPhi(sigNT15, f5)

    # dict_key = file_a_sat + '_' + str(times_unix[0]) + ' ' + str(times_unix[-1])
    result = {'sat': file_a_sat,
              'times': times,
              'ism_times': ism_times,
              'ism_tec': ism_tec,
              'NT12psr': NT12psr,
              'NT15psr': NT15psr,
              'NT12adr': NT12adr,
              'NT15adr': NT15adr,
              'avgNT12': avgNT12,
              'avgNT15': avgNT15,
              'delNT12': delNT12,
              'delNT15': delNT15,
              'sigNT12': sigNT12,
              'sigNT15': sigNT15,
              'sigPhi12': sigPhi12,
              'sigPhi15': sigPhi15}

    return result


def plot_build(sat):
    sat_name = f" спутника {sat['sat']}"

    gfig, gax = plt.subplots()

    def dumpplot(xs, ys, vname):
        fig = plt.figure()
        plt.title(f"{vname}{sat_name}")
        plt.plot(xs, ys, label=vname)
        plt.legend()

        plt.title(f"{vname}{sat_name}")
        plt.savefig(f"./rawdump/{vname}{sat_name}.png")
        plt.close(fig)

        gax.plot(xs, ys, label=vname)

    dumpplot(sat['times'],      sat['NT12psr'],  "NT(P1-P2)")
    dumpplot(sat['times'],      sat['NT15psr'],  "NT(P1-P5)")
    dumpplot(sat['times'],      sat['NT12adr'],  "NT(adr1 - adr2)")
    dumpplot(sat['times'],      sat['NT15adr'],  "NT(adr1 - adr5)")
    dumpplot(sat['ism_times'],  sat['ism_tec'],  "ISMRAWTEC's TEC")
    dumpplot(sat['times'],      sat['avgNT12'],  "avgNT(12)")
    dumpplot(sat['times'],      sat['avgNT15'],  "avgNT(15)")
    dumpplot(sat['times'],      sat['delNT12'],  "delNT(12)")
    dumpplot(sat['times'],      sat['delNT15'],  "delNT(15)")
    dumpplot(sat['times'][59:], sat['sigNT12'],  "sigNT(12)")
    dumpplot(sat['times'][59:], sat['sigNT15'],  "sigNT(15)")
    dumpplot(sat['times'][59:], sat['sigPhi12'], "sigPhi(12)")
    dumpplot(sat['times'][59:], sat['sigPhi15'], "sigPhi(15)")

    gax.legend()
    plt.title(f"ПЭСы спутника {sat_name}")


# Ref: https://stackoverflow.com/questions/15411967
def is_ipython() -> bool:
    try:
        shell = get_ipython().__class__.__name__
        if shell == 'ZMQInteractiveShell':
            return True   # Jupyter notebook or qtconsole
        elif shell == 'TerminalInteractiveShell':
            return True   # Terminal running IPython
        else:
            return True   # Other type (?)
    except NameError:
        return False      # Probably standard Python interpreter


if not is_ipython() and __name__ == '__main__':
    if not os.path.exists("./rawdump/"):
        print("No dump files. Requesting...")
        os.system("rawdump.sh -in")

    for values in read_csvs():
        plot_build(perf_cal(values))

    plt.show()
    exit(0)


# ## Скрипт Jupyter
# 
# Эта часть будет выполняться, если программа запущена в Jupyter

# ### Установка зависимостей
# 
# Установить пакеты для работы с ClickHouse:

# In[ ]:


# Install a conda packages in the current Jupyter kernel
import sys

get_ipython().system('conda install --yes --prefix {sys.prefix} -c conda-forge clickhouse-driver clickhouse-sqlalchemy ipython-sql')


# Подгрузить SQL magic:

# In[ ]:


from sqlalchemy import create_engine


# In[ ]:


get_ipython().run_line_magic('load_ext', 'sql')


# In[ ]:


get_ipython().run_line_magic('sql', 'clickhouse://default:@clickhouse/default')


# ### Получение данных

# In[ ]:


_sat = "GLONASS14"
_from = 1676224161051
_to = 1676224426968
_secondaryfreq = "L2CA"


# In[ ]:


get_ipython().run_cell_magic('sql', 'values_range <<', "SELECT\n    time,\n    anyIf(psr, freq = 'L1CA') AS psr1,\n    anyIf(psr, freq = 'L2CA') AS psr2,\n    anyIf(psr, freq = 'L2P') AS psr5,\n    anyIf(adr, freq = 'L1CA') AS adr1,\n    anyIf(adr, freq = 'L2CA') AS adr2,\n    anyIf(adr, freq = 'L2P') AS adr5,\n    any(cno) as cno,\n    sat\nFROM\n    rawdata.range\nWHERE\n    sat=:_sat\n    AND time BETWEEN :_from AND :_to\nGROUP BY\n    time, sat\nORDER BY\n    time ASC\n")


# In[ ]:


get_ipython().run_cell_magic('sql', 'values_ismrawtec <<', 'SELECT\n    time,\n    anyIf(tec, secondaryfreq = :_secondaryfreq) AS tec,\n    sat\nFROM\n    rawdata.ismrawtec\nWHERE\n    sat=:_sat\n    AND time BETWEEN :_from AND :_to\nGROUP BY\n    time,\n    sat\nORDER BY\n    time ASC\n')


# In[ ]:


get_ipython().run_cell_magic('sql', 'values_satxyz2 <<', 'SELECT\n    time,\n    elevation,\n    sat\nFROM\n    rawdata.satxyz2\nWHERE\n    sat=:_sat\n    AND time BETWEEN :_from AND :_to\nORDER BY\n    time ASC\n')


# ### Модификация

# In[ ]:


# Замена для источника данных
def read_sql():
    return [dict(
             {"range": values_range.dict(),
              "ismrawtec": values_ismrawtec.dict(),
              "satxyz2": values_satxyz2.dict()})]


# ### Расчеты

# In[ ]:


for values in read_sql():
    plot_build(perf_cal(values))


# In[ ]:





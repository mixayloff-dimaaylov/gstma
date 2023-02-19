#!/usr/bin/env python
# coding: utf-8

# Пример взаимодействия с ClickHouse
# ==================================
# 
# На [основе][altinity].
# 
# [altinity]: https://altinity.com/blog/2019/2/25/clickhouse-and-python-jupyter-notebooks

# ## Установка зависимостей
# 
# Установить пакеты для работы с ClickHouse и ipywidgets:

# In[ ]:


# Install a conda packages in the current Jupyter kernel
import sys

get_ipython().system('conda install --yes --prefix {sys.prefix} -c conda-forge clickhouse-driver clickhouse-sqlalchemy ipywidgets')


# ## Исходная программа

# In[ ]:


#!/usr/bin/env python3

import os
import glob
import re
import statistics
import scipy.signal as sc
import datetime as dt

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

from math import pi


# ### Выражения

# In[ ]:


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


# ### Работа с выгрузками

# In[ ]:


def dump_range(sql_con, _sat, _from, _to):
    return pd.read_sql(f"""
SELECT
    time,
    anyIf(psr, freq = 'L1CA') AS psr1,
    anyIf(psr, freq = 'L2C') AS psr2,
    anyIf(psr, freq = 'L5Q') AS psr5,
    anyIf(adr, freq = 'L1CA') AS adr1,
    anyIf(adr, freq = 'L2C') AS adr2,
    anyIf(adr, freq = 'L5Q') AS adr5,
    any(cno) as cno,
    sat
FROM
    rawdata.range
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
GROUP BY
    time, sat
ORDER BY
    time ASC
""", sql_con)


def dump_ismrawtec(sql_con, _sat, _from, _to, _secondaryfreq):
    return pd.read_sql(f"""
SELECT
    time,
    anyIf(tec, secondaryfreq = '{_secondaryfreq}') AS tec,
    sat
FROM
    rawdata.ismrawtec
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
GROUP BY
    time,
    sat
ORDER BY
    time ASC
""", sql_con)


def dump_satxyz2(sql_con, _sat, _from, _to):
    return pd.read_sql(f"""
SELECT
    time,
    elevation,
    sat
FROM
    rawdata.satxyz2
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
ORDER BY
    time ASC
""", sql_con)


# Dumps tables from sql_con to files and returns Pandas DataFrame's
def dump_csvs(sql_con, _sat, _from, _to, _secondaryfreq):
    df_range = dump_range(sql_con, _sat, _from, _to,)
    df_ismrawtec = dump_ismrawtec(sql_con, _sat, _from, _to, _secondaryfreq)
    df_satxyz2 = dump_satxyz2(sql_con, _sat, _from, _to)

    csv_params = {"sep":",", "encoding":"utf-8",
                  "index":False, "header":True, "lineterminator":"\n"}

    df_range.to_csv(**csv_params,
        path_or_buf=f"./rawdump/rawdata_range_{_sat}_{_from}_{_to}.csv")
    df_ismrawtec.to_csv(**csv_params,
        path_or_buf=f"./rawdump/rawdata_ismrawtec_{_sat}_{_from}_{_to}.csv")
    df_satxyz2.to_csv(**csv_params,
        path_or_buf=f"./rawdump/rawdata_satxyz2_{_sat}_{_from}_{_to}.csv")

    return [dict({"range": df_range,
                  "ismrawtec": df_ismrawtec,
                  "satxyz2": df_satxyz2})]


# Searches CSV files in ./rawdump/ dir and returns them as list of tuples, for
# each (satellite, from, to, secondaryfreq)
def read_csvs():
    # glob SHOULD sort them in synchronous order
    files_range = glob.glob("./rawdump/rawdata_range_*.csv")
    files_ismrawtec = glob.glob("./rawdump/rawdata_ismrawtec_*.csv")
    files_satxyz2 = glob.glob("./rawdump/rawdata_satxyz2_*.csv")

    return ({"range": pd.read_csv(r),
             "ismrawtec": pd.read_csv(rt),
             "satxyz2": pd.read_csv(xyz)}
            for r, rt, xyz in zip(files_range, files_ismrawtec, files_satxyz2))


# ### Расчеты

# In[ ]:


def constants(sat_system):
    if sat_system == 'GPS':
        f1 = 1575.42e6  # L1CA
        f2 = 1227.60e6  # L2C or L2P
        f5 = 1176.45e6  # L5Q

        # TODO: даже после добавления развилки не считает
        RDCB_L1L2 = 0.0  # 34.577472687 # L1CAL2C
        RDCB_L1L5 = 0.0  # 12.218264580 # L1CAL5
    elif sat_system == 'GLONASS':
        f1 = 1602.0e6 + -3 * 0.5625e6  # L1 -1
        f2 = 1246.0e6 + -3 * 0.4375e6  # L2 -1
        f5 = 1246.0e6 + -3 * 0.4375e6  # L2P -1

        RDCB_L1L2 = 0.0  # 14.522434235  # L1CAL2CA
        RDCB_L1L5 = 0.0  # 23.634117126  # L1CAL2P
    else:
        print("Неопределенный тип спутниковой системы.")
        exit(1)

    return tuple((f1, f2, f5, RDCB_L1L2, RDCB_L1L5))


def perf_cal(values):
    df_range = values['range']
    df_ismrawtec = values['ismrawtec']
    df_satxyz2 = values['satxyz2']

    file_a_sat = df_range.sat[0]
    file_a_sat_system = re.search('^([A-Z]+)[0-9]+$', file_a_sat).group(1)

    f1, f2, f5, RDCB_L1L2, RDCB_L1L5 = constants(file_a_sat_system)

    # RANGE
    df_range.time = pd.to_datetime(df_range.time, unit='ms', utc=True)

    # ISMRAWTEC
    df_ismrawtec.time = pd.to_datetime(df_ismrawtec.time, unit='ms', utc=True)
    df_ismrawtec.set_index('time', inplace=True)
    df_ismrawtec = df_ismrawtec.resample('20ms')
    df_ismrawtec = df_ismrawtec.interpolate(method='linear').interpolate(method='ffill')
    df_ismrawtec.reset_index(inplace=True)

    # Расчеты
    df_range['k12'] = k(df_range.adr1, df_range.adr2, f1, f2, df_range.psr1, df_range.psr2)
    df_range['k15'] = k(df_range.adr1, df_range.adr5, f1, f5, df_range.psr1, df_range.psr5)
    df_range['p12'] = df_range.psr2 - df_range.psr1
    df_range['p15'] = df_range.psr5 - df_range.psr1

    _DNT12 = statistics.mean(df_range.k12)
    _DNT15 = statistics.mean(df_range.k15)
    DNT12 = multiplier(f1, f2) * _DNT12
    DNT15 = multiplier(f1, f5) * _DNT15

    print("DNT12: " + str(DNT12))
    print("DNT15: " + str(DNT15))

    # --- drawings ---
    # fig, ax = plt.subplots()
    # ax.plot(df_range.time, [DNT12] * len(k12), label="DNT12(df_range.adr1, df_range.adr2, df_range.psr1, df_range.psr2")
    # ax.plot(df_range.time, [DNT15] * len(k15), label="DNT15(df_range.adr1, df_range.adr5, df_range.psr1, df_range.psr5")
    # ax.plot(df_range.time, p12, label="P1-P2")
    # ax.plot(df_range.time, p15, label="P1-P5")
    # ax.legend()

    df_range['NT12psr'] = multiplier(f1, f2) * df_range.p12 + RDCB_L1L2
    df_range['NT15psr'] = multiplier(f1, f5) * df_range.p15 + RDCB_L1L5

    # print(p12, p15)

    df_range['NT12adr_wo_DNT'] = multiplier(f1, f2) * adr_adr(df_range.adr1, df_range.adr2, f1, f2)
    df_range['NT15adr_wo_DNT'] = multiplier(f1, f5) * adr_adr(df_range.adr1, df_range.adr5, f1, f5)
    df_range['NT12adr'] = df_range.NT12adr_wo_DNT + DNT12 + RDCB_L1L2
    df_range['NT15adr'] = df_range.NT15adr_wo_DNT + DNT15 + RDCB_L1L5

    # plt.show()

    df_range['avgNT12'] = avgNT(df_range.NT12adr)
    df_range['avgNT15'] = avgNT(df_range.NT15adr)

    df_range['delNT12'] = delNT(df_range.NT12adr)
    df_range['delNT15'] = delNT(df_range.NT15adr)

    df_range['sigNT12'] = pd.Series(sigNT(df_range.delNT12)).shift(59, fill_value=0.0)
    df_range['sigNT15'] = pd.Series(sigNT(df_range.delNT15)).shift(59, fill_value=0.0)

    df_range['sigPhi12'] = sigPhi(df_range.sigNT12, f2)
    df_range['sigPhi15'] = sigPhi(df_range.sigNT15, f5)

    # For export
    df_range['ism_tec'] = df_ismrawtec.tec

    return df_range


def plot_build(sat):
    sat_name = f" спутника {sat['sat'][0]}"

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

    dumpplot(sat.time, sat.NT12psr,  "NT(P1-P2)")
    dumpplot(sat.time, sat.NT15psr,  "NT(P1-P5)")
    dumpplot(sat.time, sat.NT12adr,  "NT(adr1 - adr2)")
    dumpplot(sat.time, sat.NT15adr,  "NT(adr1 - adr5)")
    dumpplot(sat.time, sat.ism_tec,  "ISMRAWTEC's TEC")
    dumpplot(sat.time, sat.avgNT12,  "avgNT(12)")
    dumpplot(sat.time, sat.avgNT15,  "avgNT(15)")
    dumpplot(sat.time, sat.delNT12,  "delNT(12)")
    dumpplot(sat.time, sat.delNT15,  "delNT(15)")
    dumpplot(sat.time, sat.sigNT12,  "sigNT(12)")
    dumpplot(sat.time, sat.sigNT15,  "sigNT(15)")
    dumpplot(sat.time, sat.sigPhi12, "sigPhi(12)")
    dumpplot(sat.time, sat.sigPhi15, "sigPhi(15)")

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

# ### Параметры SQL-подключения:

# In[ ]:


sql_con = "clickhouse://default:@clickhouse/default"


# ### Интерактивный запрос параметров выгрузки

# In[ ]:


from IPython.display import display
from ipywidgets import interact, Text, IntText

_satw = Text(
    description="Спутник:")
_fromw = IntText(
    description="Начальное время:",
    min=0)
_tow = IntText(
    description="Конечное время:",
    min=0)
_secondaryfreqw = Text(
    description="Secondaryfreq:")

display(_satw)
display(_fromw)
display(_tow)
display(_secondaryfreqw)


# In[ ]:


_sat = _satw.value
_from = _fromw.value
_to = _tow.value
_secondaryfreq = _secondaryfreqw.value


# ### Получение данных и расчеты

# In[ ]:


for values in dump_csvs(sql_con, _sat,
                        _from, _to, _secondaryfreq):
    if values['range'].empty:
        print("Выгрузка пуста!")
    else:
        plot_build(perf_cal(values))


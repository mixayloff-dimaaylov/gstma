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
import matplotlib.dates as mdates

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
SELECT *
FROM
    rawdata.range
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
ORDER BY
    time ASC
""", sql_con)


def dump_ismrawtec(sql_con, _sat, _from, _to, _secondaryfreq):
    return pd.read_sql(f"""
SELECT *
FROM
    rawdata.ismrawtec
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
    AND secondaryfreq = '{_secondaryfreq}'
ORDER BY
    time ASC
""", sql_con)


def dump_satxyz2(sql_con, _sat, _from, _to):
    return pd.read_sql(f"""
SELECT *
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

    _date = str(dt.datetime.fromtimestamp(_from/1000).date())
    _path = f"./rawdump/{_date}"
    os.makedirs(_path, exist_ok=True)

    df_range.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_range_{_sat}_{_from}_{_to}.csv")
    df_ismrawtec.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_ismrawtec_{_sat}_{_from}_{_to}.csv")
    df_satxyz2.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_satxyz2_{_sat}_{_from}_{_to}.csv")

    return [dict({"range": df_range,
                  "ismrawtec": df_ismrawtec,
                  "satxyz2": df_satxyz2})]


# Searches CSV files in ./rawdump/<_from_date> dir and returns them as list of tuples, for
# each (satellite, from, to, secondaryfreq)
def read_csvs(_from):
    _date = str(dt.datetime.fromtimestamp(_from/1000).date())
    _path = f"./rawdump/{_date}"

    # glob SHOULD sort them in synchronous order
    files_range = glob.glob(f"{_path}/rawdata_range_*.csv")
    files_ismrawtec = glob.glob(f"{_path}/rawdata_ismrawtec_*.csv")
    files_satxyz2 = glob.glob(f"{_path}/rawdata_satxyz2_*.csv")

    return ({"range": pd.read_csv(r),
             "ismrawtec": pd.read_csv(rt),
             "satxyz2": pd.read_csv(xyz)}
            for r, rt, xyz in zip(files_range, files_ismrawtec, files_satxyz2))


# ### Расчеты

# In[ ]:


fs = {('GPS',     'L1CA'):         1575.42e6,
      ('GPS',     'L2C'):          1227.60e6,
      ('GPS',     'L2P'):          1227.60e6,
      ('GPS',     'L2P_codeless'): 1227.60e6,
      ('GPS',     'L5Q'):          1176.45e6,
      ('GLONASS', 'L1CA'):         1602.0e6 + -3 * 0.5625e6,
      ('GLONASS', 'L2CA'):         1246.0e6 + -3 * 0.4375e6,
      ('GLONASS', 'L2P'):          1246.0e6 + -3 * 0.4375e6}


rdcbs = {('GPS',     'L1CA', 'L2C'):          0.0,
         ('GPS',     'L1CA', 'L2P'):          0.0,
         ('GPS',     'L1CA', 'L2P_codeless'): 0.0,
         ('GPS',     'L1CA', 'L5Q'):          0.0,
         ('GLONASS', 'L1CA', 'L2CA'):         0.0,
         ('GLONASS', 'L1CA', 'L2P'):          0.0}


def comb_dfs(values):
    def comb(base, df_f1, f2):
        df_f2 = base[base["freq"] == f2] \
                    .rename(columns={"freq": "freq2", "glofreq": "glofreq2",
                                     "adr": "adr2", "psr": "psr2",
                                     "cno": "cno2", "locktime": "locktime2"})

        return pd.merge(df_f1, df_f2, how="inner", on=["time", "sat", "system", "prn"])

    df_range = values['range']
    df_ismrawtec = values['ismrawtec']
    df_satxyz2 = values['satxyz2']

    sat = df_range.sat[0]
    sat_system = re.search('^([A-Z]+)[0-9]+$', sat).group(1)

    # RANGE
    df_range.time = pd.to_datetime(df_range.time, unit='ms', utc=True)

    # ISMRAWTEC
    df_ismrawtec.time = pd.to_datetime(df_ismrawtec.time, unit='ms', utc=True)
    df_ismrawtec.set_index('time', inplace=True)
    df_ismrawtec = df_ismrawtec.resample('20ms')
    df_ismrawtec = df_ismrawtec.interpolate(method='linear').interpolate(method='ffill')
    df_ismrawtec.reset_index(inplace=True)

    # sigcombing
    df_f1 = df_range[df_range["freq"] == "L1CA"] \
          .rename(columns={"freq": "freq1", "glofreq": "glofreq1",
                           "adr": "adr1", "psr": "psr1",
                           "cno": "cno1", "locktime": "locktime1"})

    freqs = df_range["freq"].unique()
    sigcombed = [{"range": comb(df_range, df_f1, f2),
                  "ismrawtec": df_ismrawtec,
                  "satxyz2": df_satxyz2}
                 for f2 in freqs[freqs != "L1CA"]]

    return sigcombed


def perf_cal(values):
    df_range = values['range']
    df_ismrawtec = values['ismrawtec']
    df_satxyz2 = values['satxyz2']

    # Преобразования частот в числовые значения
    df_range['rdcb'] = df_range.apply(lambda x: rdcbs[(x['system'], x['freq1'], x['freq2'])], axis=1)
    df_range['f1'] = df_range.apply(lambda x: fs[(x['system'], x['freq1'])], axis=1)
    df_range['f2'] = df_range.apply(lambda x: fs[(x['system'], x['freq2'])], axis=1)

    # Расчеты
    df_range['k'] = k(df_range.adr1, df_range.adr2,
                      df_range.f1,   df_range.f2,
                      df_range.psr1, df_range.psr2)
    df_range['p'] = df_range.psr2 - df_range.psr1

    _DNT = statistics.mean(df_range.k)
    DNT = multiplier(df_range.f1, df_range.f2) * _DNT

    df_range['NTpsr'] = multiplier(df_range.f1, df_range.f2) \
                           * df_range.p + df_range.rdcb

    df_range['NTadr_wo_DNT'] = multiplier(df_range.f1, df_range.f2) \
      * adr_adr(df_range.adr1, df_range.adr2, df_range.f1, df_range.f2)
    df_range['NTadr'] = df_range.NTadr_wo_DNT + DNT + df_range.rdcb

    df_range['avgNT'] = avgNT(df_range.NTadr)
    df_range['delNT'] = delNT(df_range.NTadr)

    df_range['sigNT'] = pd.Series(sigNT(df_range.delNT)).shift(59, fill_value=0.0)
    df_range['sigPhi'] = sigPhi(df_range.sigNT, df_range.f2)

    # For export
    df_range['ism_tec'] = df_ismrawtec.tec
    df_range['ism_primaryfreq'] = df_ismrawtec.primaryfreq
    df_range['ism_secondaryfreq'] = df_ismrawtec.secondaryfreq

    return df_range


def plot_build(sat):
    _date = str(sat["time"].min().date())
    _path = f"./rawdump/{_date}/plots"
    os.makedirs(_path, exist_ok=True)

    # Locals
    _sat = sat['sat'][0]
    _date = sat['time'].min().date()
    _from = sat['time'].min().time()
    _to = sat['time'].max().time()
    _freq1 = sat['freq1'][0]
    _freq2 = sat['freq2'][0]

    track_name = f"{_sat} {_date} {_from} {_to} {_freq1}+{_freq2}"
    track_name_human = f"спутника {_sat} {_freq1}+{_freq2}"

    # Matplotlib setup
    locator = mdates.AutoDateLocator()
    formatter = mdates.ConciseDateFormatter(locator)

    gfig, gax = plt.subplots()
    gax.xaxis.set_major_locator(locator)
    gax.xaxis.set_major_formatter(formatter)

    def dumpplot(xs, ys, yname, ylabel):
        fig, ax = plt.subplots()

        ax.xaxis.set_major_locator(locator)
        ax.xaxis.set_major_formatter(formatter)

        ax.set_title(f"${yname}$ {track_name_human}")
        ax.set_xlabel("Datetime")
        ax.set_ylabel(f"${ylabel}$")
        # Cuttoff filter splashes
        ax.plot(xs[200:], ys[200:], label=f"${yname}$")
        ax.grid()
        # Rotate and align the tick labels so they look better.
        fig.autofmt_xdate()
        fig.legend()

        plt.title(f"${yname}$ {track_name_human}")
        plt.savefig(f"{_path}/{track_name} {yname}.png")
        plt.close(fig)

        gax.plot(xs[200:], ys[200:], label=f"${yname}$")
        gax.set_xlabel("Datetime")

    dumpplot(sat.time, sat.NTpsr,   "N_T (P_1 - P_2)",     "TECU")
    dumpplot(sat.time, sat.NTadr,   "N_T (adr_1 - adr_2)", "TECU")
    dumpplot(sat.time, sat.ism_tec, "ISMRAWTEC's TEC",     "TECU")
    dumpplot(sat.time, sat.avgNT,   "\overline{{N_T}}",    "TECU")
    dumpplot(sat.time, sat.delNT,   "\Delta N_T",          "TECU")
    dumpplot(sat.time, sat.sigNT,   "\sigma N_T",          "TECU")
    dumpplot(sat.time, sat.sigPhi,  "\sigma \\varphi",     "TECU")
    gax.set_ylabel("TECU")

    gax.legend()
    gax.grid()
    plt.title(f"ПЭСы {track_name_human} ({_date})")
    # Rotate and align the tick labels so they look better.
    gfig.autofmt_xdate()


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


sql_con = "clickhouse://default:@clickhouse/default"


if not is_ipython() and __name__ == '__main__':
    if not os.path.exists("./rawdump/"):
        print("No dump files. Requesting...")
        os.system("rawdump.sh -in")

    _sat = input("sat: ")
    _from = int(input("from (UTC UNIX Timestamp ms): "))
    _to = int(input("to (UTC UNIX Timestamp ms):"))
    _secondaryfreq = input("secondaryfreq (for ISMRAWTEC):")

    for values in dump_csvs(sql_con,
                            _sat, _from, _to, _secondaryfreq):
        if values['range'].empty:
            print("Выгрузка пуста!")
            exit(1)

        for sigcomb in comb_dfs(values):
            plot_build(perf_cal(sigcomb))

    plt.show()
    exit(0)


# ## Скрипт Jupyter
# 
# Эта часть будет выполняться, если программа запущена в Jupyter

# ### Интерактивный запрос параметров выгрузки

# In[ ]:


from ipywidgets import interact, IntText, Dropdown

_satw = Dropdown(
    description="Спутник:")
_fromw = IntText(
    description="Начальное время:",
    min=0)
_tow = IntText(
    description="Конечное время:",
    min=0)
_secondaryfreqw = Dropdown(
    description="Secondaryfreq:")


def update_sat(*args):
    if (_fromw.value > 0) and (_tow.value > 0) \
        and (_fromw.value < _tow.value):
        df = pd.read_sql(f"""
SELECT DISTINCT(sat)
FROM
    rawdata.range
WHERE
    time BETWEEN {_fromw.value} AND {_tow.value}
""", sql_con)
        _satw.options = df["sat"].unique()
    else:
        _satw.options = []


def update_secondaryfreq(*args):
    if (_fromw.value > 0) and (_tow.value > 0) \
        and (_fromw.value < _tow.value) and (_satw.value != ""):
        df = pd.read_sql(f"""
SELECT DISTINCT(secondaryfreq)
FROM
    rawdata.ismrawtec
WHERE
    sat = '{_satw.value}'
    AND time BETWEEN {_fromw.value} AND {_tow.value}
""", sql_con)
        _secondaryfreqw.options = df["secondaryfreq"].unique()
    else:
        _secondaryfreqw.options = []


_fromw.observe(update_sat, 'value')
_tow.observe(update_sat, 'value')
_satw.observe(update_secondaryfreq, 'value')


# ### Получение данных и расчеты

# In[ ]:


@interact(_sat=_satw, _from=_fromw, _to=_tow,
          _secondaryfreq=_secondaryfreqw).options(manual=True)
def jupyter_main(_sat, _from, _to, _secondaryfreq):
    for values in dump_csvs(sql_con,
                            _sat, _from, _to, _secondaryfreq):
        if values['range'].empty:
            print("Выгрузка пуста!")
            return

        for sigcomb in comb_dfs(values):
            plot_build(perf_cal(sigcomb))


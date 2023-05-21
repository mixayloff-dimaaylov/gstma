#!/usr/bin/env python
# coding: utf-8

# Пример взаимодействия с ClickHouse
# ==================================
# 
# На [основе][altinity].
# 
# [altinity]: https://altinity.com/blog/2019/2/25/clickhouse-and-python-jupyter-notebooks

# ## LICENSE

# Copyright 2023 mixayloff-dimaaylov at github dot com
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# ## Установка зависимостей
# 
# Установить пакеты для работы с ClickHouse и ipywidgets:

# In[ ]:


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


# Install a conda packages in the current Jupyter kernel
if is_ipython():
    import sys

    get_ipython().system('mamba install -C --yes --prefix {sys.prefix} -c conda-forge clickhouse-driver clickhouse-sqlalchemy ipywidgets')


# ## Исходная программа

# In[ ]:


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


def gamma(sigPhi):
    return 1 / np.exp(np.power(sigPhi, 2) + 1)


def F_c(sigPhi, f1):
    return f1 / (np.sqrt(2) * sigPhi)


def P_c(sigPhi):
    return 200 / sigPhi


def s_4(sigPhi):
    return np.sqrt(1 - np.exp(-2 * np.power(sigPhi, 2)))


# ### Pandas-выражения

# In[ ]:


def s_4_cno(df):
    # tumbling window 1 sec
    c0 = df.set_index('time')
    c0['c1'] = np.power(np.power(10, c0.cno1/10), 2)
    c0['c2'] = np.power(10, c0.cno1/10)
    c0 = c0.resample('1s').mean(numeric_only=True)
    c0['s4cno'] = (c0.c1 - np.power(c0.c2, 2)) / np.power(c0.c2, 2)
    c0.reset_index(inplace=True)

    return c0


def s_4_pwr(df):
    # tumbling window 1 sec
    c0 = df.set_index('time')
    c0['c1'] = np.power(c0.power, 2)
    c0['c2'] = c0.power
    c0 = c0.resample('1s').mean(numeric_only=True)
    c0['s4pwr'] = np.sqrt((c0.c1 - np.power(c0.c2, 2)) / np.power(c0.c2, 2))
    c0.reset_index(inplace=True)

    return c0


# ### Работа с выгрузками

# In[ ]:


def to_datetime(df):
    res = df
    res['time'] = pd.to_datetime(df['time'], unit='ms', utc=True)
    return res


def read_sql_chunked(query, sql_con, chunksize=100000):
    dfs = pd.read_sql(query, sql_con, chunksize=chunksize)
    return pd.concat(dfs, ignore_index=True)


def dump_range(sql_con, _sat, _from, _to):
    return read_sql_chunked(f"""
SELECT DISTINCT *
FROM
    rawdata.range
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
ORDER BY
    time ASC
""", sql_con)


def dump_ismdetobs(sql_con, _sat, _from, _to):
    return read_sql_chunked(f"""
SELECT DISTINCT *
FROM
    rawdata.ismdetobs
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
ORDER BY
    time ASC
""", sql_con)


def dump_ismrawtec(sql_con, _sat, _from, _to, _secondaryfreq):
    return read_sql_chunked(f"""
SELECT DISTINCT *
FROM
    rawdata.ismrawtec
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
    AND secondaryfreq = '{_secondaryfreq}'
ORDER BY
    time ASC
""", sql_con)


def dump_ismredobs(sql_con, _sat, _from, _to):
    return read_sql_chunked(f"""
SELECT DISTINCT *
FROM
    rawdata.ismredobs
WHERE
    sat='{_sat}'
    AND time BETWEEN {_from} AND {_to}
ORDER BY
    time ASC
""", sql_con)


def dump_satxyz2(sql_con, _sat, _from, _to):
    return read_sql_chunked(f"""
SELECT DISTINCT *
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
    df_range = dump_range(sql_con, _sat, _from, _to)
    df_ismdetobs = dump_ismdetobs(sql_con, _sat, _from, _to)
    df_ismrawtec = dump_ismrawtec(sql_con, _sat, _from, _to, _secondaryfreq)
    df_ismredobs = dump_ismredobs(sql_con, _sat, _from, _to)
    df_satxyz2 = dump_satxyz2(sql_con, _sat, _from, _to)

    csv_params = {"sep":",", "encoding":"utf-8",
                  "index":False, "header":True, "lineterminator":"\n"}

    _date = str(dt.datetime.fromtimestamp(_from/1000).date())
    _path = f"./rawdump/{_date}"
    os.makedirs(_path, exist_ok=True)

    df_range.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_range_{_sat}_{_from}_{_to}.csv")
    df_ismdetobs.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_ismdetobs_{_sat}_{_from}_{_to}.csv")
    df_ismrawtec.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_ismrawtec_{_sat}_{_from}_{_to}_{_secondaryfreq}.csv")
    df_ismredobs.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_ismredobs_{_sat}_{_from}_{_to}.csv")
    df_satxyz2.to_csv(**csv_params,
        path_or_buf=f"{_path}/rawdata_satxyz2_{_sat}_{_from}_{_to}.csv")

    return [dict({"range": df_range,
                  "ismdetobs": df_ismdetobs,
                  "ismrawtec": df_ismrawtec,
                  "ismredobs": df_ismredobs,
                  "satxyz2": df_satxyz2})]


# Searches CSV files in ./rawdump/<_from_date> dir and returns them as list of tuples, for
# each (satellite, from, to, secondaryfreq)
def read_csvs(_from):
    _date = str(dt.datetime.fromtimestamp(_from/1000).date())
    _path = f"./rawdump/{_date}"

    # glob SHOULD sort them in synchronous order
    files_range = glob.glob(f"{_path}/rawdata_range_*.csv")
    files_ismdetobs = glob.glob(f"{_path}/rawdata_ismdetobs_*.csv")
    files_ismrawtec = glob.glob(f"{_path}/rawdata_ismrawtec_*.csv")
    files_ismredobs = glob.glob(f"{_path}/rawdata_ismredobs_*.csv")
    files_satxyz2 = glob.glob(f"{_path}/rawdata_satxyz2_*.csv")

    return ({"range": pd.read_csv(r),
             "ismdetobs": pd.read_csv(rd),
             "ismrawtec": pd.read_csv(rt),
             "ismredobs": pd.read_csv(rrd),
             "satxyz2": pd.read_csv(xyz)}
            for r, rd, rt, rrd, xyz in zip(
                files_range, files_ismdetobs, files_ismrawtec, files_ismredobs, files_satxyz2))


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

    def comb_redobs(base, df_f1, f2):
        df_f2 = base[base["freq"] == f2] \
                    .rename(columns={"freq": "freq2", "glofreq": "glofreq2",
                                     "totals4": "totals4_2"})

        return pd.merge(df_f1, df_f2, how="inner", on=["time", "sat", "system", "prn"])

    # RANGE
    df_range = to_datetime(values['range'])

    # ISMDETOBS
    df_ismdetobs = to_datetime(values['ismdetobs'])

    # ISMRAWTEC
    df_ismrawtec = to_datetime(values['ismrawtec'])

    # ISMREDOBS
    df_ismredobs = to_datetime(values['ismredobs'])

    # SATXYZ2
    df_satxyz2 = to_datetime(values['satxyz2'])
    df_satxyz2.elevation = np.deg2rad(df_satxyz2.elevation)

    sat = df_range.sat[0]
    sat_system = re.search('^([A-Z]+)[0-9]+$', sat).group(1)

    # sigcombing
    df_f1 = df_range[df_range["freq"] == "L1CA"] \
          .rename(columns={"freq": "freq1", "glofreq": "glofreq1",
                           "adr": "adr1", "psr": "psr1",
                           "cno": "cno1", "locktime": "locktime1"})

    df_ismredobs_f1 = df_ismredobs[df_ismredobs["freq"] == "L1CA"] \
          .rename(columns={"freq": "freq1", "glofreq": "glofreq1",
                           "totals4": "totals4_1"})

    freqs = df_range["freq"].unique()
    sigcombed = [{"range": comb(df_range, df_f1, f2),
                  "ismdetobs": df_ismdetobs,
                  "ismrawtec": df_ismrawtec,
                  "ismredobs": comb_redobs(df_ismredobs, df_ismredobs_f1, f2),
                  "satxyz2": df_satxyz2}
                 for f2 in freqs[freqs != "L1CA"]]

    return sigcombed


def perf_cal(values):
    df_range = values['range']
    df_ismdetobs = values['ismdetobs']
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

    df_range['avgNTcurved'] = avgNT(df_range.NTadr)
    df_range['delNTcurved'] = delNT(df_range.NTadr)

    df_range['avgNT'] = np.sin(df_satxyz2.elevation) * avgNT(df_range.NTadr)
    df_range['delNT'] = np.sin(df_satxyz2.elevation) * delNT(df_range.NTadr)

    df_range['sigNT'] = pd.Series(sigNT(df_range.delNT)).shift(59, fill_value=0.0)
    df_range['sigPhi'] = sigPhi(df_range.sigNT, df_range.f2)
    df_range['gamma'] = gamma(df_range.sigPhi)
    df_range['Fc'] = F_c(df_range.sigPhi, df_range.f1)
    df_range['Pc'] = P_c(df_range.sigPhi)
    df_range['s4'] = s_4(df_range.sigPhi)

    # df_range['s4cno'] = s_4_cno(df_range)
    df_ismdetobs_resampled = s_4_pwr(df_ismdetobs)

    # For export
    values['range'] = df_range
    values['ismdetobs'] = df_ismdetobs
    values['df_ismdetobs_resampled'] = df_ismdetobs_resampled

    return values


def plot_build(values):
    df_range = values['range']
    df_ismdetobsr = values['df_ismdetobs_resampled']
    df_ismrawtec = values['ismrawtec']
    df_ismredobs = values['ismredobs']
    df_satxyz2 = values['satxyz2']

    df_range.drop(index=df_range.index[:200],inplace=True)
    df_range = df_range.reset_index()

    _date = str(df_range["time"].min().date())
    _path = f"./rawdump/{_date}/plots"
    os.makedirs(_path, exist_ok=True)

    # Locals
    _sat = df_range['sat'][0]
    _date = df_range['time'].min().date()
    _from = df_range['time'].min().time()
    _to = df_range['time'].max().time()
    _freq1 = df_range['freq1'][0]
    _freq2 = df_range['freq2'][0]

    track_name = f"{_sat} {_date} {_from} {_to} {_freq1}+{_freq2}"
    track_name_human = f"спутника {_sat} {_freq1}+{_freq2}"

    # Matplotlib setup
    locator = mdates.AutoDateLocator()
    formatter = mdates.ConciseDateFormatter(locator)

    def init_plot():
        fig, ax = plt.subplots()
        ax.xaxis.set_major_locator(locator)
        ax.xaxis.set_major_formatter(formatter)
        return fig, ax

    def plot_finalize(fig, ax, title):
        ax.legend()
        ax.grid()
        plt.title(f"{title} {track_name_human} ({_date})")
        # Rotate and align the tick labels so they look better.
        fig.autofmt_xdate()

    def dumpplot(gax, xs, ys, yname, ylabel):
        fig, ax = init_plot()

        ax.set_title(f"${yname}$ {track_name_human}")
        ax.set_xlabel("Datetime")
        ax.set_ylabel(f"{ylabel}")
        ax.plot(xs, ys, label=f"${yname}$")
        ax.grid()
        # Rotate and align the tick labels so they look better.
        fig.autofmt_xdate()
        fig.legend()

        plt.title(f"${yname}$ {track_name_human}")
        plt.savefig(f"{_path}/{track_name} {yname}.png")
        plt.close(fig)

        gax.plot(xs, ys, label=f"${yname}$")
        gax.set_xlabel("Datetime")

    # First plot with TECU
    gfig, gax = init_plot()
    for xs, ys, yname in (
        (df_range.time, df_range.NTpsr,       "N_T (P_1 - P_2)"),
        (df_range.time, df_range.NTadr,       "N_T (adr_1 - adr_2)"),
        (df_ismrawtec.time, df_ismrawtec.tec, "ISMRAWTEC's TEC"),
        (df_range.time, df_range.avgNTcurved, "\overline{{N_T curved}}"),
        (df_range.time, df_range.delNTcurved, "\Delta N_T curved"),
        (df_range.time, df_range.avgNT,       "\overline{{N_T}}"),
        (df_range.time, df_range.delNT,       "\Delta N_T"),
        (df_range.time, df_range.sigNT,       "\sigma N_T"),
        (df_range.time, df_range.sigPhi,      "\sigma \\varphi")):
        dumpplot(gax, xs, ys, yname, "TECU")
    gax.set_ylabel("TECU")
    plot_finalize(gfig, gax, "ПЭСы")

    # Other plots:
    gfig, gax = init_plot()
    dumpplot(gax, df_range.time, df_range.gamma, "\gamma", "")
    gax.set_ylabel("")
    plot_finalize(gfig, gax, "Параметр Райса")

    gfig, gax = init_plot()
    dumpplot(gax, df_range.time, df_range.Fc, "F_c", "Hz")
    gax.set_ylabel("Hz")
    plot_finalize(gfig, gax, "Интервал частотной корреляции\n")

    gfig, gax = init_plot()
    dumpplot(gax, df_range.time, df_range.Pc, "P_c", "m")
    gax.set_ylabel("m")
    plot_finalize(gfig, gax, "Интервал пространственной корреляции\n")

    gfig, gax = init_plot()
    for xs, ys, yname in (
        (df_range.time, df_range.s4,                "S_4"),
        #(df_range.time, df_range.s4cno,             "S_{4 CNo}"),
        (df_ismdetobsr.time, df_ismdetobsr.s4pwr,   "S_{4 PWR}"),
        (df_ismredobs.time, df_ismredobs.totals4_1, "S_{4 RAW}"),
        (df_ismredobs.time, df_ismredobs.totals4_2, "S_{4 RAW}")):
        dumpplot(gax, xs, ys, yname, "")
    gax.set_ylabel("")
    plot_finalize(gfig, gax, "Индекс мерцаний")


sql_con = "clickhouse+native://default:@clickhouse/default"


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
        df = read_sql_chunked(f"""
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
        df = read_sql_chunked(f"""
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


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

# Пример взаимодействия с ClickHouse
# ==================================
#
# На [основе][altinity].
#
# [altinity]: https://altinity.com/blog/2019/2/25/clickhouse-and-python-jupyter-notebooks

# Установить пакеты для работы с ClickHouse:

# +
# Install a conda packages in the current Jupyter kernel
import sys

# !mamba install -C --yes --prefix {sys.prefix} -c conda-forge clickhouse-driver clickhouse-sqlalchemy ipython-sql
# -

from sqlalchemy import create_engine

# Подгрузить SQL magic:

# %load_ext sql

# Сделать тестовый запрос:

# %sql clickhouse://default:@clickhouse/default
# result = %sql SELECT * FROM rawdata.range ORDER BY time DESC LIMIT 100
df = result.DataFrame()
df

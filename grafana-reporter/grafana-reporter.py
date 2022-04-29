#!/usr/bin/env python3

import sys

from datetime import datetime as dt
from time import sleep

import requests as requests

from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions
from selenium.common.exceptions import TimeoutException
from selenium.common.exceptions import NoSuchElementException

import shutil as sh

import pathlib
from zipfile import ZipFile, ZIP_DEFLATED


# Defauls
## Clickhouse
CH_HOST = 'ionosphere-iif-clickhouse'
CH_PORT = '8123'
CH_LOGIN = 'ionuser'
CH_PASSWORD = 'password'

## Grafana
GF_HOST = 'ionosphere-iif-grafana'
GF_PORT = '3000'
GF_URL = f"http://{GF_HOST}:{GF_PORT}/d/0F2yyfCWz3/sutochnyi-monitoring?orgId=1"
GF_USERNAME = "admin"
GF_PASSWORD = "admin"

## WebDriver
WD_HOST = "ionosphere-iif-reporter-webdriver"
WD_PORT = "4444"

## Other
ARCH_ROOT = "./archives"
DUMP_ROOT = "./dump"
PERIOD = 6 * 60 * 60  # in seconds


def cur_sats(t_from, t_to):
    url = f"http://{CH_LOGIN}:{CH_PASSWORD}@{CH_HOST}:{CH_PORT}/"

    req = f"""
SELECT distinct(sat)
FROM rawdata.satxyz2
WHERE
    time BETWEEN {t_from} AND {t_to}
"""

    resp = requests.get(url, params={'query': req})

    data = [line.decode('utf-8').split('\t')
            for line in resp.iter_lines(chunk_size=10000)]

    return sum(data, [])


def sat_get(browser, dashboard_url):
    # Подключение
    try:
        browser.get(dashboard_url)

        el = WebDriverWait(browser, 15).until(
            expected_conditions.title_contains('Grafana')
           )

    except TimeoutException:
        print('Timed out waiting to connect to Grafana')
        browser.quit()
        sys.exit(1)


def sat_shot(browser, dashboard_url, sat, t_from, t_to):
    sat_get(browser,
            # ?orgId already there
            dashboard_url + f"&var-satvis={sat}&var-satgraph={sat}"
                          + f"&from={t_from}&to={t_to}")

    try:
        el = WebDriverWait(browser, 30).until(
            expected_conditions.presence_of_element_located(
                (By.CLASS_NAME, "dashboard-container"))
        )

        browser.implicitly_wait(10)

        sleep(2)  # TODO

        original_size = browser.get_window_size()

        S = lambda X: browser.execute_script('return document.body.parentNode.scroll'+X)
        browser.set_window_size(S('Width'), S('Height') + 1000)  # May need manual adjustment

        el = browser.find_element(By.TAG_NAME, 'body')

        sleep(5)  # TODO

        el.screenshot(DUMP_ROOT + "/" + f"{sat}.png")

        browser.set_window_size(original_size['width'],
                                original_size['height'])

    except Exception as e:
        print("ERROR: {}".format(e))
        browser.quit()
        sys.exit(1)

    print(f"Screenshot {sat} {dt.fromtimestamp(int(t_from)/1000)} " +
          f"{dt.fromtimestamp(int(t_from)/1000)}")


def sat_zip(arch_name):
    with ZipFile(arch_name,
                 mode="w",
                 compression=ZIP_DEFLATED,
                 compresslevel=9) as archive:
        for file in pathlib.Path(DUMP_ROOT).iterdir():
            archive.write(file, sh.os.path.basename(file))


def main(dashboard_url):
    sh.os.makedirs(ARCH_ROOT, exist_ok=True)

    t_to = int(dt.now().timestamp() * 1000)
    t_from = t_to - PERIOD * 1000

    # цикл с таймером
    while True:
        print(f"Making report on {dt.fromtimestamp(int(t_to)/1000)}...")

        sats = cur_sats(t_from, t_to)

        # Set up selenium
        browser_options = webdriver.FirefoxOptions()
        browser_options.headless = True

        browser = webdriver.Remote(
            command_executor=f"http://{WD_HOST}:{WD_PORT}",
            options=browser_options
        )
        browser.set_window_size(1920, 1080)
        browser.implicitly_wait(10)

        sat_get(browser, dashboard_url)

        # аутентификация
        try:
            browser.find_element(By.NAME,  'username').send_keys(GF_USERNAME)
            browser.find_element(By.NAME,  'password').send_keys(GF_PASSWORD)
            browser.find_element(By.XPATH, "//button[contains(text(),'Log In')]").click()
        except NoSuchElementException:
            print("Failed to log in to Grafana")
            browser.quit()
            sys.exit(1)

        # нажать кнопку Skip
        try:
            browser.implicitly_wait(2)
            skip = browser.find_element(By.XPATH, "//a[contains(text(),'Skip')]")
            skip.location_once_scrolled_into_view
            browser.execute_script("arguments[0].click();", skip)
        except NoSuchElementException:
            pass

        # prepare folder structure
        sh.rmtree(DUMP_ROOT, ignore_errors=True)
        sh.os.mkdir(DUMP_ROOT)

        # make screenshots
        for sat in sats:
            sat_shot(browser, dashboard_url, sat, t_from, t_to)

        # Сжатие -- не очень помогло
        # https://pillow.readthedocs.io/en/stable/handbook/tutorial.html#batch-processing

        sat_zip(ARCH_ROOT + "/" +
                f"{dt.fromtimestamp(int(t_from)/1000)}" +
                f"-{dt.fromtimestamp(int(t_to)/1000)}.zip")

        sh.rmtree(DUMP_ROOT, ignore_errors=True)

        browser.quit()

        print("Making report -- Compleate")

        t_to += PERIOD * 1000
        t_from = t_to - PERIOD * 1000

        print(f"Next report on {dt.fromtimestamp(int(t_to)/1000)}")

        sleep(PERIOD)


if __name__ == "__main__":
    main(GF_URL)

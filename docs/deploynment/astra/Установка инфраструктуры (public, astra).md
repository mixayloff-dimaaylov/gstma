Установка инфраструктуры
========================

## 1. Примечание

1. Все действия выполняются от пользователя **root** (при помощи `sudo` или
   иначе).

## 2. Процесс установки

Процесс установки состоит из следующих этапов:

1. Установка Astra Linux
2. Настройка сетевого соединения
3. Установка Zookeeper
4. Установка Kafka
5. Установка Spark
6. Установка Clickhouse
7. Установка Grafana
8. Установка программы передачи данных GPS-приёмника

#### 2.1. Установка Astra Linux

Далее, описан процесс подключения CD-дисков с дополнительными пакетами для
установки компонентов комплекса.

Процесс установки дополнительного ПО с дисков состоит из 2-х этапов:

1. Добавление дисков в базу пакетного менеджера `apt`
2. Установка ПО в обычном режиме. В процессе установки пакетов будет запрошен
   соответствующий установочный диск.

##### Добавление дисков в базу пакетного менеджера `apt`

Для того чтобы использовать диск, требуется:

1. Вставить диск в привод компьютера
2. Запустить команду `sudo apt-cdrom add`
3. Дать имя текущему диску, запрашиваемое `apt-cdrom`

Описанную последовательности действий требуется повторить для всех используемых
дисков. В процессе установки пакетов будет запрошен соответствующий
установочный диск.

### 2.2. Настройка сетевого соединения

Добавьте псевдонимы хостов при помощи команды:

```sh
sudo tee -a /etc/hosts <<HOSTS
127.0.0.1 st9-ape-ionosphere2c-1
127.0.0.1 st9-ape-ionosphere2m
127.0.0.1 st9-ape-ionosphere2s-1
HOSTS
```

### 2.3. Установка Zookeeper

#### 2.3.1. Установка

```sh
# Установка
sudo apt-get install -y default-jdk

# Настройка Java
echo 'export JAVA_HOME=/usr/lib/jvm/default-java' | sudo tee /etc/profile.d/java.sh
# Для применения настроек заново зайти под текущего пользователя или выполнить
# `source /etc/profile.d/java.sh`

sudo apt-get install -y zookeeper
sudo apt-get install -y zookeeperd

# Включение автозапуска демона (опционально)
sudo systemctl enable zookeeper
```

#### 2.3.2. Настройка

Для настройки требуется распаковать файлы настроек. Подключите диск
дополнительного ПО. Распаковка производится при помощи следующей
последовательности действий:

```sh
sudo tar -xv --same-owner --same-permissions -f /media/cdrom/configs/zookeeper.tar.gz -C /
```

#### 2.3.3. Запуск

```sh
# Запуск демона
sudo systemctl start zookeeper
sudo systemctl status zookeeper
```

### 2.4. Установка Kafka

#### 2.4.1. Установка

```sh
# Kafka зависит от Java
sudo apt-get install -y default-jdk

# Распаковать Kafka
sudo mkdir -p /opt/kafka &&\
sudo tar -xzf /media/cdrom/kafka_*.tgz -C /opt/kafka --strip 1

# Добавить пользователя
sudo useradd kafka -r -U -s /bin/nologin
```

#### 2.4.2. Настройка

Для настройки требуется распаковать файлы настроек. Подключите диск
дополнительного ПО. Распаковка производится при помощи следующей
последовательности действий:

```sh
sudo tar -xv --same-owner --same-permissions -f /media/cdrom/configs/kafka.tar.gz -C /

# Добавить unit-файл Kafka в список известных
sudo systemctl daemon-reload

# Создать рабочую директорию Kafka
sudo mkdir /data
sudo mkdir -p /data/kafka-logs
sudo chown kafka:kafka /data/kafka-logs
```

#### 2.4.3. Запуск

```sh
# Активация сервиса
sudo systemctl enable kafka.service

# Запуск демона (требуется Zookeeper)
sudo systemctl start kafka.service
sudo systemctl status kafka.service
```

### 2.5. Установка Spark

#### 2.5.1. Установка

Spark устанавливается после Zookeeper и Kafka. Так же для работы разработанного
приложения требуется установленный Apache Hadoop. Процесс установки описан
далее:

```sh
# Распаковка Apacha Hadoop
sudo mkdir -p /opt/hadoop && \
sudo tar -xzf /media/cdrom/hadoop-*.tar.gz -C /opt/hadoop --strip 1

# Установка переменной окружения PATH
echo 'export PATH="$PATH:/opt/hadoop/bin"' | sudo tee /etc/profile.d/hadoop.sh
# Для применения настроек заново зайти под текущего пользователя или выполнить
# `source /etc/profile.d/hadoop.sh`
```

Установка приложения производится посредство подготовленного дистрибутива и
производится при помощи следующей последовательности действий:

```sh
sudo tar -xv --same-owner --same-permissions -f /media/cdrom/configs/spark.tar.gz -C /
sudo chmod a+x /opt/start-stream-receiver-local.sh
```

Рабочая директория Spark располагается в  `/data/spark/`. Создайте ее:

```sh
sudo mkdir -p /data/spark/
sudo chown $USER:$USER -R /data/spark/
```

#### 2.5.2. Запуск

Запустить `/opt/start-stream-receiver-local.sh` при запущенной Kafka.

### 2.6. Установка Clickhouse

#### 2.6.1 Установка

```sh
sudo apt-get install -y clickhouse-server clickhouse-client
```

#### 2.6.2. Настройка

Данные Clickhouse хранятся в директории `/data/ch/`. Предполагается, что будет
доступно не менее 80G свободного пространства. Создайте директорию с владельцем 
\- `clichouse`.

```sh
sudo mkdir -p /data/ch/
sudo chown clickhouse:clickhouse -R /data/ch/
```

Далее примените настройки.

Для настройки требуется распаковать файлы настроек. Подключите диск
дополнительного ПО. Распаковка производится при помощи следующей
последовательности действий:

```sh
sudo tar -xv --same-owner --same-permissions -f /media/cdrom/configs/clickhouse.tar.gz -C /
```

Пользователи и их пароли расположены в файле `/etc/clickhouse-server/users.xml`.

#### 2.6.3. Запуск

```sh
# Настроить автозапуск сервера Clickhouse:
sudo systemctl enable clickhouse-server

# Запустить сервис и убедиться что он запушен:
sudo systemctl start clickhouse-server
sudo systemctl status clickhouse-server
```

#### 2.6.4. Создание таблиц

Подключите диск дополнительного ПО. Выполните:

```sh
/media/cdrom/configs/clickhouse_create_queries.sh
```

### 2.7. Установка Grafana

#### 2.7.1. Установка

```sh
# Установить последний OSS-release:
sudo apt-get update
sudo apt-get install -y grafana
```

#### 2.7.2. Настройка

Для настройки требуется распаковать файлы настроек. Подключите диск
дополнительного ПО. Распаковка производится при помощи следующей
последовательности действий:

```sh
sudo tar -xv --same-owner --same-permissions -f /media/cdrom/configs/grafana.tar.gz -C /
```

Плагин для отрисовки карты спутников, дашборы, настройки Grafana
устанавливаются вместе с настройками.

Учетные данные для входа в Grafana:

- Порт веб-интерфейса: 3000 (по-умолчанию) 
- Пользователь для веб-интерфейса: admin
- Пароль от пользователя `admin`: ionadmin

#### 2.7.3. Запуск

```sh
# Настроить автозапуск сервера Grafana:
sudo systemctl enable grafana-server.service

# Запустить сервис и убедиться что он запушен:
sudo systemctl start grafana-server.service
sudo systemctl status grafana-server.service
```

### 2.8. Установка программы передачи данных GPS-приёмника

#### 2.8.0. Установка драйверов

```sh
# Для компиляции модулей ядра требуются загаловочные файлы текущего ядра
sudo apt-get install -y linux-headers-"$(uname -r)"

# Распаковать установочный дистрибутив
sudo mkdir -p /tmp/ngpsusbpackage && \
sudo tar -xzf /media/cdrom/ngpsusbpackage.tar.gz -C /tmp/ngpsusbpackage --strip 1

cd /tmp/ngpsusbpackage && \
sudo chmod a+x ngpsusb-install ngpsusb-uninstall

# Произвести установку
sudo ./ngpsusb-install

# Удалить копии установочных файлов
cd ~ && sudo rm -rf /tmp/ngpsusbpackage
```

#### 2.8.1. Установка

```sh
# Распаковать дистрибутив NovAtelLogReader
sudo mkdir /opt/NovAtelLogReader && \
sudo tar -xzf /media/cdrom/NovAtelLogReader.tar.gz -C /opt/NovAtelLogReader --strip 1

# Дать права на исполнение
sudo chmod a+x /opt/NovAtelLogReader/NovAtelLogReader
```

#### 2.8.2. Настройка

Программа настраивается через файлы `*.config` в корневой папке проекта, в
формате XML. Основной файл настроек - `NovAtelLogReader.exe.config`.

В файле настроек содержутся следующей параметры (справка):

- `ComPortSpeed` - скорость соединения с GPS-приемником, бод
- `KafkaBrokers` - IP-адресс или *hostname* сервера **st9-ape-ionosphere2c-1**
- `ComPortName`  - имя COM-порта с подключенным GPS-приемником

#### 2.8.3. Запуск

Запустить исполняемый файл `/opt/NovAtelLogReader/NovAtelLogReader` от пользователя `root`.


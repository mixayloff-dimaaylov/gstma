Мониторинг состояния Docker-контейнеров через Telegram/Monocker
===============================================================

Уведомления об изменении состояний Docker-контейнеров можно получать через
Telegram при помощи минималистичной службы Monocker, которая так же запускается
в виде Docker-контейнера. Подробнее можно прочитать на [официальной странице
проекта][monocker].

Для использования Monocker необходимо добавить соответствующее определение в
_override_-файл Docker Compose и определить значение для `MESSAGE_PLATFORM` и
`SERVER_LABEL`:

```yaml
version: "3.4"

services:
  # MONitors dOCKER
  # https://hub.docker.com/r/petersem/monocker
  monocker:
    container_name: gstma-monocker
    image: petersem/monocker:latest
    profiles: ["default"]

    environment:
      # Optional label to preface messages. Handy if you are running multiple
      # versions of Monocker
      SERVER_LABEL: 'server1'

      # Specify the messaging platform and details, or leave blank if only
      # wanting container logs (pick one only)
      MESSAGE_PLATFORM: 'telegram@your_bot_id@your_chat_id'

      # Optional - includes or excludes specified containers - default behaviour
      # is false
      LABEL_ENABLE: 'false'

      # Optional - only show when container state changes to being offline
      # (paused, exited, running (unhealthy), or dead) - default is false
      ONLY_OFFLINE_STATES: 'false'

      # [Optional] - Regardless of any other settings, you can ignore or include
      # 'exited'
      EXCLUDE_EXITED: 'false'

      # [Optional] - Set the poll period in seconds. Defaults to 10 seconds,
      # which is also the minimum.
      PERIOD: 10

      # [Optional] - Supress startup messages from being sent. Default is false
      DISABLE_STARTUP_MSG: 'false'

    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro

    restart: unless-stopped
```

И запустить соответствующим образом:

```sh
docker-compose --profile default up -d monocker
```

Monocker так же поддерживает другие сервисы для отправки уведомлений. См. на
[официальной странице проекта][monocker].

[monocker]: https://github.com/petersem/monocker

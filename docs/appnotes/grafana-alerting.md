Уведомления через Grafana Unified Alerting
==========================================

Grafana 9 имеет встроенную возможность рассылки уведомлений, если наблюдаемая
характеристика превышает установленное пороговое значение. Подробнее можно
прочитать на официальном сайте и в блоге:

- [Alerting | Grafana documentation][unif-alerting]
- [The new unified alerting system for Grafana: Everything you need to know][news-2021]
- [Grafana Alerting: Explore our latest updates in Grafana 9][news-2022]

## Настройка уведомлений через Grafana Provisioning

Интерфейс Grafana позволяет произвести все действия по настройке уведомлений в
WebUI, через браузер. Однако, этот способ не сохраняет настройки при
перезапусках Grafana. Далее будет рассмотрен способ настройки через [Grafana
Provisioning][provisioning].

Проект поставляется с набором правил, сохраненным в следующей директории (далее
директория _alerting_):

- [image_content/config/grafana/etc/grafana/provisioning/alerting][dir-alerting]

Рассмотрим пример настройки рассылки уведомлений через Telegram с использованием
предустановленных правил. Для этого необходимо:

1. Создать _contact point_ для отправки уведомлений в Telegram. Для этого
   необходимо сохранить следующий шаблон в файл `cpoints.yaml` в директории
   _alerting_, предварительно заполнив `chatid` и `bottoken`:

```yaml
# docs: https://grafana.com/docs/grafana/v9.5/alerting/set-up/provision-alerting-resources/file-provisioning/#provision-contact-points

# config file version
apiVersion: 1

# List of contact points to import or update
contactPoints:
  # <int> organization ID, default = 1
  - orgId: 1
    # <string, required> name of the contact point
    name: "Telegram chat Contact point"
    receivers:
      # Telegram notifier for internal Chat
      - name: Telegram chat Notifier
        # <string, required> type of the receiver
        # docs: https://grafana.com/docs/grafana/v9.5/alerting/set-up/provision-alerting-resources/file-provisioning/#telegram
        type: telegram
        # <string, required> unique identifier for the receiver
        uid: telegram1
        settings:
          chatid: "NNN"
          bottoken: "NNN:XXX"
          message: |
            {{ template "telegram.S4" . }}
```

2. Создать _notification policy_ для перенаправления интересующих уведомлений в
   созданный _contact point_. Для этого необходимо сохранить следующий шаблон в
   файл `policies.yaml` в директории _alerting_:

```yaml
# docs: https://grafana.com/docs/grafana/v9.5/alerting/set-up/provision-alerting-resources/file-provisioning/#provision-notification-policies

# config file version
apiVersion: 1

# List of notification policies
policies:
  # <int> organization ID, default = 1
  - orgId: 1
    # <string> name of the contact point that should be used for this route
    receiver: "Telegram chat Contact point"
    # <list> The labels by which incoming alerts are grouped together. For example,
    #        multiple alerts coming in for cluster=A and alertname=LatencyHigh would
    #        be batched into a single group.
    #
    #        To aggregate by all possible labels use the special value '...' as
    #        the sole label name, for example:
    #        group_by: ['...']
    #        This effectively disables aggregation entirely, passing through all
    #        alerts as-is. This is unlikely to be what you want, unless you have
    #        a very low alert volume or your upstream notification system performs
    #        its own grouping.
    group_by:
      - alertname
      - sat
    # <list> a list of matchers that an alert has to fulfill to match the node
    matchers:
      - S4 =~ ".*"
    # <list> Times when the route should be muted. These must match the name of a
    #        mute time interval.
    #        Additionally, the root node cannot have any mute times.
    #        When a route is muted it will not send any notifications, but
    #        otherwise acts normally (including ending the route-matching process
    #        if the `continue` option is not set)
    # mute_time_intervals:
    #   - abc
    # <duration> How long to initially wait to send a notification for a group
    #            of alerts. Allows to collect more initial alerts for the same group.
    #            (Usually ~0s to few minutes), default = 30s
    # group_wait: 30s
    # <duration> How long to wait before sending a notification about new alerts that
    #            are added to a group of alerts for which an initial notification has
    #            already been sent. (Usually ~5m or more), default = 5m
    # group_interval: 5m
    # <duration>  How long to wait before sending a notification again if it has already
    #             been sent successfully for an alert. (Usually ~3h or more), default = 4h
    # repeat_interval: 4h
    # <list> Zero or more child routes
    # routes:
    # ...
```

В данном случае, уведомлению фильтруются по метке `S4` (см. параметр
`matchers`).

3. Для того чтобы ссылки в уведомлениях имели правильный домен и были доступны
   для перехода, необходимо переопределить этот домен в _override_-файле Docker
   Compose:

```yaml
services:
  grafana:
    environment:
      GF_SERVER_DOMAIN: <имя_или_IP-адрес>
```

4. Перезапустить Grafana, чтобы применить внесенные изменения:

```sh
docker-compose --profile default up -d --force-recreate grafana
```

Правила, так же как и загруженные указанным способом настройка, нельзя изменить
из WebUI (на момент написания данного документа).

[dir-alerting]: ../../image_content/config/grafana/etc/grafana/provisioning/alerting/
[news-2021]: https://grafana.com/blog/2021/06/14/the-new-unified-alerting-system-for-grafana-everything-you-need-to-know/
[news-2022]: https://grafana.com/blog/2022/06/14/grafana-alerting-explore-our-latest-updates-in-grafana-9/
[provisioning]: https://grafana.com/docs/grafana/v9.5/alerting/set-up/provision-alerting-resources/file-provisioning/
[unif-alerting]: https://grafana.com/docs/grafana/v9.5/alerting/

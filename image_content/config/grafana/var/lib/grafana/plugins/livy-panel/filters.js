'use strict';

System.register(['moment', 'lodash'], function (_export, _context) {
    "use strict";

    var moment, _, filters;

    return {
        setters: [function (_moment) {
            moment = _moment.default;
        }, function (_lodash) {
            _ = _lodash.default;
        }],
        execute: function () {
            filters = angular.module('grafana.filters');


            filters.filter('calendar', function () {
                return function (date) {
                    return moment(date).calendar();
                };
            });

            filters.filter('dateFormat', function () {
                return function (date, format) {
                    return moment(date).format(format);
                };
            });

            filters.filter('duration', function () {
                return function (ms) {
                    var seconds = ms / 1000;

                    return [parseInt(seconds / 60 / 60), parseInt(seconds / 60 % 60), parseInt(seconds % 60)].join(":").replace(/\b(\d)\b/g, "0$1");
                };
            });

            filters.filter('startedAt', function () {
                return function (batch) {
                    var timestamp = _.get(batch, 'rm.startedTime');

                    if (timestamp) {
                        return new Date(timestamp);
                    }
                };
            });

            filters.filter('serverState', function () {
                var states = {
                    'online': 'Доступен',
                    'connecting': 'Подключение',
                    'offline': 'Недоступен',
                    'success': 'Успешно',
                    'error': 'Ошибка',
                    'pending': 'Отправка запроса'
                };

                return function (input) {
                    return states.hasOwnProperty(input) ? states[input] : input;
                };
            });

            filters.filter('serverStateIcon', function () {
                var states = {
                    'online': 'fa fa-circle',
                    'connecting': 'fa fa-circle',
                    'offline': 'fa fa-circle',
                    'success': 'fa fa-check-circle',
                    'error': 'fa fa-times-circle',
                    'pending': 'fa fa-spinner fa-spin'
                };

                return function (input) {
                    return states.hasOwnProperty(input) ? states[input] : input;
                };
            });

            filters.filter('jobState', function () {
                var states = {
                    'not_started': 'Не запущена',
                    'starting': 'Запускается',
                    'running': 'Выполняется',
                    'idle': 'Бездействует',
                    'busy': 'Выполняется',
                    'shutting_down': 'Завершается',
                    'error': 'Ошибка',
                    'dead': 'Остановлена',
                    'success': 'Выполнена'
                };

                return function (input) {
                    return states.hasOwnProperty(input) ? states[input] : input;
                };
            });
        }
    };
});
//# sourceMappingURL=filters.js.map

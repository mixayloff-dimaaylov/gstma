'use strict';

System.register(['app/plugins/sdk', 'lodash'], function (_export, _context) {
  "use strict";

  var PanelCtrl, _, _typeof, _createClass, panelDefaults, LivyCtrl;

  function _classCallCheck(instance, Constructor) {
    if (!(instance instanceof Constructor)) {
      throw new TypeError("Cannot call a class as a function");
    }
  }

  function _possibleConstructorReturn(self, call) {
    if (!self) {
      throw new ReferenceError("this hasn't been initialised - super() hasn't been called");
    }

    return call && (typeof call === "object" || typeof call === "function") ? call : self;
  }

  function _inherits(subClass, superClass) {
    if (typeof superClass !== "function" && superClass !== null) {
      throw new TypeError("Super expression must either be null or a function, not " + typeof superClass);
    }

    subClass.prototype = Object.create(superClass && superClass.prototype, {
      constructor: {
        value: subClass,
        enumerable: false,
        writable: true,
        configurable: true
      }
    });
    if (superClass) Object.setPrototypeOf ? Object.setPrototypeOf(subClass, superClass) : subClass.__proto__ = superClass;
  }

  return {
    setters: [function (_appPluginsSdk) {
      PanelCtrl = _appPluginsSdk.PanelCtrl;
    }, function (_lodash) {
      _ = _lodash.default;
    }],
    execute: function () {
      _typeof = typeof Symbol === "function" && typeof Symbol.iterator === "symbol" ? function (obj) {
        return typeof obj;
      } : function (obj) {
        return obj && typeof Symbol === "function" && obj.constructor === Symbol && obj !== Symbol.prototype ? "symbol" : typeof obj;
      };

      _createClass = function () {
        function defineProperties(target, props) {
          for (var i = 0; i < props.length; i++) {
            var descriptor = props[i];
            descriptor.enumerable = descriptor.enumerable || false;
            descriptor.configurable = true;
            if ("value" in descriptor) descriptor.writable = true;
            Object.defineProperty(target, descriptor.key, descriptor);
          }
        }

        return function (Constructor, protoProps, staticProps) {
          if (protoProps) defineProperties(Constructor.prototype, protoProps);
          if (staticProps) defineProperties(Constructor, staticProps);
          return Constructor;
        };
      }();

      panelDefaults = {
        rmServer: 'http://mesos-host:5050',
        livyServer: 'http://livy-host:8999',
        submitJar: 'hdfs:///path/to/file.jar',
        jobClassName: 'com.full.job.ClassName',
        satTemplateVar: 'sat',
        showJobList: true,
        driverMemory: '1g',
        driverCores: 2,
        executorMemory: '2g',
        executorCores: 2,
        numExecutors: 3,
        dynamicAllocation: false
      };

      _export('LivyCtrl', LivyCtrl = function (_PanelCtrl) {
        _inherits(LivyCtrl, _PanelCtrl);

        function LivyCtrl($scope, $injector, $http) {
          _classCallCheck(this, LivyCtrl);

          var _this = _possibleConstructorReturn(this, (LivyCtrl.__proto__ || Object.getPrototypeOf(LivyCtrl)).call(this, $scope, $injector));

          _.defaults(_this.panel, panelDefaults);

          _this.$http = $http;
          _this.serverState = 'connecting';
          _this.events.on('init-edit-mode', _this.onInitEditMode.bind(_this));

          _this.livyJobs = [];
          _this.frameworks = [];
          _this.skipCleanupIds = [];

          _this.updateJobs();
          return _this;
        }

        _createClass(LivyCtrl, [{
          key: 'onInitEditMode',
          value: function onInitEditMode() {
            this.addEditorTab('Options', 'public/plugins/livy-panel/editor.html', 2);
          }
        }, {
          key: '_cleanupStorage',
          value: function _cleanupStorage(activeIds) {
            activeIds = _.concat(activeIds, this.skipCleanupIds);
            this.skipCleanupIds = [];

            _(localStorage).keys().filter(function (k) {
              return k.startsWith('livy');
            }).differenceWith(activeIds, function (a, b) {
              return a == 'livy-' + b;
            }).each(function (k) {
              return localStorage.removeItem(k);
            });
          }
        }, {
          key: '_makeLivyJobModel',
          value: function _makeLivyJobModel(session) {
            return {
              id: session.id,
              appId: session.appId,
              sparkUiUrl: session.appInfo.sparkUiUrl,
              state: session.state,
              args: JSON.parse(localStorage.getItem('livy-' + session.id))
            };
          }
        }, {
          key: '_makeFrameworkModel',
          value: function _makeFrameworkModel(framework) {
            var startTime = framework.registered_time * 1000;
            var endTime = framework.unregistered_time * 1000;

            var tasks = _.concat(framework.tasks, framework.completed_tasks, framework.unreachable_tasks);
            var labels = _.chain(tasks).find('labels').get('labels').flatten().mapKeys('key').mapValues('value').value();

            var taskStats = _.countBy(tasks, 'state');

            var _getState = function _getState() {
              if (framework.active) {
                return framework.tasks.length > 0 ? 'running' : 'idle';
              } else if (framework.unregistered_time > 0) {
                if (taskStats['TASK_FAILED'] > 0) return 'error';
                if (taskStats['TASK_KILLED'] > 0) return 'dead';

                return 'success';
              }

              return 'not_started';
            };

            return {
              id: framework.id,
              idx: framework.id.slice(-4),
              name: framework.name,
              startTime: startTime,
              endTime: endTime,
              isRunning: endTime == 0,
              isComplete: endTime > 0,
              totalTime: (endTime || new Date().getTime()) - startTime,
              activeTasks: (taskStats['TASK_RUNNING'] || 0) + (taskStats['TASK_STARTING'] || 0),
              failedTasks: taskStats['TASK_FAILED'] || 0,
              killedTasks: taskStats['TASK_KILLED'] || 0,
              finishedTasks: taskStats['TASK_FINISHED'] || 0,
              resources: framework.resources,
              state: _getState(),
              args: labels
            };
          }
        }, {
          key: 'updateJobs',
          value: function updateJobs() {
            var _this2 = this;

            if (this.panel.showJobList) {
              var batches = this.$http.get(this.panel.livyServer + '/batches');
              var frameworks = this.$http.get(this.panel.rmServer + '/frameworks');

              batches.then(function (response) {
                var ids = _.map(response.data.sessions, 'id');
                _this2._cleanupStorage(ids);
                _this2.livyJobs = _.map(response.data.sessions, _this2._makeLivyJobModel);
                _this2.serverState = 'online';
              }, function (error) {
                return _this2.serverState = 'offline';
              });

              frameworks.then(function (response) {
                var active = _(response.data.frameworks).orderBy('registered_time', 'desc').map(_this2._makeFrameworkModel);
                var completed = _(response.data.completed_frameworks).orderBy('unregistered_time', 'desc').map(_this2._makeFrameworkModel);
                _this2.frameworks = _.concat(active.value(), completed.value());
              }, function (error) {
                return _this2.serverState = 'offline';
              });
            } else {
              this.$http.get(this.panel.livyServer + '/ping').then(function (response) {
                return _this2.serverState = 'online';
              }, function (error) {
                return _this2.serverState = 'offline';
              });
            }

            this.$timeout(function () {
              return _this2.updateJobs();
            }, 3000);
          }
        }, {
          key: 'canSubmit',
          value: function canSubmit() {
            return _typeof(this.dashboard.time.from) === "object" && this.serverState === 'online' && this.requestState !== 'pending';
          }
        }, {
          key: 'submitJob',
          value: function submitJob() {
            var _this3 = this;

            var _escapeLabel = function _escapeLabel(v) {
              return _.toString(v).replace(/,/g, '\\,').replace(/:/g, '\\:');
            };
            var _makeLabel = function _makeLabel(v, k) {
              return _escapeLabel(k) + ':' + _escapeLabel(v);
            };

            var args = {
              timeFrom: this.dashboard.time.from.format('x'),
              timeTo: this.dashboard.time.to.format('x'),
              sat: _.find(this.dashboard.templating.list, { name: this.panel.satTemplateVar }).current.value
            };

            var options = {
              "file": this.panel.submitJar,
              "className": this.panel.jobClassName,
              "driverMemory": this.panel.driverMemory,
              "driverCores": this.panel.driverCores,
              "executorMemory": this.panel.executorMemory,
              "executorCores": this.panel.executorCores,
              "numExecutors": this.panel.numExecutors,
              "conf": {
                "spark.dynamicAllocation.enabled": this.panel.dynamicAllocation,
                "spark.mesos.task.labels": _.map(args, _makeLabel).join(',')
              },
              "args": _.values(args)
            };

            var request = this.$http.post(this.panel.livyServer + '/batches', options);

            this.serverState = 'pending';

            request.then(function (response) {
              _this3.serverState = 'success';

              if (response.data.id) {
                args.submitted = new Date().getTime();
                _this3.livyJobs.push(_this3._makeLivyJobModel(response.data));
                _this3.skipCleanupIds.push(response.data.id);
                localStorage.setItem("livy-" + response.data.id, JSON.stringify(args));
              }
            }, function (error) {
              return _this3.serverState = 'error';
            });
          }
        }, {
          key: 'killBatch',
          value: function killBatch(id) {
            var _this4 = this;

            var request = this.$http.delete(this.panel.livyServer + '/batches/' + id);
            this.serverState = 'pending';
            request.then(function (response) {
              return _this4.serverState = 'success';
            }, function (error) {
              return _this4.serverState = 'error';
            });
          }
        }, {
          key: 'killSession',
          value: function killSession(id) {
            var _this5 = this;

            var request = this.$http.delete(this.panel.livyServer + '/sessions/' + id);
            this.serverState = 'pending';
            request.then(function (response) {
              return _this5.serverState = 'success';
            }, function (error) {
              return _this5.serverState = 'error';
            });
          }
        }, {
          key: 'killFramework',
          value: function killFramework(id) {
            var _this6 = this;

            var request = this.$http.post(this.panel.rmServer + '/master/teardown', "frameworkId=" + id);
            this.serverState = 'pending';
            request.then(function (response) {
              return _this6.serverState = 'success';
            }, function (error) {
              return _this6.serverState = 'error';
            });
          }
        }]);

        return LivyCtrl;
      }(PanelCtrl));

      _export('LivyCtrl', LivyCtrl);

      LivyCtrl.templateUrl = 'module.html';
    }
  };
});
//# sourceMappingURL=livy_ctrl.js.map

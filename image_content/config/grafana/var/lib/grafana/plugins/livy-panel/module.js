'use strict';

System.register(['./livy_ctrl', './filters', './css/panel.css!'], function (_export, _context) {
  "use strict";

  var LivyCtrl;
  return {
    setters: [function (_livy_ctrl) {
      LivyCtrl = _livy_ctrl.LivyCtrl;
    }, function (_filters) {}, function (_cssPanelCss) {}],
    execute: function () {
      _export('PanelCtrl', LivyCtrl);
    }
  };
});
//# sourceMappingURL=module.js.map

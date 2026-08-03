var API = require('../utils/api');
var requestHandler = {
    url: '',
    data: {},
    method: '',
    success: function (res) {
    },
    fail: function () {
    },
    complete: function () {
    }
  }
   
  function request(requestHandler) {
    var data = requestHandler.data;
    var url = requestHandler.url;
    var method = requestHandler.method;
	var header = {
		'Content-Type':'application/x-www-form-urlencoded'
	};
	if(requestHandler.header){
		header = requestHandler.header;
	}
	if(API.getKey()!=""){
		header.Accept = "application/json; charset=utf-8"
		header.key = API.getKey();
	}
    var uniRequestOptions = {
      url: url,
      data: data,
	  header:header,
      method: method,
      success: function (res) {     
        if (typeof requestHandler.success === 'function') {
          requestHandler.success(res)
        }
      },
      fail: function (error) {
        if (typeof requestHandler.fail === 'function') {
          requestHandler.fail(error);
        }
      },
	  // complete 在成功、失败和超时后都会执行。页面使用它释放请求锁，
	  // 因此不能只在 success/fail 中解锁，否则异常分支容易留下永久加载状态。
      complete: function (result) {
        if (typeof requestHandler.complete === 'function') {
          requestHandler.complete(result);
        }
      }
    };
	// 仅在调用方明确指定时设置。上传、支付等慢接口继续使用平台默认值，
	// 消息列表等普通查询可以单独设置较短超时，避免页面长时间处于等待状态。
	if (typeof requestHandler.timeout === 'number') {
		uniRequestOptions.timeout = requestHandler.timeout;
	}
	if (requestHandler.dataType) {
		uniRequestOptions.dataType = requestHandler.dataType;
	}
    uni.request(uniRequestOptions)
  }
   
  module.exports = {
    request: request
  }

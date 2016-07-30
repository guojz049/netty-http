package org.abcs.netty.http.servlet;

import org.abcs.netty.http.util.SendError;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午7:03:12
 * @类说明:
 * @版本 xx
 */
public abstract class HttpServlet {

	public final void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
		Method method = request.method();
		switch (method) {
			case Get :
				doGet(request, response);
				break;
			case Post :
				doPost(request, response);
				break;
			case Other :
				SendError.sendNoImplemented(request.handlerContext(), "server no implemented this method");
				break;
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws Exception {
		JSONObject result = new JSONObject();
		result.put("request", request.toJson());
		result.put("response", response.toJson());

		response.content(JSON.toJSONString(result, true));
		response.sendCustom();
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
		JSONObject result = new JSONObject();
		result.put("request", request.toJson());
		result.put("response", response.toJson());

		response.content(JSON.toJSONString(result, true));
		response.sendCustom();
	}

	public static enum Method {
		Get("GET"), Post("POST"), Other("Other");
		public String type;
		private Method(String type) {
			this.type = type;
		}
	}

}

package org.abcs.netty.http.servlet;

import org.abcs.netty.http.util.SendError;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午7:03:12
 * @类说明:
 * @版本 xx
 */
public abstract class HttpServlet {

	protected void service(HttpServletRequest request, HttpServletResponse response) throws Exception {
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
		// FIXME
		// 打印 默认的 request 对象.调用 response 中的方法
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// FIXME
		// 打印 默认的 request 对象.调用 response 中的方法
	}

	public static enum Method {
		Get("GET"), Post("POST"), Other("Other");
		public String type;
		private Method(String type) {
			this.type = type;
		}
	}

}

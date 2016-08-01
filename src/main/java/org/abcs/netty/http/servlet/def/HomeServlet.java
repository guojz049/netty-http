package org.abcs.netty.http.servlet.def;

import java.io.File;
import java.net.URL;

import org.abcs.netty.http.servlet.HttpServlet;
import org.abcs.netty.http.servlet.HttpServletRequest;
import org.abcs.netty.http.servlet.HttpServletResponse;

/**
 * @作者 Mitkey
 * @时间 2016年8月1日 上午11:10:20
 * @类说明:
 * @版本 xx
 */
public class HomeServlet extends HttpServlet {

	private File file;

	public HomeServlet() {
		URL resource = HomeServlet.class.getClassLoader().getResource("index.html");
		file = new File(resource.getFile());
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws Exception {
		response.sendForbidden();
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
		response.content(file);
	}

}

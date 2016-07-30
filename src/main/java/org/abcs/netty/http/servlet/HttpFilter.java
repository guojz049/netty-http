package org.abcs.netty.http.servlet;

/**
 * @作者 Mitkey
 * @时间 2016年7月27日 下午7:10:21
 * @类说明:
 * @版本 xx
 */
public abstract class HttpFilter {

	/** 返回 true 表示执行下一步，否则中断 */
	public boolean doFilter(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// do some logic
		return true;
	}

}

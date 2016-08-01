package org.abcs.netty.http.handler;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import org.abcs.netty.http.AbcsNettyHttpServerSetting;
import org.abcs.netty.http.servlet.HttpFilter;
import org.abcs.netty.http.servlet.HttpServlet;
import org.abcs.netty.http.servlet.HttpServletRequest;
import org.abcs.netty.http.servlet.HttpServletResponse;
import org.abcs.netty.http.servlet.def.FileServlet;
import org.abcs.netty.http.servlet.def.HomeServlet;
import org.abcs.netty.http.util.SendError;
import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午5:50:03
 * @类说明:转换器插头 handler
 * @版本 xx
 */
public class ConverterPlugHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final Logger LOGGER = Logger.getLogger(ConverterPlugHandler.class);

	private final HttpServlet fileServlet;
	private final HttpServlet homeServlet;
	private AbcsNettyHttpServerSetting config;

	public ConverterPlugHandler(AbcsNettyHttpServerSetting config) {
		this.config = config;
		this.fileServlet = new FileServlet(config);
		this.homeServlet = new HomeServlet();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		// 解码失败的原因可能是向服务器发送的数据太大。如 get 请求在 url 中存放的字节数有限
		if (!request.decoderResult().isSuccess()) {
			SendError.sendBadRequest(ctx);
			return;
		}
		if (HttpUtil.is100ContinueExpected(request)) {
			ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
		}

		// 构建 request 和 response
		HttpServletRequest httpServletRequest = HttpServletRequest.builder(ctx, request);
		HttpServletResponse httpServletResponse = HttpServletResponse.builder(ctx, httpServletRequest);
		// 进入过滤器处理中断，不进入 servlet
		if (!doFilter(httpServletRequest, httpServletResponse)) {
			return;
		}
		// 进入 servlet 处理，无此 servlet 处理
		if (!doServlet(httpServletRequest, httpServletResponse)) {
			// 进入文件 servlet 处理....
			// http://127.0.0.1:8080/?op=file
			Object object = httpServletRequest.params().get("op");
			if ("file".equals(object)) {
				fileServlet.service(httpServletRequest, httpServletResponse);
			} else {
				homeServlet.service(httpServletRequest, httpServletResponse);
			}
			return;
		}

		JSONObject result = new JSONObject();
		result.put("request", httpServletRequest.toJson());
		result.put("response", httpServletResponse.toJson());
		throw new RuntimeException("服务器逻辑错误，不应该进入该流程。 request 与 response：" + JSON.toJSONString(result, true));
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LOGGER.error(ctx.channel() + " exceptionCaught", cause);
		if (ctx.channel().isActive()) {
			SendError.sendServerError(ctx);
		}
	}

	private boolean doServlet(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// 查找该 path 对应 servlet
		HttpServlet httpServlet = config.matchingServlet(request.path());
		if (httpServlet == null) {
			// 未找到对应 servlet
			return false;
		}
		// 进入处理匹配的 servlet
		httpServlet.service(request, response);
		return true;
	}

	private boolean doFilter(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// 查找全局过滤器
		HttpFilter httpFilter = config.matchingFilter(AbcsNettyHttpServerSetting.MappingAll);
		// 若全局过滤器中断
		if (httpFilter != null && !httpFilter.doFilter(request, response)) {
			return false;
		}

		// 查找该 path 对应的过滤器
		httpFilter = config.matchingFilter(request.path());
		// 若对应过滤器中断
		if (httpFilter != null && !httpFilter.doFilter(request, response)) {
			return false;
		}
		return true;
	}

}

package org.abcs.netty.http.servlet;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;

import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.CharsetUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午7:04:59
 * @类说明:
 * @版本 xx
 */
public class HttpServletResponse {
	private static final SimpleDateFormat Date_Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

	private ChannelHandlerContext ctx;
	private FullHttpResponse response;
	private Charset charset;
	private ByteBuf content;

	private HttpServletResponse(ChannelHandlerContext ctx, FullHttpRequest request) {
		this(ctx, request, CharsetUtil.UTF_8);
	}
	public HttpServletResponse(ChannelHandlerContext ctx, FullHttpRequest request, Charset charset) {
		this.ctx = ctx;
		this.charset = charset;
		this.content = Unpooled.EMPTY_BUFFER;
		this.response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
		keepAlive(HttpUtil.isKeepAlive(request));
	}

	public HttpServletResponse contentTypeJson() {
		setContentType("application/json");
		return this;
	}
	public HttpServletResponse contentTypeTextPlain() {
		setContentType("text/plain");
		return this;
	}
	public HttpServletResponse contentTypeTextHtml() {
		setContentType("text/html");
		return this;
	}
	public HttpServletResponse contentTypeTextXml() {
		setContentType("text/xml");
		return this;
	}
	public HttpServletResponse contentLength(int length) {
		response.headers().set(HttpHeaderNames.CONTENT_LENGTH, length);
		return this;
	}
	public HttpServletResponse cookie(Cookie cookie) {
		if (cookie == null) {
			throw new IllegalArgumentException("add cookie can not null");
		}
		response.headers().add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
		return this;
	}
	public HttpServletResponse date(Date date) {
		response.headers().set(HttpHeaderNames.DATE, Date_Format.format(date));
		return this;
	}
	public HttpServletResponse dataAndCache(long lastModify, int cacheSeconds) {
		Calendar time = Calendar.getInstance();
		String dateString = Date_Format.format(time.getTime());
		time.add(Calendar.SECOND, cacheSeconds);// 当前时间加缓存时间
		String expiresString = Date_Format.format(time.getTime());

		response.headers().set(DATE, dateString);// 请求时间头
		response.headers().set(EXPIRES, expiresString);// 有效时间头。即缓存
		response.headers().set(CACHE_CONTROL, "private, max-age=" + cacheSeconds);
		response.headers().set(LAST_MODIFIED, Date_Format.format(new Date(lastModify)));

		return this;
	}
	public HttpServletResponse httpVersion(HttpVersion version) {
		response.setProtocolVersion(version);
		return this;
	}
	public HttpServletResponse status(HttpResponseStatus status) {
		response.setStatus(status);
		return this;
	}
	public HttpServletResponse keepAlive(boolean keepAlive) {
		HttpUtil.setKeepAlive(response, keepAlive);
		return this;
	}

	public HttpServletResponse content(String str) {
		contentTypeTextPlain();
		content = Unpooled.copiedBuffer(str, charset);
		return this;
	}
	public HttpServletResponse content(JSONArray array) {
		contentTypeJson();
		byte[] bytes = array.toJSONString().getBytes(charset);
		content = Unpooled.copiedBuffer(bytes);
		return this;
	}
	public HttpServletResponse content(JSONObject object) {
		contentTypeJson();
		byte[] bytes = object.toJSONString().getBytes(charset);
		content = Unpooled.copiedBuffer(bytes);
		return this;
	}

	public void sendOk() {
		ChannelFuture future = ctx.writeAndFlush(response.replace(content));
		if (!HttpUtil.isKeepAlive(response)) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/** 302 重定向 */
	public void sendRedirect(String newUri) {
		response.setStatus(HttpResponseStatus.FOUND);
		response.headers().set(HttpHeaderNames.LOCATION, newUri);
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}
	/** 403 已接受请求，但拒绝执行 */
	public void sendForbidden(Object... des) {
		sendError(FORBIDDEN, des);
	}
	/** 404 未找到资源 */
	public void sendNotFound(Object... des) {
		sendError(NOT_FOUND, des);
	}
	/** 405 header 的 method 错误 */
	public void sendMethodError(Object... des) {
		sendError(METHOD_NOT_ALLOWED, des);
	}
	/** 400 错误请求 */
	public void sendBadRequest(Object... des) {
		sendError(BAD_REQUEST, des);
	}
	/** 500 内部服务器错误 */
	public void sendServerError(Object... des) {
		sendError(INTERNAL_SERVER_ERROR, des);
	}
	/** 501 服务器未实行 */
	public void sendNoImplemented(Object... des) {
		sendError(NOT_IMPLEMENTED, des);
	}

	private void setContentType(String type) {
		response.headers().set(CONTENT_TYPE, type + "; charset=" + charset);
	}
	private void sendError(HttpResponseStatus status, Object... des) {
		// 构建错误信息
		JSONObject result = new JSONObject();
		result.put("status", status);
		result.put("error", JSON.toJSON(des));

		content(result);
		contentTypeTextPlain();
		contentLength(response.content().readableBytes());

		// 以错误状态信息响应给客户端，需要关闭连接
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	public static HttpServletResponse builder(ChannelHandlerContext ctx, FullHttpRequest request) {
		return new HttpServletResponse(ctx, request);
	}
	public static HttpServletResponse builder(ChannelHandlerContext ctx, FullHttpRequest request, Charset charset) {
		return new HttpServletResponse(ctx, request, charset);
	}

}

package org.abcs.netty.http.servlet;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpHeaderNames.SET_COOKIE;
import static io.netty.handler.codec.http.HttpHeaderNames.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaderValues.CHUNKED;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;

import java.io.File;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午7:04:59
 * @类说明:
 * @版本 xx
 */
public class HttpServletResponse {
	public static final SimpleDateFormat Date_Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
	private static final Logger LOGGER = Logger.getLogger(HttpServletResponse.class);
	private static final int Cache_Seconds = 2 * 60;// 2分钟的缓存时间

	private ChannelHandlerContext ctx;
	private HttpServletRequest servletRequest;
	private HttpVersion version;
	private HttpResponseStatus status;
	private HttpHeaders headers;
	private Object content;
	private Charset charset;
	private boolean keepAlive = false;
	private boolean isSended = false;

	private HttpServletResponse(ChannelHandlerContext ctx, HttpServletRequest request) {
		this(ctx, request, CharsetUtil.UTF_8);
	}
	public HttpServletResponse(ChannelHandlerContext ctx, HttpServletRequest request, Charset charset) {
		this.ctx = ctx;
		this.servletRequest = request;
		this.version = HttpVersion.HTTP_1_1;
		this.status = HttpResponseStatus.OK;
		this.headers = new DefaultHttpHeaders();
		this.content = Unpooled.EMPTY_BUFFER;
		this.charset = charset;
		this.keepAlive = request.isKeepAlive();
	}
	/** 自定义 httpRequest */
	public HttpServletRequest servletRequest() {
		return servletRequest;
	}
	/** 响应的头信息 */
	public HttpHeaders headers() {
		return headers;
	}
	/** 设置 contentType 为 application/json */
	public HttpServletResponse contentTypeJson() {
		headers.set(CONTENT_TYPE, "application/json; charset=" + charset);
		return this;
	}
	/** 设置 contentType 为 text/plain */
	public HttpServletResponse contentTypeTextPlain() {
		headers.set(CONTENT_TYPE, "text/plain; charset=" + charset);
		return this;
	}
	/** 设置 contentType 为 text/html */
	public HttpServletResponse contentTypeTextHtml() {
		headers.set(CONTENT_TYPE, "text/html; charset=" + charset);
		return this;
	}
	/** 设置 contentType 为 text/xml */
	public HttpServletResponse contentTypeTextXml() {
		headers.set(CONTENT_TYPE, "text/xml; charset=" + charset);
		return this;
	}
	/** 先客户端写入 cookie */
	public HttpServletResponse cookie(Cookie cookie) {
		if (cookie == null) {
			throw new IllegalArgumentException("add cookie can not null");
		}
		headers.add(SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
		return this;
	}
	/** 设置日期信息 */
	public HttpServletResponse date(Date date) {
		headers.set(DATE, Date_Format.format(date));
		return this;
	}
	/** 设置日期和缓存信息 */
	public HttpServletResponse dataAndCache(long lastModify, int cacheSeconds) {
		Calendar time = Calendar.getInstance();
		String dateString = Date_Format.format(time.getTime());
		time.add(Calendar.SECOND, cacheSeconds);// 当前时间加缓存时间
		String expiresString = Date_Format.format(time.getTime());

		headers.set(DATE, dateString);// 请求时间头
		headers.set(EXPIRES, expiresString);// 有效时间头。即缓存
		headers.set(CACHE_CONTROL, "private, max-age=" + cacheSeconds);
		headers.set(LAST_MODIFIED, Date_Format.format(new Date(lastModify)));
		return this;
	}
	/** 设置响应的 http 协议版本 */
	public HttpServletResponse httpVersion(HttpVersion version) {
		this.version = version;
		return this;
	}
	/** 设置响应状态码 */
	public HttpServletResponse status(HttpResponseStatus status) {
		this.status = status;
		return this;
	}
	/** 设置是否为长连接 */
	public HttpServletResponse keepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}
	/** 响应内容为 file（文件） */
	public HttpServletResponse content(File file) {
		content = file;
		return this;
	}
	/** 响应内容为普通文本 */
	public HttpServletResponse content(String str) {
		contentTypeTextPlain();
		content = Unpooled.copiedBuffer(str, charset);
		return this;
	}
	/** 响应内容为json */
	public HttpServletResponse content(JSON json) {
		contentTypeJson();
		content(json.toJSONString());
		return this;
	}

	/** 302 重定向 */
	public void sendRedirect(String newUri) {
		// 重定向需要关闭 chanel
		status(FOUND).keepAlive(false).headers().set(LOCATION, newUri);
	}
	/** 403 已接受请求，但拒绝执行 */
	public void sendForbidden(Object... des) {
		status(FORBIDDEN).keepAlive(false).content(JSON.toJSONString(des));
	}
	/** 404 未找到资源 */
	public void sendNotFound(Object... des) {
		status(NOT_FOUND).keepAlive(false).content(JSON.toJSONString(des));
	}
	/** 405 header 的 method 错误 */
	public void sendMethodError(Object... des) {
		status(METHOD_NOT_ALLOWED).keepAlive(false).content(JSON.toJSONString(des));
	}
	/** 400 错误请求 */
	public void sendBadRequest(Object... des) {
		status(BAD_REQUEST).keepAlive(false).content(JSON.toJSONString(des));
	}
	/** 500 内部服务器错误 */
	public void sendServerError(Object... des) {
		status(INTERNAL_SERVER_ERROR).keepAlive(false).content(JSON.toJSONString(des));
	}
	/** 501 服务器未实行 */
	public void sendNoImplemented(Object... des) {
		status(NOT_IMPLEMENTED).keepAlive(false).content(JSON.toJSONString(des));
	}
	/** 304 未修改该文件 */
	public void sendNotModified(Object... des) {
		date(new Date()).status(NOT_MODIFIED).keepAlive(false).content(JSON.toJSONString(des).toString());
	}
	public JSONObject toJson() {
		// 若有新的描述信息，需要加入 TODO
		JSONObject result = new JSONObject();
		result.put("channelId", ctx.channel().id().toString());
		result.put("headers", JSON.toJSON(headers));
		result.put("content-encoding", charset);
		if (content instanceof ByteBuf) {
			ByteBuf tempByteBuf = (ByteBuf) content;
			result.put("content", tempByteBuf.toString(charset));
		} else {
			result.put("content", content);
		}
		result.put("protocol", version.text());
		result.put("keepAlive", keepAlive);
		result.put("status", status.toString());
		return result;
	}

	/** 结构本身会调用该方法发送。但是也可主动调用该方法 */
	public void autoSendable() throws Exception {
		if (isSended) {
			return;
		}

		ChannelFuture lastFuture = null;
		if (content instanceof File) {
			// 文件
			File file = (File) content;
			// 设置为分块传输，当前文件内容长度不确定时使用
			// 因为使用的是 HttpChunkedInput 分块传输，分割传输的长度是不确定的。
			// 不要同时配置 content_length 属性
			headers().set(TRANSFER_ENCODING, CHUNKED);
			// 设置文件类型
			headers().set(CONTENT_TYPE, parseFileMimeType(file.getName()));
			// 设置缓存
			dataAndCache(file.lastModified(), Cache_Seconds);

			// 写入包含文件类型描述相关信息的 response 。不需要包含 content
			HttpResponse response = new DefaultHttpResponse(version, status, headers);
			if (keepAlive) {
				HttpUtil.setKeepAlive(response, true);
			}
			ctx.write(response);

			// 写入具体文件内容,HttpChunkedInput 内部包含结束标记 LastHttpContent。
			HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedFile(file));
			// 使用新的进程进行写入操作
			ChannelProgressivePromise promise = ctx.newProgressivePromise();
			// 文件下载进度监听
			FileDownloadProgressiveFutureListener listener = new FileDownloadProgressiveFutureListener();

			lastFuture = ctx.writeAndFlush(chunkedInput, promise).addListener(listener);
		} else {
			// 普通文本
			FullHttpResponse fullHttpResponse = convertFullHttpResponse();
			// 若是长连接且不需要关闭，响应头信息配置为 长连接
			if (keepAlive) {
				HttpUtil.setKeepAlive(fullHttpResponse, true);
			}
			lastFuture = ctx.writeAndFlush(fullHttpResponse);
		}

		// 不是长连接。需要关闭
		if (!keepAlive) {
			lastFuture.addListener(ChannelFutureListener.CLOSE);
		}
		isSended = true;
	}

	private FullHttpResponse convertFullHttpResponse() {
		ByteBuf tempByteBuf = (ByteBuf) content;
		headers.set(CONTENT_LENGTH, tempByteBuf.readableBytes());
		headers.set(CONTENT_ENCODING, charset);
		return new DefaultFullHttpResponse(version, status, tempByteBuf, headers, new DefaultHttpHeaders());
	}

	/** 根据文件名字解析出文件类型.默认 二进制 格式 */
	private String parseFileMimeType(String fileName) {
		String typeFor = URLConnection.getFileNameMap().getContentTypeFor(fileName);
		if (typeFor == null || typeFor.trim().length() == 0) {
			// 无法识别默认使用数据流
			typeFor = "application/octet-stream";
		}
		return typeFor;
	}

	private static final class FileDownloadProgressiveFutureListener implements ChannelProgressiveFutureListener {
		@Override
		public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
			LOGGER.info(future.channel() + " download transfer progress：" + progress + " / " + total);
		}
		@Override
		public void operationComplete(ChannelProgressiveFuture future) {
			LOGGER.info(future.channel() + " download transfer complete.");
		}
	}

	public static HttpServletResponse builder(ChannelHandlerContext ctx, HttpServletRequest request) {
		return new HttpServletResponse(ctx, request);
	}
	public static HttpServletResponse builder(ChannelHandlerContext ctx, HttpServletRequest request, Charset charset) {
		return new HttpServletResponse(ctx, request, charset);
	}

}

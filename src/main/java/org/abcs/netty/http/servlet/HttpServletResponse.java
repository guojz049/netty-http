package org.abcs.netty.http.servlet;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
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
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import javax.activation.MimetypesFileTypeMap;

import org.apache.log4j.Logger;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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
import io.netty.handler.codec.http.HttpHeaderNames;
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
	private static final int Cache_Seconds = 60;

	private ChannelHandlerContext ctx;
	private HttpServletRequest servletRequest;
	private HttpVersion version;
	private HttpResponseStatus status;
	private HttpHeaders headers;
	private Object content;
	private Charset charset;
	private boolean keepAlive = false;

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
	public HttpServletRequest servletRequest() {
		return servletRequest;
	}
	public HttpHeaders headers() {
		return headers;
	}
	public HttpServletResponse contentTypeJson() {
		headers.set(CONTENT_TYPE, "application/json; charset=" + charset);
		return this;
	}
	public HttpServletResponse contentTypeTextPlain() {
		headers.set(CONTENT_TYPE, "text/plain; charset=" + charset);
		return this;
	}
	public HttpServletResponse contentTypeTextHtml() {
		headers.set(CONTENT_TYPE, "text/html; charset=" + charset);
		return this;
	}
	public HttpServletResponse contentTypeTextXml() {
		headers.set(CONTENT_TYPE, "text/xml; charset=" + charset);
		return this;
	}
	public HttpServletResponse cookie(Cookie cookie) {
		if (cookie == null) {
			throw new IllegalArgumentException("add cookie can not null");
		}
		headers.add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
		return this;
	}
	public HttpServletResponse date(Date date) {
		headers.set(HttpHeaderNames.DATE, Date_Format.format(date));
		return this;
	}
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
	public HttpServletResponse httpVersion(HttpVersion version) {
		this.version = version;
		return this;
	}
	public HttpServletResponse status(HttpResponseStatus status) {
		this.status = status;
		return this;
	}
	public HttpServletResponse keepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	public HttpServletResponse content(File file) {
		content = file;
		return this;
	}
	public HttpServletResponse content(String str) {
		contentTypeTextPlain();
		content = Unpooled.copiedBuffer(str, charset);
		return this;
	}
	public HttpServletResponse content(JSONArray array) {
		contentTypeJson();
		content(array.toJSONString());
		return this;
	}
	public HttpServletResponse content(JSONObject object) {
		contentTypeJson();
		content(object.toJSONString());
		return this;
	}

	public void sendCustom() throws Exception {
		ChannelFuture future;
		if (content instanceof File) {
			// 文件
			File file = (File) content;
			try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
				long fileLength = raf.length();

				// 设置内容长度
				headers.set(CONTENT_LENGTH, fileLength);
				// 设置文件类型
				headers.set(CONTENT_TYPE, new MimetypesFileTypeMap().getContentType(file.getPath()));
				// 设置缓存
				dataAndCache(file.lastModified(), Cache_Seconds);

				// 写入包含文件类型描述相关信息的 response 。不需要包含 content
				HttpResponse response = new DefaultHttpResponse(version, status, headers);
				if (keepAlive) {
					HttpUtil.setKeepAlive(response, true);
				}
				ctx.write(response);

				// 写入具体文件内容,HttpChunkedInput 内部包含结束标记 LastHttpContent。但是不知道 write 没有
				HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192));
				// 使用新的进程进行写入操作
				ChannelProgressivePromise promise = ctx.newProgressivePromise();
				// 文件下载进度监听
				FileDownloadProgressiveFutureListener listener = new FileDownloadProgressiveFutureListener(file);
				future = ctx.writeAndFlush(chunkedInput, promise).addListener(listener);
			}
		} else {
			// 普通文本
			future = ctx.writeAndFlush(setUpResponse(false));
		}

		if (!keepAlive) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/** 302 重定向 */
	public void sendRedirect(String newUri) {
		status(HttpResponseStatus.FOUND);
		headers.set(HttpHeaderNames.LOCATION, newUri);
		// 重定向需要关闭 chanel
		ctx.writeAndFlush(setUpResponse(true)).addListener(ChannelFutureListener.CLOSE);
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
	/** 304 未修改该文件 */
	public void sendNotModified(Object... des) {
		date(new Date());
		sendError(NOT_MODIFIED, des);
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
	@Override
	public String toString() {
		return JSON.toJSONString(toJson(), true);
	}

	private void sendError(HttpResponseStatus status, Object... des) {
		// 构建错误信息
		JSONObject result = new JSONObject();
		result.put("status", status.toString());
		result.put("error", JSON.toJSON(des));

		// 设置信息
		status(status).content(result.toJSONString());

		// 以错误状态信息响应给客户端，需要关闭连接
		ctx.writeAndFlush(setUpResponse(true)).addListener(ChannelFutureListener.CLOSE);
	}
	private FullHttpResponse setUpResponse(boolean close) {
		ByteBuf tempByteBuf = (ByteBuf) content;
		headers.set(HttpHeaderNames.CONTENT_LENGTH, tempByteBuf.readableBytes());
		headers.set(HttpHeaderNames.CONTENT_ENCODING, charset);

		FullHttpResponse response = new DefaultFullHttpResponse(version, status, tempByteBuf, headers, new DefaultHttpHeaders());
		HttpUtil.setKeepAlive(response, !close && keepAlive);
		return response;
	}

	private static final class FileDownloadProgressiveFutureListener implements ChannelProgressiveFutureListener {
		private String filePath;
		public FileDownloadProgressiveFutureListener(File file) {
			filePath = file.toString();
		}
		@Override
		public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
			if (total < 0) { // total unknown
				LOGGER.info(future.channel() + "【" + filePath + "】" + " download transfer progress：" + progress);
			} else {
				LOGGER.info(future.channel() + "【" + filePath + "】" + " download transfer progress：" + progress + " / " + total);
			}
		}
		@Override
		public void operationComplete(ChannelProgressiveFuture future) {
			LOGGER.info(future.channel() + "【" + filePath + "】" + " download transfer complete.");
		}
	}

	public static HttpServletResponse builder(ChannelHandlerContext ctx, HttpServletRequest request) {
		return new HttpServletResponse(ctx, request);
	}
	public static HttpServletResponse builder(ChannelHandlerContext ctx, HttpServletRequest request, Charset charset) {
		return new HttpServletResponse(ctx, request, charset);
	}

}

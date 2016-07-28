package org.abcs.netty.http.handler;

import static io.netty.handler.codec.http.HttpHeaderNames.CACHE_CONTROL;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.DATE;
import static io.netty.handler.codec.http.HttpHeaderNames.EXPIRES;
import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static io.netty.handler.codec.http.HttpHeaderNames.LOCATION;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpResponseStatus.FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;

import org.abcs.netty.http.AbcsNettyHttpServerSetting;
import org.abcs.netty.http.servlet.HttpFilter;
import org.abcs.netty.http.util.SendError;
import org.apache.log4j.Logger;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午5:50:03
 * @类说明:转换器插头 handler
 * @版本 xx
 */
public class ConverterPlugHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final Logger LOGGER = Logger.getLogger(ConverterPlugHandler.class);
	private static final SimpleDateFormat Date_Format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
	private static final Pattern Insecure_Uri = Pattern.compile(".*[<>&\"].*");
	private static final Pattern Allowed_File_Name = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");
	private static final int Cache_Seconds = 60;

	private AbcsNettyHttpServerSetting config;

	public ConverterPlugHandler(AbcsNettyHttpServerSetting config) {
		this.config = config;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		if (!request.decoderResult().isSuccess()) {
			SendError.sendBadRequest(ctx);
			return;
		}
		if (HttpUtil.is100ContinueExpected(request)) {
			ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
		}

		// 获取 url
		String uri = request.uri();
		// if ("/".equals(uri)) {
		// // 主页
		// // TODO
		// return;
		// }
		// if ("/favicon.ico".equals(uri)) {
		// // 主页的 icon
		// // TODO
		// return;
		// }

		// 进入过滤器
		try {
			// 全局过滤器
			HttpFilter httpFilter = config.matchingFilter(uri);
			if (httpFilter != null) {

			}

			// 自定义过滤器

		} catch (Exception e) {
			// TODO: handle exception
		}

		// 查找该 url 对应的 servlet
		// 若有，进入对应 servlet
		// 若无，进入读取文件处理
		dealReadyFile(ctx, request);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LOGGER.error(ctx.channel() + " exceptionCaught", cause);
		if (ctx.channel().isActive()) {
			SendError.sendServerError(ctx);
		}
	}

	private void dealReadyFile(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
		// 仅处理 get 请求
		if (request.method() != HttpMethod.GET) {
			SendError.sendMethodError(ctx, "please use get method to request file");
			return;
		}
		// 判断 server 设置的 root dir 是否可用的
		if (config.rootDirAvailable()) {
			SendError.sendNotFound(ctx, "root dir not avaliable");
			return;
		}

		String uri = request.uri();
		File file = sanitizeUri(uri);
		if (file == null) {
			SendError.sendForbidden(ctx, "the request path is disabled");
			return;
		}
		if (file.isHidden() || !file.exists()) {
			SendError.sendNotFound(ctx, "request file not exists");
			return;
		}
		// 若已开启目录列表查看，且该路径为文件夹
		if (config.openDirList() && file.isDirectory()) {
			if (uri.endsWith("/")) {
				// 响应目录列表
				responseDirListing(ctx, file);
			} else {
				// 重定向进入到 sendListing 方法处理
				String newUri = uri + '/';
				FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
				response.headers().set(LOCATION, newUri);
				ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
			}
			return;
		}
		// 禁止非标准文件查看。若一个 file 不是文件夹，哪它也不会一定是标准文件
		if (!file.isFile()) {
			SendError.sendForbidden(ctx, "this request path not normal file");
			return;
		}

		// 下载操作
		downloadFile(ctx, request, file);
	}

	private void downloadFile(ChannelHandlerContext ctx, FullHttpRequest request, File file) throws ParseException, IOException {
		// 检测是否修改了文件
		if (checkModifySince(ctx, request, file)) {
			return;
		}

		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException e) {
			SendError.sendNotFound(ctx, "reqeust file no exists(instantiate randomAccessFile fail)");
			return;
		}
		long fileLength = raf.length();

		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		setRequestHanderLine(ctx, request, file, fileLength, response);
		// 写入请求头和请求行
		ctx.write(response);

		// 写入内容,HttpChunkedInput 内部包含结束标记 LastHttpContent 。但是不知道 write 没有
		HttpChunkedInput chunkedInput = new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192));
		ChannelProgressivePromise promise = ctx.newProgressivePromise();
		ctx.writeAndFlush(chunkedInput, promise).addListener(new ChannelProgressiveFutureListener() {
			@Override
			public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
				if (total < 0) { // total unknown
					LOGGER.info(future.channel() + " download transfer progress：" + progress);
				} else {
					LOGGER.info(future.channel() + " download transfer progress：" + progress + " / " + total);
				}
			}
			@Override
			public void operationComplete(ChannelProgressiveFuture future) {
				LOGGER.info(future.channel() + " download transfer complete.");
			}
		});

		// 若是长连接
		ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
		if (!HttpUtil.isKeepAlive(request)) {
			// 当请求响应完毕后关闭连接
			lastContentFuture.addListener(ChannelFutureListener.CLOSE);
		}
	}

	private void setRequestHanderLine(ChannelHandlerContext ctx, FullHttpRequest request, File file, long fileLength, HttpResponse response) {
		Calendar time = Calendar.getInstance();
		String dateString = Date_Format.format(time.getTime());
		time.add(Calendar.SECOND, Cache_Seconds);// 当前时间加缓存时间
		String expiresString = Date_Format.format(time.getTime());
		response.headers().set(CONTENT_LENGTH, fileLength);
		response.headers().set(CONTENT_TYPE, new MimetypesFileTypeMap().getContentType(file.getPath()));
		response.headers().set(DATE, dateString);// 请求时间头
		response.headers().set(EXPIRES, expiresString);// 有效时间头。即缓存
		response.headers().set(CACHE_CONTROL, "private, max-age=" + Cache_Seconds);
		response.headers().set(LAST_MODIFIED, Date_Format.format(new Date(file.lastModified())));
		if (HttpUtil.isKeepAlive(request)) {
			response.headers().set(CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		}
	}

	private boolean checkModifySince(ChannelHandlerContext ctx, FullHttpRequest request, File file) throws ParseException {
		// 缓存核实
		String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
		if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
			Date ifModifiedSinceDate = Date_Format.parse(ifModifiedSince);
			// 只对比到秒一级别
			long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
			long fileLastModifiedSeconds = file.lastModified() / 1000;
			if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
				// 当时间戳一致时，返回 304 文件未修改
				FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
				response.headers().set(DATE, Date_Format.format(new GregorianCalendar().getTime()));
				ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
				return true;
			}
		}
		return false;
	}

	private static void responseDirListing(ChannelHandlerContext ctx, File dir) {
		String dirPath = dir.getPath();
		StringBuilder buf = new StringBuilder();
		buf.append("<!DOCTYPE html>\r\n")//
				.append("<html><head><title>")//
				.append("Listing of: ").append(dirPath).append("</title></head><body>\r\n")//
				.append("<h3>Listing of: ")//
				.append(dirPath)//
				.append("</h3>\r\n")//
				.append("<ul>")//
				.append("<li><a href=\"../\">..</a></li>\r\n");
		for (File file : dir.listFiles()) {
			// 跳过隐藏的、不可读的文件
			if (file.isHidden() || !file.canRead()) {
				continue;
			}
			// 检测文件名是否合法
			String name = file.getName();
			if (!Allowed_File_Name.matcher(name).matches()) {
				continue;
			}
			buf.append("<li><a href=\"").append(name).append("\">").append(name).append("</a></li>\r\n");
		}
		buf.append("</ul></body></html>\r\n");

		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8));
		response.headers().set(CONTENT_TYPE, "text/html; charset=UTF-8");
		response.headers().set(CONTENT_LENGTH, response.content().readableBytes());
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

	private File sanitizeUri(String uri) {
		// 路径解码
		try {
			uri = URLDecoder.decode(uri, "UTF-8");
		} catch (Exception e) {
			throw new IllegalArgumentException("request file url error", e);
		}
		if (uri.isEmpty() || uri.charAt(0) != '/') {
			return null;
		}
		// 路径安全检查
		uri = uri.replace('/', File.separatorChar);
		if (uri.contains(File.separator + '.') //
				|| uri.contains('.' + File.separator) //
				|| uri.charAt(0) == '.' //
				|| uri.charAt(uri.length() - 1) == '.' //
				|| Insecure_Uri.matcher(uri).matches()) {
			return null;
		}
		// 转换为绝对路径
		return config.rootDirChildFile(uri);
	}

}

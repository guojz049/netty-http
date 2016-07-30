package org.abcs.netty.http.util;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_IMPLEMENTED;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.Arrays;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午6:17:44
 * @类说明:
 * @版本 xx
 */
public class SendError {

	/** 403 已接受请求，但拒绝执行 */
	public static void sendForbidden(ChannelHandlerContext ctx, Object... des) {
		sendError(ctx, FORBIDDEN, des);
	}
	/** 404 未找到资源 */
	public static void sendNotFound(ChannelHandlerContext ctx, Object... des) {
		sendError(ctx, NOT_FOUND, des);
	}
	/** 405 header 的 method 错误 */
	public static void sendMethodError(ChannelHandlerContext ctx, Object... des) {
		sendError(ctx, METHOD_NOT_ALLOWED, des);
	}
	/** 400 错误请求 */
	public static void sendBadRequest(ChannelHandlerContext ctx, Object... des) {
		sendError(ctx, BAD_REQUEST, des);
	}
	/** 500 内部服务器错误 */
	public static void sendServerError(ChannelHandlerContext ctx, Object... des) {
		sendError(ctx, INTERNAL_SERVER_ERROR, des);
	}
	/** 501 服务器未实行 */
	public static void sendNoImplemented(ChannelHandlerContext ctx, Object... des) {
		sendError(ctx, NOT_IMPLEMENTED, des);
	}

	private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status, Object... des) {
		String formartString = des.length > 0 ? Arrays.asList(des) + "\n\n\t" : "";
		FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, status, Unpooled.copiedBuffer(formartString + "Failure: " + status, CharsetUtil.UTF_8));
		response.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8");
		response.headers().set(CONTENT_LENGTH, response.content().readableBytes());

		// 以错误状态信息响应给客户端，需要关闭连接
		ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
	}

}

package org.abcs.netty.http.handler;

import java.io.RandomAccessFile;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;

/**
 * @作者 Mitkey
 * @时间 2016年7月25日 下午6:55:20
 * @类说明: FIXME 修正为 http 编解码的处理方式
 * @版本 xx
 */
public class FileHandler extends SimpleChannelInboundHandler<String> {

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		ctx.writeAndFlush("HELO: Type the path of the file to retrieve.\n");
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		System.err.println("userEventTriggered: " + evt.getClass());
		ctx.channel().writeAndFlush(evt.getClass().getSimpleName());
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		super.channelReadComplete(ctx);
		System.err.println("FileHandler.channelReadComplete()");
		ctx.channel().writeAndFlush("xxxxxxxxxxxxx");
	}

	@Override
	public void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
		System.err.println("FileHandler.channelRead0():" + msg);

		RandomAccessFile raf = null;
		long length = -1;
		try {
			raf = new RandomAccessFile(msg, "r");
			length = raf.length();
		} catch (Exception e) {
			ctx.writeAndFlush("ERR: " + e.getClass().getSimpleName() + ": " + e.getMessage() + '\n');
			return;
		} finally {
			if (length < 0 && raf != null) {
				raf.close();
			}
		}

		ctx.write("OK: " + raf.length() + '\n');
		if (ctx.pipeline().get(SslHandler.class) == null) {
			// SSL not enabled - can use zero-copy file transfer.
			ctx.write(new DefaultFileRegion(raf.getChannel(), 0, length));
		} else {
			// SSL enabled - cannot use zero-copy file transfer.
			ctx.write(new ChunkedFile(raf));
		}
		ctx.writeAndFlush("\n");
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();

		if (ctx.channel().isActive()) {
			ctx.writeAndFlush("ERR: " + cause.getClass().getSimpleName() + ": " + cause.getMessage() + '\n').addListener(ChannelFutureListener.CLOSE);
		}
	}
}
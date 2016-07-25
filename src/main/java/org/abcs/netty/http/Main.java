package org.abcs.netty.http;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @作者 Mitkey
 * @时间 2016年7月19日 下午3:03:15
 * @类说明:
 * @版本 xx
 */
public class Main {
	public static void main(String[] args) throws Exception {
		new Main().run();
	}

	public void run() throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup(); // (1)
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap b = new ServerBootstrap(); // (2)
			b.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class) // (3)
					.childHandler(new MyInitChannelHandler())// 初始化处理请求的 handler
					.option(ChannelOption.TCP_NODELAY, true)// tcp 不延迟
					.option(ChannelOption.SO_BACKLOG, 128) // 积压数据长度
					.childOption(ChannelOption.SO_KEEPALIVE, true); // 长连接
			b.bind(8080).sync().channel().closeFuture().sync();
		} finally {
			System.out.println("Main.run()");
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	private final class MyInitChannelHandler extends ChannelInitializer<SocketChannel> {
		@Override
		public void initChannel(SocketChannel ch) throws Exception {
			ch.pipeline().addLast(new TestHandler());
		}
	}

	private final class TestHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			ByteBuf in = (ByteBuf) msg;
			ctx.writeAndFlush(msg);

			try {
				while (in.isReadable()) { // (1)
					System.out.print((char) in.readByte());
					System.out.flush();
				}
			} finally {
				System.err.println("toString:" + ctx.channel().id().toString());
			}
		}
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			super.exceptionCaught(ctx, cause);
			ctx.close();
		}
	}

}

package org.abcs.netty.http;

import java.net.InetAddress;

import org.abcs.netty.http.handler.FileHandler;
import org.apache.log4j.Logger;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月25日 下午3:27:11
 * @类说明:
 * @版本 xx
 */
public class AbcsNettyHttpServer {
	private static final Logger LOGGER = Logger.getLogger(AbcsNettyHttpServer.class);
	private static final String PRX = "▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓";

	private AbcsNettyHttpServerSetting setting = new AbcsNettyHttpServerSetting();

	public AbcsNettyHttpServer(AbcsNettyHttpServerSetting setting) {
		super();
		this.setting = setting;
	}

	public void run() throws Exception {
		// 记录开始时间
		long start = System.currentTimeMillis();

		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap bootstrap = new ServerBootstrap();
			bootstrap.group(bossGroup, workerGroup)// 定义2个 EventLoop 组，boos 处理请求，worker 处理 io
					.channel(NioServerSocketChannel.class) // 用于创建 channel 实例
					.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10 * 1000)// 连接超时，单位毫秒
					.option(ChannelOption.TCP_NODELAY, true)// tcp 不延迟
					.option(ChannelOption.SO_BACKLOG, 128) // 积压数据长度
					.childOption(ChannelOption.SO_KEEPALIVE, false) // 长连接
					.childHandler(new MyChannelInitializer());// 创建 childHandler 来处理每一个新连接请求，通过 initChannel() 方法

			if (setting.isOpenConnectionLog()) {
				// 开启连接日志。【在 handler 中和 childHandler 中添加的作用是不一样的。handler 是针对连接，childHandler 是 io 操作】
				bootstrap.handler(new LoggingHandler(LogLevel.INFO));
			}

			// 绑定到指定端口，通过调用 sync 同步方法阻塞直到绑定完成，完成后获取 channel
			int port = setting.getPort();
			Channel channel = bootstrap.bind(port).sync().channel();

			// channel.localAddress(); // 本地为：0.0.0.0/0.0.0.0:port
			// 获取本地 ip
			String hostName = InetAddress.getLocalHost().getHostAddress();
			long speedTime = System.currentTimeMillis() - start;
			LOGGER.info(String.format("%s 【%s:%s】 Server startup in %s ms %s", PRX, hostName, port, speedTime, PRX));

			// 应用程序会一直等待，直到 channel 关闭
			channel.closeFuture().sync();
		} finally {
			// 关闭 EventLoopGroup ，释放掉所有资源包括创建的线程
			workerGroup.shutdownGracefully();
			bossGroup.shutdownGracefully();
		}
	}

	private final class MyChannelInitializer extends ChannelInitializer<SocketChannel> {
		// 一旦有新 channel 注册了，将会调用该方法。并在该方法调用后，从 ChannelPipeline 中删除该 channel
		@Override
		public void initChannel(SocketChannel ch) throws Exception {
			ChannelPipeline pipeline = ch.pipeline();

			if (setting.isOpenIoLog()) {
				// 开启每个连接的 I/O 日志
				pipeline.addLast(new LoggingHandler(LogLevel.INFO));
			}

			// 首先添加文本分割解码器，分隔符为 \n 和 \r
			pipeline.addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()));
			// 添加大内容解码器：1024*8 = 8kb
			pipeline.addLast(new LineBasedFrameDecoder(8192));
			// 添加 string 编码器
			pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
			// 添加 string 解码器
			pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
			// 添加支持以异步方式写操作大块数据的 handler.常配合 ChunkedInput 使用。具体查看 class 说明
			pipeline.addLast(new ChunkedWriteHandler());

			// ===== my business logic handler ==== start

			// 添加文件读取的 handler
			pipeline.addLast(new FileHandler());

			// ===== my business logic handler ==== end
		}
	}
}

package org.abcs.netty;

import org.abcs.netty.http.AbcsNettyHttpServer;
import org.abcs.netty.http.AbcsNettyHttpServerSetting;
import org.apache.log4j.PropertyConfigurator;

/**
 * @作者 Mitkey
 * @时间 2016年7月19日 下午3:03:15
 * @类说明:
 * @版本 xx
 */
public class Main {

	public static void main(String[] args) throws Exception {
		// 若 main 方法启动时，传入了参数。默认为配置 log4j 的 config 文件
		if (args.length > 0) {
			String configFilename = args[0];
			PropertyConfigurator.configureAndWatch(configFilename);
		}

		AbcsNettyHttpServerSetting setting = new AbcsNettyHttpServerSetting();
		setting.setOpenConnectionLog(true).setOpenCors(true).setOpenIoLog(false);
		new AbcsNettyHttpServer(setting).run();
	}

}

package org.abcs.netty.http;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午2:28:32
 * @类说明:
 * @版本 xx
 */
public class AbcsNettyHttpServerSetting {
	/** 服务端口，默认为 8080 */
	private int port = 8080;
	/** 开启连接日志，默认关闭 */
	private boolean openConnectionLog = false;
	/** 开启 IO 日志，默认关闭 */
	private boolean openIoLog = false;

	// ================================ setting and getting ================================
	public int getPort() {
		return port;
	}
	public AbcsNettyHttpServerSetting setPort(int port) {
		this.port = port;
		return this;
	}
	public boolean isOpenConnectionLog() {
		return openConnectionLog;
	}
	public AbcsNettyHttpServerSetting setOpenConnectionLog(boolean openConnectionLog) {
		this.openConnectionLog = openConnectionLog;
		return this;
	}
	public boolean isOpenIoLog() {
		return openIoLog;
	}
	public AbcsNettyHttpServerSetting setOpenIoLog(boolean openIoLog) {
		this.openIoLog = openIoLog;
		return this;
	}

}

package org.abcs.netty.http;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.abcs.netty.http.servlet.HttpFilter;
import org.abcs.netty.http.servlet.HttpServlet;

import io.netty.util.internal.SystemPropertyUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午2:28:32
 * @类说明:
 * @版本 xx
 */
public class AbcsNettyHttpServerSetting {
	public static final String MappingAll = "/*";

	/** 服务端口，默认为 8080 */
	private int port = 8080;
	/** 资源的根目录，默认与工作目录同级 */
	private File root = new File(SystemPropertyUtil.get("user.dir"));
	/** 开启资源目录列表查看，默认关闭 */
	private boolean openDirList = false;
	/** 开启连接日志，默认关闭 */
	private boolean openConnectionLog = false;
	/** 开启 IO 日志，默认关闭 */
	private boolean openIoLog = false;
	/** 开启跨域，默认关闭 */
	private boolean openCors = false;
	/** servlet 集 */
	private Map<String, HttpServlet> servletMaps = new ConcurrentHashMap<String, HttpServlet>();
	/** filter 集 */
	private Map<String, HttpFilter> filterMaps = new ConcurrentHashMap<String, HttpFilter>();

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
	public boolean isOpenCors() {
		return openCors;
	}
	public AbcsNettyHttpServerSetting setOpenCors(boolean openCors) {
		this.openCors = openCors;
		return this;
	}

	public AbcsNettyHttpServerSetting openDir(boolean openDirList) {
		this.openDirList = openDirList;
		return this;
	}
	public boolean openDirList() {
		return openDirList;
	}
	public AbcsNettyHttpServerSetting rootDir(String root) {
		if (root == null || root.trim().length() == 0) {
			throw new IllegalArgumentException("root path can not null");
		}
		rootDir(new File(root));
		return this;
	}
	public AbcsNettyHttpServerSetting rootDir(File root) {
		if (root == null) {
			throw new IllegalArgumentException("root dir can not null");
		}
		if (!root.exists()) {
			// 不存在创建目录
			root.mkdirs();
		} else if (!root.isDirectory()) {
			// 不是目录
			throw new IllegalArgumentException("root dir not directory");
		} else if (!root.canRead()) {
			// 不可读
			throw new IllegalArgumentException("root dir can not readable");
		}
		this.root = root;
		return this;
	}
	public File rootDirChildFile(String path) {
		if (path == null || path.trim().length() == 0) {
			throw new IllegalArgumentException("request file path can not null");
		}
		return new File(root, path);
	}
	public boolean rootDirAvailable() {
		return root == null;
	}

	public AbcsNettyHttpServerSetting mappingServlet(String path, Class<? extends HttpServlet> servletClass) {
		path = checkPathAndClass(path, servletClass);

		synchronized (servletClass) {
			if (servletMaps.containsKey(path)) {
				throw new IllegalArgumentException("【" + path + "】" + " alrady exists mapping Servlet");
			}
			try {
				servletMaps.put(path, servletClass.newInstance());
			} catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalArgumentException("instance " + servletClass + " fail");
			}
		}
		return this;
	}
	public HttpServlet matchingServlet(String path) {
		return servletMaps.get(path);
	}

	public AbcsNettyHttpServerSetting mappingFilter(String path, Class<? extends HttpFilter> filterClass) {
		path = checkPathAndClass(path, filterClass);

		synchronized (filterClass) {
			if (filterMaps.containsKey(path)) {
				throw new IllegalArgumentException("【" + path + "】" + " alrady exists mapping Filter");
			}

			try {
				filterMaps.put(path, filterClass.newInstance());
			} catch (InstantiationException | IllegalAccessException e) {
				throw new IllegalArgumentException("instance " + filterClass + " fail");
			}
		}
		return this;
	}
	public HttpFilter matchingFilter(String path) {
		return filterMaps.get(path);
	}

	private String checkPathAndClass(String path, Class<?> filterClass) {
		if (path == null || path.trim().length() == 0) {
			throw new IllegalArgumentException("mapping path can not null");
		}
		if (filterClass == null) {
			throw new IllegalArgumentException("class can not null");
		}
		// 所有路径必须以 "/" 开头，如果没有则补全之
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		return path;
	}
}

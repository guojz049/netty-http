package org.abcs.netty.http.servlet.def;

import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;

import java.io.File;
import java.net.URLDecoder;
import java.text.ParseException;
import java.util.Date;
import java.util.regex.Pattern;

import org.abcs.netty.http.ABCSServerConfig;
import org.abcs.netty.http.servlet.HttpServlet;
import org.abcs.netty.http.servlet.HttpServletRequest;
import org.abcs.netty.http.servlet.HttpServletResponse;

/**
 * @作者 Mitkey
 * @时间 2016年7月30日 上午11:29:38
 * @类说明:
 * @版本 xx
 */
public class FileServlet extends HttpServlet {

	private static final Pattern Insecure_Uri = Pattern.compile(".*[<>&\"].*");

	private ABCSServerConfig config;

	public FileServlet(ABCSServerConfig config) {
		super();
		this.config = config;
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws Exception {
		response.sendMethodError("please use get method to request file");
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
		// 判断 server 设置的 root dir 是否可用的
		if (config.rootDirAvailable()) {
			response.sendForbidden("root dir not avaliable");
			return;
		}

		String uriString = request.uri();
		File file = sanitizeUri(uriString);
		// uri 不安全的
		if (file == null) {
			response.sendForbidden("the request path is disabled");
			return;
		}
		// 该 uri 对应的文件不存在或隐藏的
		if (file.isHidden() || !file.exists()) {
			response.sendNotFound("request file not exists or hidden");
			return;
		}

		// 禁止非标准文件查看。若一个 file 不是文件夹，哪它也不会一定是标准文件
		// 盘符的根目录也不是文件如 C:\\
		if (!file.isFile()) {
			response.sendForbidden("this request path not normal file");
			return;
		}

		// 检测是否修改了文件
		if (!checkModifySince(response, file)) {
			// 当时间戳一致时，返回 304 文件未修改
			response.sendNotModified();
			return;
		}

		response.content(file);
	}

	private boolean checkModifySince(HttpServletResponse response, File file) throws ParseException {
		// 缓存核实
		String ifModifiedSince = response.servletRequest().headers().get(IF_MODIFIED_SINCE);
		if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
			Date ifModifiedSinceDate = HttpServletResponse.Date_Format.parse(ifModifiedSince);
			// 只对比到秒一级别
			long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
			long fileLastModifiedSeconds = file.lastModified() / 1000;
			if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
				return false;
			}
		}
		return true;
	}

	// uri 安全监测
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

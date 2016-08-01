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
	private static final Pattern Allowed_File_Name = Pattern.compile("[A-Za-z0-9][-_A-Za-z0-9\\.]*");

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

		// TODO 需要替换掉 uri 中的 ?op=file
		String uriString = request.uri().replace("?op=file", "");
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

		// 若已开启目录列表查看，且该路径为文件夹
		if (config.dirList() && file.isDirectory()) {
			if (uriString.endsWith("/")) {
				// 响应目录列表
				responseDirListing(response, file);
			} else {
				// 重定向进入到 sendListing 方法处理
				String newUri = uriString + '/';
				response.sendRedirect(newUri);
			}
			return;
		}

		// 禁止非标准文件查看。若一个 file 不是文件夹，哪它也不会一定是标准文件
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
		response.sendCustom();
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

	private void responseDirListing(HttpServletResponse response, File dir) throws Exception {
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

		response.keepAlive(false);
		response.content(buf.toString());
		response.contentTypeTextHtml();
		response.sendCustom();
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

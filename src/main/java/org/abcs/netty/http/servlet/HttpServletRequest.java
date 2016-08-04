package org.abcs.netty.http.servlet;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.COOKIE;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED;
import static io.netty.handler.codec.http.HttpHeaderValues.TEXT_PLAIN;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.abcs.netty.http.servlet.HttpServlet.Method;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;

/**
 * @作者 Mitkey
 * @时间 2016年7月26日 下午7:04:38
 * @类说明:
 * @版本 xx
 */
public class HttpServletRequest {
	private static final boolean debug = true;
	// 若数据超过 16kb，则存放到磁盘
	private static final HttpDataFactory httpDataFactory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);

	private Map<String, Cookie> cookiesMap;
	private Map<String, String> headersMap;
	private Map<String, Object> paramsMap;
	private String content = null;
	private boolean flagInitCookies = false;
	private boolean flagInitHeaders = false;
	private boolean flagInitParams = false;

	private ChannelHandlerContext ctx;
	private FullHttpRequest request;

	private HttpServletRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
		this.ctx = ctx;
		this.request = request;
		this.cookiesMap = new HashMap<String, Cookie>();
		this.headersMap = new HashMap<String, String>();
		this.paramsMap = new HashMap<String, Object>();
	}
	/** netty 原生的 FullHttpRequest */
	public FullHttpRequest nettyRequest() {
		return request;
	}
	/** netty 所有 channel 的上下文对象 */
	public ChannelHandlerContext handlerContext() {
		return ctx;
	}
	/** http 请求方法的类型 */
	public Method method() {
		HttpMethod method = request.method();
		if (method == GET) {
			return Method.Get;
		} else if (method == POST) {
			return Method.Post;
		} else {
			return Method.Other;
		}
	}
	/** uri（带参数的路径）。如 http://127.0.0.1:8080/?name=mitkey 中的 uri 为 /?name=mitkey */
	public String uri() {
		return request.uri();
	}
	/** path（不带参数的路径）。如 http://127.0.0.1:8080/?name=mitkey 中的 path 为 / */
	public String path() {
		URI tempUri = null;
		try {
			tempUri = new URI(request.uri());
		} catch (Exception e) {
			throw new IllegalArgumentException("can not conver URI", e);
		}
		return tempUri == null ? null : tempUri.getPath();
	}
	/** ip 地址 */
	public String ip() {
		String ip = request.headers().get("X-Forwarded-For");
		if (ip == null || ip.trim().length() == 0) {
			final InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
			ip = insocket.getAddress().getHostAddress();
		} else {
			if (ip != null && ip.indexOf(",") > 0) {
				final String[] ips = ip.trim().split(",");
				for (String subIp : ips) {
					if (subIp == null || subIp.trim().length() == 0 || "unknown".equalsIgnoreCase(subIp)) {
					} else {
						ip = subIp;
						break;
					}
				}
			}
		}
		return ip;
	}
	/** http 协议的版本：1.0 或 1.1 */
	public String protocol() {
		return request.protocolVersion().text();
	}
	/** 请求头信息 */
	public Map<String, String> headers() {
		if (flagInitHeaders) {
			return headersMap;
		}
		// 解析请求头
		HttpHeaders headers = request.headers();
		if (headers != null && !headers.isEmpty()) {
			Iterator<Entry<String, String>> iteratorAsString = headers.iteratorAsString();
			while (iteratorAsString.hasNext()) {
				Entry<String, String> entry = iteratorAsString.next();
				headersMap.put(entry.getKey(), entry.getValue());
			}
		}
		flagInitHeaders = true;
		return headersMap;
	}
	/** 客户端请求头中携带的 cookies */
	public Map<String, Cookie> cookies() {
		if (flagInitCookies) {
			return cookiesMap;
		}
		// 编码 cookie
		String cookieString = request.headers().get(COOKIE);
		if (cookieString != null && cookieString.trim().length() != 0) {
			Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(cookieString);
			if (cookies != null && !cookies.isEmpty()) {
				Iterator<Cookie> iterator = cookies.iterator();
				while (iterator.hasNext()) {
					Cookie cookie = iterator.next();
					if (cookie != null) {
						cookiesMap.put(cookie.name(), cookie);
					}
				}
			}
		}
		flagInitCookies = true;
		return cookiesMap;
	}
	/** 是否为长连接。HTTP/1.1 协议默认为 true，而 HTTP/1.0 需要手动设置 */
	public boolean isKeepAlive() {
		return HttpUtil.isKeepAlive(request);
	}
	/** 获取该请求使用的编码 */
	public Charset charset() {
		return HttpUtil.getCharset(request);
	}
	/** 请求参数：获取 get 参数和 post 参数都是该入口 */
	public Map<String, Object> params() {
		if (flagInitParams) {
			return paramsMap;
		}
		// 默认不管是 get 还是 post 都解析 url 中的参数
		parseGetParams(paramsMap);
		// 解析 post 特有数据
		if (method() == Method.Post) {
			parsePostParams(paramsMap);
		}
		// 解析特殊包
		ByteBuf byteBuf = request.content();
		if (byteBuf != null) {
			content = byteBuf.toString(CharsetUtil.UTF_8);
		}
		flagInitParams = true;
		return paramsMap;
	}
	/** 获取 content */
	public String content() {
		return content;
	}
	/** 是否为表单提交 */
	public boolean contentTypeIsXWwwFormUrlencoded() {
		return request.headers().contains(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED, true);
	}
	/** 是否为 json 提交 */
	public boolean contentTypeIsJson() {
		return request.headers().contains(CONTENT_TYPE, APPLICATION_JSON, true);
	}
	/** 是否为纯文本提交 */
	public boolean contentTypeIsTextPlain() {
		return request.headers().contains(CONTENT_TYPE, TEXT_PLAIN, true);
	}
	/** 解析 post 请求特有的参数 */
	private void parsePostParams(Map<String, Object> targetParamsMap) {
		HttpPostRequestDecoder decoder = null;
		try {
			decoder = new HttpPostRequestDecoder(httpDataFactory, request);

			// 初始化新数据块。当前无需再手动初始化，因为在初始化 decoder 时
			// 只要传入的 HttpRequest 是 HttpContent 实现类。 当前为 FullHttpRequest
			// decoder.offer(request);

			// 恰当的最小内存使用工厂方式
			Iterator<InterfaceHttpData> iterator = decoder.getBodyHttpDatas().iterator();
			while (iterator.hasNext()) {
				// 无需 httpData.release()。因为该步骤在 HttpPostRequestDecoder 的 destroy() 中已实现
				InterfaceHttpData httpData = iterator.next();
				if (httpData == null)
					continue;

				switch (httpData.getHttpDataType()) {
					case Attribute : // 普通参数
						converAttribute(httpData, targetParamsMap);
						break;
					case FileUpload :// 上传的文件
						converFileUpload(httpData, targetParamsMap);
						break;
					case InternalAttribute :// 未处理的类型
						if (debug)
							System.err.println("params " + httpData.getName() + " data type no deal with");
						break;
					default :
						break;
				}
			}
		} finally {
			if (decoder != null) {// 释放资源
				decoder.destroy();
				decoder = null;
			}
		}
	}
	/** 解析 get 请求中 uri 中携带的参数 */
	private void parseGetParams(Map<String, Object> targetParamsMap) {
		QueryStringDecoder decoderQuery = new QueryStringDecoder(uri());
		for (Entry<String, List<String>> attr : decoderQuery.parameters().entrySet()) {
			List<String> value = attr.getValue();
			if (value != null) {// 只取第一个元素
				targetParamsMap.put(attr.getKey(), value.get(0));
			} else if (debug) {
				System.err.println("get request【" + uri() + "】.params " + attr.getKey() + " value is null");
			}
		}
	}
	/** 转换文件上传的参数与内容 */
	private void converFileUpload(InterfaceHttpData httpData, Map<String, Object> targetParamsMap) {
		// 判断该 key 对应数据类型时，使用 obj instanceof File 或 obj.getClass().isArray()
		FileUpload fileUpload = (FileUpload) httpData;
		// 所有数据已经存储完毕
		if (fileUpload.isCompleted()) {
			if (fileUpload.isInMemory()) {
				// 在内存中
				try {
					targetParamsMap.put(fileUpload.getName(), fileUpload.get());
				} catch (IOException e) {
					if (debug) {
						System.err.println("params" + fileUpload.getName() + " get byte[] error");
					}
				}
			} else {
				// 在磁盘中。若文件的大小超过 Integer 的最大边界值会抛出异常。一般应用中，上传的文件不会这么大
				try {
					targetParamsMap.put(fileUpload.getName(), fileUpload.getFile());
				} catch (IOException e) {
					if (debug) {
						System.err.println("params " + fileUpload.getName() + " get file error, it not exists disk. may be in memory:" + e.getMessage());
					}
				}
			}
		}
	}
	/** 转换 post 请求中普通参数与内容 */
	private void converAttribute(InterfaceHttpData httpData, Map<String, Object> targetParamsMap) {
		// 若 Attribute 的实例是 DiskAttribute，则调用 getVaule 可能抛出 io 异常。一般应用下不会
		Attribute attribute = (Attribute) httpData;
		try {
			targetParamsMap.put(attribute.getName(), attribute.getValue());
		} catch (IOException e) {
			if (debug)
				System.err.println("params " + attribute.getName() + " value ready Error:" + e.getMessage());
		}
	}

	public JSONObject toJson() {
		// 若有新的描述信息，需要加入 TODO
		JSONObject result = new JSONObject();
		result.put("channelId", ctx.channel().id().toString());
		result.put("ip", ip());
		result.put("uri", uri());
		result.put("path", path());
		result.put("charset", charset());
		result.put("protocol", protocol());
		result.put("method", method());
		result.put("isKeepAlive", isKeepAlive());
		result.put("headers", JSON.toJSON(headers()));
		result.put("params", JSON.toJSON(params()));
		result.put("content", content == null ? "【no send】" : content);
		JSONArray array = new JSONArray();
		for (Entry<String, Cookie> entry : cookies().entrySet()) {
			array.add(entry.getValue().toString());
		}
		result.put("cookies", array);
		return result;
	}

	public static HttpServletRequest builder(ChannelHandlerContext ctx, FullHttpRequest request) {
		return new HttpServletRequest(ctx, request);
	}

}

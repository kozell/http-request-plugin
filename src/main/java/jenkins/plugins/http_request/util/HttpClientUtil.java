package jenkins.plugins.http_request.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;

import jenkins.plugins.http_request.HttpMode;

/**
 * @author Janario Oliveira
 */
public class HttpClientUtil {

	private HttpClientUtil() {
		// Don't
	}

	public static HttpRequestBase createRequestBase(RequestAction requestAction)
			throws IOException {
        HttpRequestBase httpRequestBase = doCreateRequestBase(requestAction);
        for (HttpRequestNameValuePair header : requestAction.getHeaders()) {
            httpRequestBase.addHeader(header.getName(), header.getValue());
        }

        return httpRequestBase;
    }

	private static HttpRequestBase doCreateRequestBase(RequestAction requestAction)
			throws IOException {
		final String uriWithParams = getUrlWithParams(requestAction);

		//without entity
		if (requestAction.getMode() == HttpMode.HEAD) {
			return new HttpHead(uriWithParams);
		} else if (requestAction.getMode() == HttpMode.GET && (requestAction.getRequestBody() == null || requestAction.getRequestBody().isEmpty())) {
			return new HttpGet(uriWithParams);
		} else if (requestAction.getMode() == HttpMode.TRACE) {
			return new HttpTrace(uriWithParams);
		}

		//with entity
		HttpEntityEnclosingRequestBase http;
		if (requestAction.getMode() == HttpMode.GET) {
			http = new HttpBodyGet(uriWithParams);
		} else if (requestAction.getMode() == HttpMode.DELETE) {
			http = new HttpBodyDelete(uriWithParams);
		} else if (requestAction.getMode() == HttpMode.PUT) {
			http = new HttpPut(uriWithParams);
		} else if (requestAction.getMode() == HttpMode.PATCH) {
			http = new HttpPatch(uriWithParams);
		} else if (requestAction.getMode() == HttpMode.OPTIONS) {
			return new HttpOptions(uriWithParams);
		} else { //default post
			http = new HttpPost(uriWithParams);
		}

		http.setEntity(makeEntity(requestAction));
		return http;
	}

	private static HttpEntity makeEntity(RequestAction requestAction) throws
			UnsupportedEncodingException {
		if (requestAction.getRequestBody() != null && !requestAction.getRequestBody().isEmpty()) {
			ContentType contentType = null;
			for (HttpRequestNameValuePair header : requestAction.getHeaders()) {
				if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(header.getName())) {
					contentType = ContentType.parse(header.getValue());
					break;
				}
			}

			return new StringEntity(requestAction.getRequestBody(), contentType);
		}
		return toUrlEncoded(requestAction.getParameters());
	}

	private static String getUrlWithParams(RequestAction requestAction) throws IOException {
		return appendParamsToUrl(requestAction.getUrl().toString(), requestAction.getParameters());
	}

	public static String appendParamsToUrl(String url, List<HttpRequestNameValuePair> params) throws IOException {
		try {
			return new URIBuilder(url)
					.addParameters(params.stream().map(p -> ((NameValuePair)p)).collect(Collectors.toList()))
					.build()
					.toString();
		} catch (URISyntaxException e) {
			throw new IOException("URI Syntax Exception encapsulated. " + e.getMessage(), e);
		}
	}

	public static String paramsToString(List<HttpRequestNameValuePair> params) throws IOException {
		try (InputStream is = toUrlEncoded(params).getContent()) {
			return IOUtils.toString(is, StandardCharsets.UTF_8);
		}
	}

	private static UrlEncodedFormEntity toUrlEncoded(List<HttpRequestNameValuePair> params)
			throws UnsupportedEncodingException {
		return new UrlEncodedFormEntity(params);
	}
	/*
	 * public static String urlParamEncoder(List<HttpRequestNameValuePair> params) {
	 * URLEncoder.encode(null, StandardCharsets.UTF_8); return null; }
	 */

    public static HttpResponse execute(HttpClient client, HttpContext context, HttpRequestBase method,
								PrintStream logger) throws IOException {
        logger.println("Sending request to url: " + method.getURI());

        final HttpResponse httpResponse = client.execute(method, context);
        logger.println("Response Code: " + httpResponse.getStatusLine());

        return httpResponse;
    }
}

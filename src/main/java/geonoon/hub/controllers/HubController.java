package geonoon.hub.controllers;

import geonoon.hub.processors.FeedDistributor;
import geonoon.hub.processors.FeedRetriever;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javassist.tools.framedump;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HubController {

	private final static Logger LOG = LoggerFactory
			.getLogger(HubController.class);

	@Resource(name = "retrieverQueue")
	private BlockingQueue<String> retrieverQueue;

	@Resource(name = "subfeedMap")
	private ConcurrentHashMap<String, Set<String>> subfeedMap;

	@RequestMapping(value = "/", method = RequestMethod.POST)
	public ModelAndView post(HttpServletRequest request,
			HttpServletResponse response) throws IOException {
		String mode = request.getParameter("hub.mode");
		System.out.println(mode);
		if ("publish".equals(mode)) {
			LOG.info("Received publish request");
			String[] urls = request.getParameterValues("hub.url");
			// hub.url equal to the feed URL of the feed that has been updated.
			// This field may be repeated to indicate multiple feeds that have
			// been updated
			if (urls != null) {
				for (String url : urls) {
					publish(url, response);
				}
			} else {
				response.sendError(500, "Missing hub.url parameter");
			}
		} else if ("subscribe".equals(mode)) {
			// implemented by self;
			LOG.info("Received subscriber request");
			// respose
			response.setStatus(202);

			// send get request to sub
			String url = request.getParameter("hub.callback");
			String topicUrl = request.getParameter("hub.topic");
			HttpClient httpclient = new DefaultHttpClient();
			// 创建参数信息
			List<NameValuePair> params = new LinkedList<NameValuePair>();
			params.add(new BasicNameValuePair("hub.mode", request
					.getParameter("hub.mode")));
			params.add(new BasicNameValuePair("hub.topic", request
					.getParameter("hub.topic")));
			params.add(new BasicNameValuePair("hub.challenge", "yes"));
			// 对参数编码
			String param = URLEncodedUtils.format(params, "UTF-8");
			// 将URL与参数拼接
			HttpGet httpget = new HttpGet(url + "?" + param);

			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			String responseBody = httpclient.execute(httpget, responseHandler);
			if (responseBody.equals("yes")) {
				// in here ,will save subscriber url --wkh
				System.out.println("verfiy challenge sucess");
				if (!subfeedMap.containsKey(topicUrl)) {
					Set<String> set = new HashSet<String>();
					set.add(url);
					subfeedMap.put(topicUrl, set);
				} else {
					subfeedMap.get(topicUrl).add(url);
					// Set<String> set=subfeedMap.get(topicUrl);
					// set.add(url);
					// subfeedMap.put(topicUrl, set);
				}

			}
			httpclient.getConnectionManager().shutdown();

		}
		else if ("unsubscribe".equals(mode)) {
			LOG.info("Received unsubscriber request");
			// respose
			response.setStatus(202);

			// send get request to sub
			String url = request.getParameter("hub.callback");
			String topicUrl = request.getParameter("hub.topic");
			HttpClient httpclient = new DefaultHttpClient();
			// 创建参数信息
			List<NameValuePair> params = new LinkedList<NameValuePair>();
			params.add(new BasicNameValuePair("hub.mode", request
					.getParameter("hub.mode")));
			params.add(new BasicNameValuePair("hub.topic", request
					.getParameter("hub.topic")));
			params.add(new BasicNameValuePair("hub.challenge", "yes"));
			// 对参数编码
			String param = URLEncodedUtils.format(params, "UTF-8");
			// 将URL与参数拼接
			HttpGet httpget = new HttpGet(url + "?" + param);

			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			String responseBody = httpclient.execute(httpget, responseHandler);
			if (responseBody.equals("yes")) {
				// in here ,will delete unsubscriber url --wkh
				System.out.println("verfiy challenge sucess");
				if (subfeedMap.containsKey(topicUrl)) {
					subfeedMap.get(topicUrl).remove(url);
				}
			}
			httpclient.getConnectionManager().shutdown();
			
		}
		return null;
	}

	private void publish(String url, HttpServletResponse response)
			throws IOException {
		try {
			URI uri = new URI(url);
			// String ss = uri.getScheme();
			if ("http".equals(uri.getScheme())
					|| "https".equals(uri.getScheme())) {
				// TODO handle https

				boolean wasAdded = false;
				try {
					wasAdded = retrieverQueue.offer(url, 2000,
							TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					wasAdded = false;
				}

				if (wasAdded) {
					response.sendError(204);
				} else {
					response.sendError(500,
							"Internal error, failed to publish update");
				}
			} else {
				response.sendError(500, "Only HTTP URLs allowed: " + url);
			}
		} catch (URISyntaxException e1) {
			response.sendError(500, "Invalid hub.url value: " + url);
		}
	}
}

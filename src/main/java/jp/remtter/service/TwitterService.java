package jp.remtter.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.arnx.jsonic.JSON;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.http.HttpParameters;

import jp.remtter.util.LogUtil;

public class TwitterService {
	public String userTimelineUrl = "https://api.twitter.com/1.1/statuses/user_timeline.json";

	// getFollower
	public String followerUrl = "https://api.twitter.com/1.1/followers/ids.json";
	public String lookupUrl = "https://api.twitter.com/1.1/users/lookup.json";
	public String directMessageUrl = "https://api.twitter.com/1.1/direct_messages/new.json";

	private String userId;
	private String screenName;
	private String consumerKey;
	private String consumerSecret;
	private String accessToken;
	private String accessSecret;

	private LogUtil logger = new LogUtil(TwitterService.class);

	public void setToken(String screenName, String consumerKey,
			String consumerSecret, String accessToken, String accessSecret) {

		this.screenName = screenName;
		this.consumerKey = consumerKey;
		this.consumerSecret = consumerSecret;
		this.accessToken = accessToken;
		this.accessSecret = accessSecret;

		/*
		 * logger.sendLog("setToken"); logger.sendLog("screenName: " +
		 * screenName); logger.sendLog("consumerToken: " + consumerToken);
		 * logger.sendLog("consumerSecret: " + consumerSecret);
		 * logger.sendLog("accessToken: " + accessToken);
		 * logger.sendLog("accessSecret:" + accessSecret);
		 */
	}

	public List<Map<String, String>> getUserTimeline(int maxnum)
			throws APILimitException {
		List<Map<String, String>> dtoList = new ArrayList<Map<String, String>>();

		try {
			OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey,
					consumerSecret);

			consumer.setTokenWithSecret(accessToken, accessSecret);
			int length = 0;
			long id = 0;
			int tweetCnt = 0;
			while (tweetCnt < maxnum && length < 11000) {
				StringBuilder urlStr = new StringBuilder();
				urlStr.append(userTimelineUrl + "?count=200&screen_name="
						+ screenName);
				if (id > 0) {
					urlStr.append("&max_id=" + id);
				}

				// HTTPリクエスト
				URL url = new URL(urlStr.toString());
				HttpURLConnection con = (HttpURLConnection) url
						.openConnection();
				con.setRequestMethod("GET");

				// 著名
				consumer.sign(con);

				// con.connect();
				logger.debug("getUserTimeline response: " + con.getResponseCode() + " "
						+ con.getResponseMessage());

				BufferedReader reader = new BufferedReader(
						new InputStreamReader(con.getInputStream()));

				String json = "";
				String line;

				while ((line = reader.readLine()) != null) {
					json += line;
				}

				reader.close();
				con.disconnect();

				List<Map<String, String>> pojoList = JSON.decode(json,
						List.class);

				for (Map<String, String> pojo : pojoList) {
					long tweetId = Long.parseLong(pojo.get("id_str"));

					// 除外
					if (tweetId == id) {
						continue;
					} else if (pojo.get("text").indexOf("@") > -1) {
						continue;
					}

					if (tweetCnt > maxnum) {
						break;
					} else if (length > 11000) {
						break;
					}
					tweetCnt++;

					// logger.sendLog(pojo.get("text"));
					length += pojo.get("text").length();
					Map<String, String> twitterTimelineDto = new HashMap<String, String>();
					twitterTimelineDto.put("content", pojo.get("text"));
					dtoList.add(twitterTimelineDto);

					// update id

					if (id == 0) {
						id = tweetId;
					} else if (id > tweetId) {
						id = tweetId;
					}
				}
			}

		} catch (Exception e) {
			dtoList = null;
			if (getResponseCode(e.getMessage()) == 429) {
				throw new APILimitException(e.getMessage());
			} else {
				//e.printStackTrace();
				logger.warn(e.getMessage());
			}
		}

		return dtoList;
	}

	public List<String> getFollowerIds(String userId, int maxnum)
			throws APILimitException {
		List<String> followerIdsList = new ArrayList<String>();
		try {
			String nextCursor = "";
			int counter = 0;
			while (true) {
				OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey,
						consumerSecret);

				consumer.setTokenWithSecret(accessToken, accessSecret);

				int idsCount;
				if (maxnum < 5000) {
					idsCount = maxnum;
				} else {
					idsCount = 5000;
				}

				StringBuilder urlStr = new StringBuilder();
				urlStr.append(followerUrl + "?count=" + idsCount + "&user_id="
						+ userId);

				if (nextCursor.length() > 0) {
					urlStr.append("&cursor=" + nextCursor);
				}

				// HTTPリクエスト
				URL url = new URL(urlStr.toString());
				HttpURLConnection con = (HttpURLConnection) url
						.openConnection();
				con.setRequestMethod("GET");

				// 著名
				consumer.sign(con);

				// con.connect();
				logger.debug("getFollowerIds response:" + con.getResponseCode() + " "
						+ con.getResponseMessage());

				BufferedReader reader = new BufferedReader(
						new InputStreamReader(con.getInputStream()));

				String json = "";
				String line;

				while ((line = reader.readLine()) != null) {
					json += line;
				}

				reader.close();
				con.disconnect();

				FollowerIdsResponseJSON followerIdsResponse = JSON.decode(json,
						FollowerIdsResponseJSON.class);

				followerIdsList = followerIdsResponse.getUserIdList();

				counter += 5000;

				String tmpStr = followerIdsResponse.getNextCursorStr();
				if (nextCursor.equals(tmpStr) || tmpStr.equals("0")
						|| counter >= maxnum) {
					break;
				}
				nextCursor = tmpStr;

			}
		} catch (Exception e) {
			followerIdsList = null;
			
			if (getResponseCode(e.getMessage()) == 429) {
				throw new APILimitException(e.getMessage());
			} else {
				//e.printStackTrace();
				logger.warn(e.getMessage());
			}
		}
		// loolupUsers(followerIdsList);
		return followerIdsList;
	}

	public List<Map<String, String>> lookupUsers(List<String> idsList)
			throws APILimitException {
		List<Map<String, String>> userInfoList = new ArrayList<Map<String, String>>();

		try {
			int userCount = 0;
			while (true) {
				OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey,
						consumerSecret);

				consumer.setTokenWithSecret(accessToken, accessSecret);

				StringBuilder idsBuff = new StringBuilder();
				for (int i = 0; i < 100; i++) {
					if (userCount == idsList.size()) {
						break;
					}

					if (idsBuff.length() > 0) {
						idsBuff.append(",");
					}
					idsBuff.append(idsList.get(userCount));
					userCount++;
				}

				StringBuilder urlStr = new StringBuilder();
				urlStr.append(lookupUrl + "?user_id=" + idsBuff.toString());

				// HTTPリクエスト
				URL url = new URL(urlStr.toString());
				HttpURLConnection con = (HttpURLConnection) url
						.openConnection();
				con.setRequestMethod("GET");

				// 著名
				consumer.sign(con);

				// con.connect();
				logger.debug("lookupUsers response: " + con.getResponseCode() + " "
						+ con.getResponseMessage());

				BufferedReader reader = new BufferedReader(
						new InputStreamReader(con.getInputStream()));

				String json = "";
				String line;

				while ((line = reader.readLine()) != null) {
					json += line;
				}

				reader.close();
				con.disconnect();


				List<Map<String, String>> lookupResponseList = JSON.decode(
						json, List.class);

				for (String id : idsList) {
					for (Map<String, String> res : lookupResponseList) {
						String lookupId = String.valueOf(res.get("id"));
						if (id.equals(lookupId)) {
							Map<String, String> map = new HashMap<String, String>();
							map.put("id", lookupId);
							map.put("screen_name", res.get("screen_name"));

							userInfoList.add(map);
							logger.debug("id:" + map.get("id") + "  name: "
									+ map.get("screen_name"));
						}
					}
				}
				
				if (userCount >= idsList.size()) {
					break;
				}
			}
		} catch (Exception e) {
			userInfoList = null;
			if (getResponseCode(e.getMessage()) == 429) {
				throw new APILimitException(e.getMessage());
			} else {
				//e.printStackTrace();
				logger.warn(e.getMessage());
			}
		}

		return userInfoList;
	}

	public void sendDirectMessage(String toUserId, String message)
			throws APILimitException {
		if (message.length() > 140) {
			return;
		}

		HttpURLConnection con = null;

		try {
			OAuthConsumer consumer = new DefaultOAuthConsumer(consumerKey,
					consumerSecret);

			consumer.setTokenWithSecret(accessToken, accessSecret);

			String parameterString = "user_id=" + toUserId + "&text="
					+ URLEncoder.encode(message, "UTF-8");

			HttpParameters hp = new HttpParameters();
			hp.put("user_id", toUserId);
			hp.put("text",
					URLEncoder.encode(message, "UTF-8").replace("+", "%20"));

			URL url = new URL(directMessageUrl);

			con = (HttpURLConnection) url.openConnection();
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			consumer.setAdditionalParameters(hp);
			consumer.sign(con);

			PrintWriter printWriter = new PrintWriter(con.getOutputStream());
			printWriter.print(parameterString);
			printWriter.close();

			// con.connect();
			
			logger.debug("sendDirectMessage response: " + con.getResponseCode() + " "
					+ con.getResponseMessage());

			BufferedReader reader = new BufferedReader(new InputStreamReader(
					con.getInputStream()));

			String json = "";
			String line;

			while ((line = reader.readLine()) != null) {
				json += line;
			}

		} catch (Exception e) {
			if (getResponseCode(e.getMessage()) == 429) {
				throw new APILimitException(e.getMessage());
			} else {
				//e.printStackTrace();
				logger.warn(e.getMessage());
			}
		} finally {
			try {
				con.disconnect();
			} catch (Exception e) {

			}
		}
	}

	private int getResponseCode(String message) {
		int start = message.indexOf("HTTP response code: ");
		int end = message.indexOf(" for ");

		if (start == -1 || end == -1 || start > end) {
			return -1;
		}

		String codeStr = message.substring(
				start + "HTTP response code: ".length(), end);
		
		//logger.debug("IOException code:" + codeStr + "  original message: " + message);
		
		return Integer.parseInt(codeStr);
	}

	// inner class
	public static class FollowerIdsResponseJSON {
		private List<String> idsList;

		private String nextCursorStr;

		public void setIds(List<String> usersMap) {
			this.idsList = usersMap;
		}

		public void setNextCursorStr(String str) {
			this.nextCursorStr = str;
		}

		public List<String> getUserIdList() {
			return idsList;
		}

		public int size() {
			return idsList.size();
		}

		public String getNextCursorStr() {
			return nextCursorStr;
		}
	}

	// custom Exception
	public static class APILimitException extends Exception {
		private String message;

		public APILimitException(String message) {
			super(message);
			this.message = message;
		}
	}
}

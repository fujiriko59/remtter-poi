package jp.remtter.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jp.remtter.auth.OAuthTokenCreator;
import jp.remtter.service.TwitterService;
import jp.remtter.util.FileUtil;
import jp.remtter.util.LogUtil;

public class Remtter {
	public Map<String, String> remtterAcountMap = new HashMap<String, String>();
	public List<Map<String, String>> crawlerAcountList = new ArrayList<Map<String, String>>();

	public String followerDataDir = "";
	public String countFilePath = "";

	private TwitterService twitterService;

	private LogUtil logger = new LogUtil(Remtter.class);

	public static void main(String[] args) {
		Remtter obj = new Remtter();
		obj.init();
		obj.submain(args);
	}

	public void init() {
		logger.info("remtter-poi initialize");

		Properties prop = new Properties();

		InputStream in = null;
		try {
			in = new FileInputStream(new File("./conf/remtter.properties"));
			prop.load(in);

			// get remtter properties
			remtterAcountMap.put("remtterUserId",
					prop.getProperty("remtterUserId"));
			remtterAcountMap.put("screenName", prop.getProperty("screenName"));
			remtterAcountMap
					.put("consumerKey", prop.getProperty("consumerKey"));
			remtterAcountMap.put("consumerSecret",
					prop.getProperty("consumerSecret"));
			remtterAcountMap
					.put("accessToken", prop.getProperty("accessToken"));
			remtterAcountMap.put("accessSecret",
					prop.getProperty("accessSecret"));

			// get crawler properties
			// TODO アカウント何個でも設定出来るようにしたい
			Map<String, String> map = new HashMap<String, String>();
			map.put("accessToken", prop.getProperty("crawler1.accessToken"));
			map.put("accessSecret", prop.getProperty("crawler1.accessSecret"));
			crawlerAcountList.add(map);

			map = new HashMap<String, String>();
			map.put("accessToken", prop.getProperty("crawler2.accessToken"));
			map.put("accessSecret", prop.getProperty("crawler2.accessSecret"));
			crawlerAcountList.add(map);

			followerDataDir = prop.getProperty("followerDataDir");
			countFilePath = prop.getProperty("countFilePath");

			logger.info("crawler num: " + crawlerAcountList.size());
		} catch (IOException e) {
			logger.warn(e.getMessage());
		} finally {
			try {
				in.close();
			} catch (IOException e2) {
			}
		}
	}

	public void submain(String[] args) {
		boolean apiLimitSts = false;

		logger.info("remtter-poi start up");
		twitterService = new TwitterService();

		// setting token
		setRemtterToken();

		// get remtter followers
		List<String> remtterFollowerIds = null;
		try {

			remtterFollowerIds = twitterService.getFollowerIds("", 100000);

			if (remtterFollowerIds == null) {
				return;
			}

			Collections.reverse(remtterFollowerIds);
		} catch (TwitterService.APILimitException e) {
			logger.info("reached rate limit of api");
			return;
		} catch (Exception e) {
			logger.warn("Unexpected shut down: " + e.getMessage());
			return;
		}

		logger.info("remtter follower num: " + remtterFollowerIds.size());

		// check counter File
		File countFile = new File(countFilePath);
		if (!countFile.exists()) {
			FileUtil.write(countFilePath, "0", true);
		}
		int count = Integer.parseInt(FileUtil.read(countFilePath, "UTF-8"));

		for (int crawlerCount = 0; crawlerCount < crawlerAcountList.size(); crawlerCount++) {
			logger.info("crawler:" + crawlerCount);
			// set crawler token
			setCrawlerToken(crawlerCount);

			// main loop util limit of api (APILimitException)
			apiLimitSts = false;
			while (!apiLimitSts) {
				if (remtterFollowerIds.size() <= count) {
					// reset counter
					count = 0;
				}

				String userId = remtterFollowerIds.get(count);

				count++;

				// get user's followers
				List<String> newIds = null;
				try {
					newIds = twitterService.getFollowerIds(userId, 1000);
				} catch (TwitterService.APILimitException e) {
					apiLimitSts = true;
					count--;
					break;
				} catch (TwitterService.UnAuthorizedException e) {
					// TODO remtterじゃないアカウントで401なったときの対応
					logger.info("UnAuthorized -> " + userId);
					try {
						logger.info("Send follow request -> " + userId);
						setRemtterToken();
						twitterService.followUser(userId);
						setCrawlerToken(crawlerCount);
					} catch (Exception e2) {
						logger.warn(e2.getMessage());
					}
					continue;
				}

				// failed to get followers
				if (newIds == null) {
					logger.warn("getFollowerIds return null");
					continue;
				}

				logger.info("count:" + (count - 1) + "  follower num:"
						+ newIds.size());

				// get userName remove this user
				List<String> remNameList = null;
				try {
					remNameList = followerCheck(userId, newIds);
				} catch (TwitterService.APILimitException e) {
					apiLimitSts = true;
					count--;
					break;
				} catch (Exception e) {
				}

				if (remNameList == null) {
					logger.warn("followerCheck return null");
					continue;
				}

				// user remove this user is exsist
				if (remNameList.size() > 0) {
					// create direct message
					int messageCount = 0;
					while (messageCount < remNameList.size() && !apiLimitSts) {
						String endText = "がリムってるみたい";
						StringBuilder messageBuf = new StringBuilder();
						for (; messageCount < remNameList.size(); messageCount++) {
							if ((messageBuf.length()
									+ remNameList.get(messageCount).length()
									+ 1 + endText.length()) > 140) {
								messageCount--;
								break;
							}
							messageBuf.append("@");
							messageBuf.append(remNameList.get(messageCount));
							messageBuf.append(" さん ");
						}

						if (messageCount >= remNameList.size()) {
							messageBuf.append(endText);
						}

						// send direct message
						try {
							setRemtterToken();
							twitterService.sendDirectMessage(
									remtterFollowerIds.get(count),
									messageBuf.toString());
							setCrawlerToken(crawlerCount);
						} catch (TwitterService.APILimitException e) {
							apiLimitSts = true;
							break;
						} catch (TwitterService.UnAuthorizedException e) {
							logger.warn("UnAuthorized send message");
							continue;
						}

						logger.info(messageBuf.toString());
					}
				}

				if (apiLimitSts) {
					count--;
					break;
				}

				// overwrite file
				updateFollowersFile(userId, newIds);

				// update counter
				FileUtil.write(countFilePath, String.valueOf(count - 1), true);
			}
		}

		if (apiLimitSts) {
			logger.info("reached rate limit of api");
		}
	}

	public List<String> followerCheck(String userId, List<String> ids)
			throws TwitterService.APILimitException {
		List<String> removeIdList = new ArrayList<String>();
		List<String> removeNameList = new ArrayList<String>();

		File file = new File(followerDataDir + "/" + userId + ".txt");

		if (ids == null) {
			return null;
		}

		if (file.exists()) {
			String oldText = FileUtil.read(file.getAbsolutePath());
			String[] oldIdAry = oldText.split(",");

			for (String oldId : oldIdAry) {
				boolean chk = false;
				for (String id : ids) {
					if (oldId.equals(id)) {
						chk = true;
						break;
					}
				}
				if (!chk) {
					removeIdList.add(oldId);
				}
			}

			if (removeIdList.size() > 0) {
				try {
					List<Map<String, String>> remMapList = twitterService
							.lookupUsers(removeIdList);

					for (Map<String, String> map : remMapList) {
						removeNameList.add(map.get("screen_name"));
					}
				} catch (TwitterService.APILimitException e) {
					throw new TwitterService.APILimitException(e.getMessage());
				} catch (TwitterService.UnAuthorizedException e) {
					logger.warn("unauthorized lookup");
					removeNameList = null;
				} catch (Exception e) {
					removeNameList = null;
				}
			}
		}

		return removeNameList;
	}

	public void updateFollowersFile(String userId, List<String> followerList) {
		// update followers file
		StringBuilder updateTextBuff = new StringBuilder();
		for (String id : followerList) {
			if (updateTextBuff.length() > 0) {
				updateTextBuff.append(",");
			}
			updateTextBuff.append(id);
		}
		File fileDir = new File(followerDataDir);
		if (!fileDir.isDirectory()) {
			fileDir.mkdirs();
		}

		File file = new File(followerDataDir + "/" + userId + ".txt");
		FileUtil.write(file.getAbsolutePath(), updateTextBuff.toString(), true);
	}

	public void setRemtterToken() {
		twitterService.setToken(remtterAcountMap.get("consumerKey"),
				remtterAcountMap.get("consumerSecret"),
				remtterAcountMap.get("accessToken"),
				remtterAcountMap.get("accessSecret"));
	}

	public void setCrawlerToken(int count) {
		if (count < crawlerAcountList.size()) {
			twitterService.setToken(remtterAcountMap.get("consumerKey"),
					remtterAcountMap.get("consumerSecret"), crawlerAcountList
							.get(count).get("accessToken"), crawlerAcountList
							.get(count).get("accessSecret"));
		}
	}

}
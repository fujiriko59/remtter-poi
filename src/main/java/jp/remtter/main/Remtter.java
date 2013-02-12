package jp.remtter.main;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jp.remtter.service.TwitterService;
import jp.remtter.util.FileUtil;
import jp.remtter.util.LogUtil;

public class Remtter {
	public String remtterUserId = "";
	public String screenName = "";
	public String consumerKey = "";
	public String consumerSecret = "";
	public String accessToken = "";
	public String accessSecret = "";

	public String followerDataDir = "data/followers";
	public String countFilePath = "count.txt";

	private TwitterService twitterService;

	private LogUtil logger = new LogUtil(Remtter.class);

	public static void main(String[] args) {

		Remtter obj = new Remtter();
		obj.submain(args);
	}

	public void submain(String[] args) {
		logger.info("remtter-poi start up");
		try {
			twitterService = new TwitterService();

			// setting token
			twitterService.setToken(screenName, consumerKey, consumerSecret,
					accessToken, accessSecret);

			List<String> remtterFollowerIds = twitterService.getFollowerIds(
					remtterUserId, 100000);
			if (remtterFollowerIds.size() == 0) {
				twitterService.lookupUsers(remtterFollowerIds);
				return;
			}
			Collections.reverse(remtterFollowerIds);

			logger.info("remtter follower num: " + remtterFollowerIds.size());

			File countFile = new File(countFilePath);
			if (!countFile.exists()) {
				FileUtil.write(countFilePath, "0", true);
			}
			int count = Integer.parseInt(FileUtil.read(countFilePath, "UTF-8"));
			
			while (true) {
				if (remtterFollowerIds.size() <= count) {
					count = 0;
				}

				String userId = remtterFollowerIds.get(count);

				count++;

				List<String> newIds = twitterService.getFollowerIds(userId,
						1000);

				if (newIds == null) {
					logger.warn("getFollowerIds return null");
					continue;
				}

				logger.info("count:" + (count - 1) + "  follower num:"
						+ newIds.size());

				List<String> remNameList = followerCheck(userId, newIds);
				if (remNameList == null) {
					logger.warn("followerCheck return null");
					continue;
				}

				if (remNameList.size() > 0) {

					// create direct message
					int messageCount = 0;
					while (messageCount < remNameList.size()) {
						String endText = " さんがリムってるみたい";
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
						twitterService.sendDirectMessage(
								remtterFollowerIds.get(count),
								messageBuf.toString());

						logger.info(messageBuf.toString());
					}
				}

				// update followers file
				StringBuilder updateTextBuff = new StringBuilder();
				for (String id : newIds) {
					if (updateTextBuff.length() > 0) {
						updateTextBuff.append(",");
					}
					updateTextBuff.append(id);
				}
				File fileDir = new File(followerDataDir); 
				if(!fileDir.isDirectory()) {
					fileDir.mkdirs();
				}
				
				File file = new File(followerDataDir + "/" + userId + ".txt");
				FileUtil.write(file.getAbsolutePath(),
						updateTextBuff.toString(), true);
				
				//update counter
				FileUtil.write(countFilePath, String.valueOf(count - 1), true);
			}
		} catch (Exception e) {
			logger.info("api limited");
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
				} catch (Exception e) {
					removeNameList = null;
				}
			}
		}

		return removeNameList;
	}
}
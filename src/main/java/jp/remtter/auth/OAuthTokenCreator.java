package jp.remtter.auth;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthConsumer;
import oauth.signpost.basic.DefaultOAuthProvider;

public class OAuthTokenCreator {
	public void create(String consumerKey, String consumerSecret) {
		try {
			// プロキシサーバの設定
			// System.setProperty("http.proxyHost", "proxy.example.net");
			// System.setProperty("http.proxyPort", "3128");

			OAuthConsumer consumer = new DefaultOAuthConsumer(
					consumerKey, consumerSecret);

			OAuthProvider provider = new DefaultOAuthProvider(
					"http://twitter.com/oauth/request_token",
					"http://twitter.com/oauth/access_token",
					"http://twitter.com/oauth/authorize");

			String authUrl = provider.retrieveRequestToken(consumer,
					OAuth.OUT_OF_BAND);
			System.out.println("このURLにアクセスし、表示されるPINを入力してください。");
			System.out.println(authUrl);
			System.out.print("PIN:");

			BufferedReader br = new BufferedReader(new InputStreamReader(
					System.in));
			String pin = br.readLine();

			provider.retrieveAccessToken(consumer, pin);
			System.out.println("Access token: " + consumer.getToken());
			System.out.println("Token secret: " + consumer.getTokenSecret());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}

package jp.remtter.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class FileUtil {
	private static String defaultEncode = "UTF-8";

	public static String read(String path) {
		return read(path, defaultEncode);
	}

	public static String read(String path, String encode) {
		BufferedReader in = null;
		String text = "";
		try {
			File file = new File(path);

			if (!file.exists()) {
				return null;
			}

			in = new BufferedReader(new InputStreamReader(new FileInputStream(
					path), encode));

			String line;
			while ((line = in.readLine()) != null) {
				text += line;
			}
		} catch (Exception e) {
			text = null;
		} finally {
			try {
				in.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		return text;
	}

	public static void write(String path, String text, boolean overWrite) {
		write(path, text, overWrite, defaultEncode);
	}

	public static void write(String path, String text, boolean overWrite,
			String encode) {
		PrintWriter pw = null;
		try {
			File file = new File(path);
			if (!file.exists()) {
				file.createNewFile();
			}

			pw = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(new FileOutputStream(path,
							!overWrite), encode)));

			pw.print(text);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				pw.close();
			} catch(Exception e) {
				
			}
		}

	}
}

package jp.remtter.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {
	private boolean stsDebug = true;
	private boolean stsWarn = true;
	private boolean stsInfo = true;

	private boolean outputSts = true;
	private boolean fileOutputSts = true;
	
	private String logDir = "logs";
	private String logFileNameFormat = "remtter-log";
	private String logFileType = "out";
	private long maxFileSize = 2000000;

	private String className;

	public LogUtil(Class name) {
		className = name.getName();
	}

	public LogUtil(Class name, boolean sts) {
		className = name.getName();
		outputSts = sts;
	}

	public void debug(String log) {
		String output = time() + " [DEBUG]  " + className + "  " + log;
		if (outputSts && stsDebug) {
			System.out.println(output);
			fileOutput(output);
		}
	}

	public void warn(String log) {
		String output = time() + " WARN " + className + "  " + log;
		if (outputSts && stsWarn) {
			System.out.println(output);
			fileOutput(output);
		}
	}

	public void info(String log) {
		String output = time() + " [INFO] " + className + "  " + log;
		if (outputSts && stsInfo) {
			System.out.println(output);
			fileOutput(output);
		}
	}
	
	private void fileOutput(String log) {
		if(!fileOutputSts) {
			return;
		}
		
		File fileDir = new File(logDir);
		if(!fileDir.isDirectory()) {
			fileDir.mkdirs();
		}
		
		String path = logDir + "/" + logFileNameFormat + "." + logFileType;
		
		File file = new File(path);
		if(file.exists()) {
			if(file.length() > maxFileSize) {
				String newPath = logDir + "/" + logFileNameFormat + "-" + time() + "." + logFileType;
				File newFile = new File(newPath);
				file.renameTo(newFile);
			}
		}
		
		file = new File(path);
		if(!file.exists()) {
			try {
				file.createNewFile();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		String lineCode = System.getProperty("line.separator");
		FileUtil.write(path, log + lineCode, false);
		
	}
	
	private String time() {
		Date date = new Date();

		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

		return sdf.format(date);

	}

}

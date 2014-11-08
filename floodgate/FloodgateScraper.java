import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;

import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URL;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

///
/// floodgate から記譜を取得する
///
///   Usage: java -cp . FloodgateScraper dir year begin-month end-month
///
public final class FloodgateScraper {

  private static final Pattern csaPattern = Pattern.compile("href=\"(.+\\.csa)\"");

  // うるう年か?
  private static boolean isLeapYear(int year) {
    return (year % 400 == 0) || ((year % 4 == 0) && (year % 100 != 0));
  }

  // 年月からその月の日数を取得する
  private static int numDays(int year, int month) {
    switch (month) {
    case 2:
      return isLeapYear(year) ? 29 : 28;
    case 4: case  6:
    case 9: case 11:
      return 30;
    case 1: case  3:
    case 5: case  7:
    case 8: case 10: case 12:
      return 31;
    default:
      return 0;
    }
  }

  public static void scrapeCsaURL(int year, int month, int day, List<URL> urls) throws IOException {
    String baseUrl = String.format("http://wdoor.c.u-tokyo.ac.jp/shogi/x/%04d/%02d/%02d/", year, month, day);
    URL url = new URL(baseUrl);

    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.connect();
    try (InputStream in = connection.getInputStream()) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(in));
      reader.lines().forEach(line -> {
        Matcher csaMatcher = csaPattern.matcher(line);
        if (csaMatcher.find()) {
          // 1 行に 1 つしか存在しないはず
          try {
            String names = csaMatcher.group(1); // 棋譜名
            urls.add(new URL(baseUrl + names));
          } catch (MalformedURLException e) {
            System.err.println(e);
          }
        }
      });
    }
  }

  public static void wget(URL url, File dir) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    connection.connect();
    try (InputStream in = connection.getInputStream()) {
      File file = new File(dir, url.getFile());
      file.getParentFile().mkdirs();
      try (OutputStream out = new FileOutputStream(file)) {
        byte[] buf = new byte[1024*8];
        for (int size; (size = in.read(buf)) >= 0;) {
          out.write(buf, 0, size);
        }
      }
    }
  }

  public static void main(String[] args) throws InterruptedException {
    final File dir        = new File(args[0]);
    final int  year       = Integer.parseInt(args[1]);
    final int  beginMonth = Integer.parseInt(args[2]);
    final int  endMonth   = Integer.parseInt(args[3]);

    List<URL> urls = new ArrayList<URL>();
    {
      for (int month = beginMonth; month <= endMonth; ++month) {
        for (int day = 1, ndays = numDays(year, month); day <= ndays; ++day) {
          System.err.printf("%04d/%02d/%02d%n", year, month, day);
          try {
            scrapeCsaURL(year, month, day, urls);
          } catch (IOException e) {
            System.out.println(e);
          }
          Thread.sleep(1000);
        }
      }
    }
    for (URL url : urls) {
      System.out.println(url);
      try {
        wget(url, dir);
      } catch (IOException e) {
        System.out.println(e);
      }
      Thread.sleep(1000);
    }
    System.err.println(urls.size());
  }
}

import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.HashSet;
import java.util.ArrayList;

///
/// csa 形式の棋譜から独立した手を 1 行ずつのテキストファイルに変換する
///
///   Usage: java -cp . CsaConverter dir > corpus.txt
///
final class CsaConverter {

  // dir 以下の csa ファイルを列挙する
  private static List<File> enumCsa(File dir, List<File> files) {
    if (dir.isDirectory()) {
      for (File file : dir.listFiles()) {
        enumCsa(file, files);
      }
    }
    if (dir.isFile()) {
      if (dir.getName().endsWith(".csa")) {
        files.add(dir);
      }
    }
    return files;
  }


  private static boolean skipHeader(BufferedReader in) throws IOException {
    String line;
    while ((line = in.readLine()) != null) {
      if ("+".equals(line)) {
        return true;
      }
    }
    return false;
  }

  private static void readBody(BufferedReader in, List<String> list) throws IOException {
    String line;
    while ((line = in.readLine()) != null) {
      switch (line.charAt(0)) {
      case '+':
      case '-':
        list.add(line);
        break;
      default:
        //ignore
        break;
      }
    }
  }

  // csa をパースする
  public static void parse(BufferedReader in, List<String> list) throws IOException {
    if (skipHeader(in)) {
      readBody(in, list);
      return; // ok.
    }

    throw new IOException("invalid csa");
  }

  public static void main(String[] args) {
    List<File> files = enumCsa(new File(args[0]), new ArrayList<File>());

    List<String> data = new ArrayList<>();
    for (File file : files) {
      try (BufferedReader in = new BufferedReader(new FileReader(file))) {
        List<String> list = new ArrayList<>();
        parse(in, list);
        if (list.isEmpty()) {
          continue;
        }
        if (list.size() <= 25) {
          continue;
        }
        StringBuilder builder = new StringBuilder();
        for (String te : list) {
          builder.append(" ");
          builder.append(te);
        }
        data.add(builder.toString().trim());
      } catch (IOException e) {
        System.err.println(file);
      }
    }

    String[] array = data.toArray(new String[data.size()]);
    Arrays.sort(array);
    for (String value : array) {
      System.out.println(value);
    }
  }

/*
  private static void readBody(BufferedReader in, Set<String> set) throws IOException {
    String line;
    while ((line = in.readLine()) != null) {
      switch (line.charAt(0)) {
      case '+':
      case '-':
        assert line.equals(decode(encode(line))): line + ": " + decode(encode(line));
        set.add(line);
        break;
      default:
        //ignore
        break;
      }
    }
  }
  public static void main(String[] args) {
    List<File> files = enumCsa(new File(args[0]), new ArrayList<File>());
    List<File> failedFiles = new ArrayList<File>();

    Set<String> set = new HashSet<>();
    for (File file : files) {
      try (BufferedReader in = new BufferedReader(new FileReader(file))) {
        parse(in, set);
      } catch (IOException e) {
        System.err.println(file);
      }
    }

    String[] data = set.toArray(new String[set.size()]);
    Arrays.sort(data);
    for (String w : data) {
      System.out.println(w);
    }
  }
*/
}
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

final class CsaUtility {

  public static final Integer BOS = Integer.MAX_VALUE-1;
  public static final Integer EOS = Integer.MAX_VALUE;

  private static final Map<String, Integer> koma2int;
  private static final char[] int2koma = {
    '歩', '香', '桂', '銀', '金', '角', '飛', '王',
    'と', '杏', '圭', '全',       '馬', '龍',
  };
  private static final char[] colnum = {
    '１', '２', '３', '４', '５', '６', '７', '８', '９',
  };
  private static final char[] rownum = {
    '一', '二', '三', '四', '五', '六', '七', '八', '九',
  };

  static {
    String[] koma = {
      "FU", "KY", "KE", "GI", "KI", "KA", "HI", "OU",
      "TO", "NY", "NK", "NG",       "UM", "RY",
    };

    koma2int = new HashMap<>();
    for (int i = 0, length = koma.length; i < length; ++i) {
      koma2int.put(koma[i], i);
    }
  }

  // 符号をエンコードする (V = 2*82*81*14)
  private static Integer encode(String symbol, final int status) {
    int sg = (symbol.charAt(0) == '+') ? 0 : 1;
    int sx = symbol.charAt(1) - '1';
    int sy = symbol.charAt(2) - '1';
    int ex = symbol.charAt(3) - '1';
    int ey = symbol.charAt(4) - '1';

    if ((sx < 0) || (sy < 0)) {
      sx = 0;
      sy = 9; // b が 81 になるようにする
    } else {
      if ((status & 1) != 0) {
        // 左右反転
        sx = 8 - sx;
        ex = 8 - sx;
      }
      if ((status & 2) != 0) {
        // 手番反転
        sg = 1 - sg;
        sx = 8 - sx;
        sy = 8 - sy;
        ex = 8 - ex;
        ey = 8 - ey;
      }
    }

    //int a = sg;
    //int b = sx * 9 + sy;
    int a = 0;
    int b = 0;
    int c = ex * 9 + ey;
    int d = koma2int.get(symbol.substring(5, 7));

    return  ((a * 82 + b) * 81 + c) * 14 + d;
  }

  private static String decode(int hash) {
    int d = (hash                 ) % 14;
    int c = (hash /  14           ) % 81;
    int b = (hash / (14 * 81     )) % 82;
    int a = (hash / (14 * 81 * 82));

    StringBuilder builder = new StringBuilder();
    //builder.append((a ==  0) ? '+' : '-');
    //builder.append((b == 81) ? "00" : String.format("%c%c", b/9 + '1', b%9 + '1'));
    //builder.append(                   String.format("%c%c", c/9 + '1', c%9 + '1'));
    builder.append(colnum[c/9]);
    builder.append(rownum[c%9]);
    builder.append(int2koma[d]);
    return builder.toString();
  }

  public static Integer[] toIntegers(String line) {
    return toIntegers(line, true);
  }

  public static Integer[] toIntegers(String line, boolean hasNgram) {
    return toIntegers(line, hasNgram, 0);
  }

  ///
  /// status: 0: そのまま、1: 左右反転、2: 手番反転、3: 手番を反転して左右反転
  ///
  ///
  public static Integer[] toIntegers(String line, boolean hasNgram, int status) {
    String[] tokens = line.trim().split(" ");

    int length = tokens.length;
    if (hasNgram) {
      Integer[] data = new Integer[length+2];
      data[0] = CsaUtility.BOS;
      for (int i = 0; i < length; ++i) {
        data[i+1] = CsaUtility.encode(tokens[i], status);
      }
      data[length+1] = CsaUtility.EOS;
      return data;
    } else {
      Integer[] data = new Integer[length];
      for (int i = 0; i < length; ++i) {
        data[i] = CsaUtility.encode(tokens[i], status);
      }
      return data;
    }
  }

  public static String convertToString(Integer c) {
    if (CsaUtility.BOS.equals(c)) {
      return "BOS";
    }
    if (CsaUtility.EOS.equals(c)) {
      return "EOS";
    }
    return CsaUtility.decode(c) + " ";
  }

  /**
   * @return 語彙数
   */
  public static int vocaburaly(final Integer[][] w) {
    final Set<Integer> set = new HashSet<>();
    for (Integer[] m : w) {
      for (Integer v : m) {
        set.add(v);
      }
    }
    return set.size();
  }
}

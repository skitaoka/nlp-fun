import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import java.lang.ref.WeakReference;

///
/// 階層的 Pitman-Yor 言語モデル (HPYLM) のテスト実装 (ハイパーパラメータの推定なし)
///
final class HPYLM {

  /// 最大コンテキスト長 (bigram なら 1、trigram なら 2)
  private final int degree;

  // ハイパーパラメータ (本来はコンテキスト長ごとに推定するのがいいらしい)
  private final double d;     ///< discount parameters
  private final double theta; ///< strength parameters

  // コンテキスト長が 0 のレストラン
  private final Restaurant root = new Restaurant(null);

  private final class Restaurant {
    private WeakReference<Restaurant> parent; ///< 親ノード
    private Map<Character, Restaurant> children; ///< 子ノード
    private Map<Character, List<Table>> tables; ///< 文字に対応するテーブル

    private int numTables; ///< レストランの全テーブル数
    private int numCustomers; ///< レストランの全客数

    private final class Table {
      private int numCustomers;

      int get() {
        return numCustomers;
      }

      void inc() {
        ++numCustomers;
      }

      void dec() {
        --numCustomers;
      }

      boolean isEmpty() {
        return numCustomers == 0;
      }
    }

    Restaurant(Restaurant parent) {
      this.parent   = new WeakReference<>(parent);
      this.children = new HashMap<>();
      this.tables   = new HashMap<>();
    }

    ///
    /// 文字 c に対応する子ノードを返す (無ければ作って返す).
    ///
    private Restaurant child(Character c) {
      Restaurant child = this.children.get(c);
      if (child == null) {
        this.children.put(c, child = new Restaurant(this));
      }
      return child;
    }

    ///
    /// 文字 c に対応するテーブルの集合を返す (無ければ作って返す)
    ///
    List<Table> tables(Character c) {
      List<Table> tables = this.tables.get(c);
      if (tables == null) {
        this.tables.put(c, tables = new ArrayList<>());
      }
      return tables;
    }

    ///
    /// 親のコンテキストで文字 c が生起する確率
    ///
    private double pi(Character c) {
      Restaurant parent = this.parent.get();
      if (parent != null) {
        return parent.probability(c);
      } else {
        return 1.0 / (1 << 16);
      }
    }

    ///
    /// 現在のコンテキストで文字 c が生起する確率
    ///
    double probability(Character c) {
      List<Table> tables = this.tables(c);

      // この文脈 h における文字 w についての客数
      int c_hw = 0;
      for (Table table : tables) {
        c_hw += table.get();
      }

      // この文脈 h における文字 w についてのテーブル数
      final int t_hw = tables.size();

      // この文脈　h における客数
      final int c_h = numCustomers;

      // この文脈 h におけるテーブル数
      final int t_h = numTables;

      return ((c_hw - d * c_hw) + (theta + d * t_h) * pi(c)) / (theta + c_h);
    }

    ///
    /// 文字 c を追加する.
    ///
    void addCustomer(Character c, Random rnd) {
      // 文字 c に対応するテーブルの集合を取得する
      final List<Table> tables = this.tables(c);
      final int size = tables.size(); // 文字　 c に対応するテーブル数

      // cumulative distribution function をつくる
      final double[] cdf = new double[size + 1];
      for (int i = 0; i < size; ++i) {
        cdf[i] = Math.max(0.0, tables.get(i).get() - d);
      }
      {
        cdf[size] = (theta + d * numTables) * pi(c);
      }
      for (int i = 1; i <= size; ++i) {
        cdf[i] += cdf[i-1];
      }

      // 確率に従ってサンプリングする
      int t = 0;
      {
        double xi = rnd.nextDouble() * cdf[size];
        for (int i = 0; i <= size; ++i) {
          if (xi < cdf[i]) {
            t = i; // テーブルを選択
            break;
          }
        }
      }

      // 新しくテーブルを作る
      if (t == size) {
        tables.add(new Table());
        ++numTables;

        // 親にも追加する
        Restaurant parent = this.parent.get();
        if (parent != null) {
          parent.addCustomer(c, rnd);
        }
      }

      // テーブルに客を加える
      tables.get(t).inc();
      ++numCustomers;
    }

    ///
    /// 文字 c を削除する.
    ///
    void removeCustomer(Character c, Random rnd) {
      final List<Table> tables = this.tables(c);
      final int size = tables.size(); // 文字　 c に対応するテーブル数

      // cumulative distribution function をつくる
      final int[] cdf = new int[size];
      for (int i = 0; i < size; ++i) {
        cdf[i] = tables.get(i).get();
      }
      for (int i = 1; i < size; ++i) {
        cdf[i] += cdf[i-1];
      }

      // 確率に従ってサンプリングする
      int t = 0;
      {
        int xi = rnd.nextInt(cdf[cdf.length-1]);
        for (int i = 0; i <= size; ++i) {
          if (xi < cdf[i]) {
            t = i; // テーブルを選択
            break;
          }
        }
      }

      // テーブルから客を削除する
      Table table = tables.get(t);
      table.dec();
      --numCustomers;

      // テーブルの客が 0 になったらテーブル自体を削除する
      if (table.isEmpty()) {
        tables.remove(t);
        --numTables;

        // 親からも削除する
        Restaurant parent = this.parent.get();
        if (parent != null) {
          parent.removeCustomer(c, rnd);
        }
      }
    }

    void dump(String header) {
      int tableCount = 0;
      int customerCount = 0;

      for (Map.Entry<Character, List<Table>> entry : this.tables.entrySet()) {
        List<Table> tables = entry.getValue();
        tableCount += tables.size();

        System.out.printf("%s%s: [", header, entry.getKey());
        for (Table table : tables) {
          customerCount += table.get();

          System.out.printf("%d ", table.get());
        }
        System.out.printf("]%n", header);
      }
      assert numTables    ==    tableCount;
      assert numCustomers == customerCount;
      System.out.printf("%svalidTableCount: %b%n", header, numTables == tableCount);
      System.out.printf("%svalidCustomerCount: %b%n", header, numCustomers == customerCount);

      String indent = header + "  ";
      for (Map.Entry<Character, Restaurant> entry : children.entrySet()) {
        System.out.printf("%s%s: {%n", header, entry.getKey());
        entry.getValue().dump(indent);
        System.out.printf("%s}%n", header);
      }
    }
  }

  ///
  /// @param degree 最大コンテキスト長 (bigram なら 1, trigram なら 2)
  /// @param d discount parameter
  /// @param theta strength parameter
  ///
  HPYLM(int degree, double d, double theta) {
    this.degree = degree;
    this.d      = d;
    this.theta  = theta;
  }

  ///
  /// 客の配置をサンプリングする.
  ///
  void sample(final int numEpoch, final Character[][] statements) {
    Random rnd = new Random();

    for (int epoch = 0; epoch < numEpoch; ++ epoch) {
      System.out.printf("epoch: %d%n", epoch);

      // statements をシャッフル
      shuffle(statements, rnd);

      // すべての文について
      for (Character[] statement : statements) {
        if (epoch > 0) {
          removeCustomer(statement, rnd);
        }
        addCustomer(statement, rnd);
      }

      // ハイパーパラメータの更新
    }
  }

  ///
  /// 対応するノードを見つける.
  ///
  private Restaurant context(final Character[] statement, final int i) {
    Restaurant node = this.root;
    for (int k = 1, length = Math.min(i, degree); k <= length; ++k) {
      node = node.child(statement[i-k]);
    }
    return node;
  }

  ///
  /// 客を追加する.
  ///
  private void addCustomer(Character[] statement, Random rnd) {
    // すべての文字について
    for (int i = 0, n = statement.length; i < n; ++i) {
      context(statement, i).addCustomer(statement[i], rnd);
    }
  }

  ///
  /// 客を削除する.
  ///
  private void removeCustomer(Character[] statement, Random rnd) {
    // すべての文字について
    for (int i = 0, n = statement.length; i < n; ++i) {
      context(statement, i).removeCustomer(statement[i], rnd);
    }
  }

  ///
  /// 文章の生起確率を求める.
  ///
  double[] probability(Character[] statement) {
    double[] p = new double[statement.length];

    // すべての文字について
    for (int i = 0, n = p.length; i < n; ++i) {
      p[i] = context(statement, i).probability(statement[i]);
    }

    return p;
  }

  ///
  /// 内部状態をダンプする.
  ///
  private void dump() {
    root.dump("");
  }

  ///
  /// 配列をシャッフルする
  ///
  private static <T> void shuffle(final T[] data, final Random rnd) {
    for (int i = data.length; i --> 0;) {
      swap(data, rnd.nextInt(i + 1), i);
    }
  }

  ///
  /// 配列の要素をスワップする.
  ///
  private static <T> void swap(final T[] data, final int i, final int j) {
    if (i != j) {
      T tmp = data[i]; data[i] = data[j]; data[j] = tmp;
    }
  }

  private static Character[] toCharacters(String statement) {
    Character[] retval = new Character[statement.length()];
    for (int i = 0, length = retval.length; i < length; ++i) {
      retval[i] = statement.charAt(i);
    }
    return retval;
  }

  private static Character[][] toCharacters(String[] statements) {
    Character[][] retval = new Character[statements.length][];
    for (int i = 0, size = retval.length; i < size; ++i) {
      retval[i] = toCharacters(statements[i]);
    }
    return retval;
  }

  public static void main(String[] args) throws IOException {
    // 入力文章
    final Character[][] statements = toCharacters(new String[] {
      "吾輩は猫である。",
      "名前はまだ無い。",
      "どこで生れたかとんと見当がつかぬ。",
      "何でも薄暗いじめじめした所でニャーニャー泣いていた事だけは記憶している。",
      "吾輩はここで始めて人間というものを見た。",
      "しかもあとで聞くとそれは書生という人間中で一番獰悪な種族であったそうだ。",
      "この書生というのは時々我々を捕えて煮て食うという話である。",
      "しかしその当時は何という考もなかったから別段恐しいとも思わなかった。",
      "ただ彼の掌に載せられてスーと持ち上げられた時何だかフワフワした感じがあったばかりである。",
      "掌の上で少し落ちついて書生の顔を見たのがいわゆる人間というものの見始であろう。",
      "この時妙なものだと思った感じが今でも残っている。",
      "第一毛をもって装飾されべきはずの顔がつるつるしてまるで薬缶だ。",
      "その後ご猫にもだいぶ逢ったがこんな片輪には一度も出会わした事がない。",
      "のみならず顔の真中があまりに突起している。",
      "そうしてその穴の中から時々ぷうぷうと煙を吹く。",
      "どうも咽せぽくて実に弱った。",
      "これが人間の飲む煙草というものである事はようやくこの頃知った。",
    });

    // 学習 (サンプリング) する
    final HPYLM hpylm = new HPYLM(2, 0.2, 2);
    hpylm.sample(1000, statements);
    hpylm.dump();

    // 標準入力の文の確率を出力する
    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty()) {
          for (double p : hpylm.probability(toCharacters(line))) {
            System.out.println(p);
          }
        }
      }
    }
  }
}

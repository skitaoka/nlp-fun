import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Formatter;
import java.util.Random;

import java.lang.ref.WeakReference;

///
/// 可変長 n-gram 言語モデル (VPYLM) のテスト実装 (ハイパーパラメータの推定なし)
///
final class VPYLM {

  /// 通過確率と停止確率のベータ分布のパラメータ
  private final double alpha; ///< stop parameter
  private final double beta; ///< through parameter

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

    private int throughCount; ///< 通過数
    private int stopCount; ///< 停止数

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
    /// 停止確率
    ///
    double stopProbability() {
      return (alpha + stopCount) / (alpha + beta + throughCount + stopCount);
    }

    private void incStopCount() {
      ++stopCount;
    }

    private void decStopCount() {
      --stopCount;
    }

    ///
    /// 通過確率
    ///
    double throughProbability() {
      return (beta + throughCount) / (alpha + beta + throughCount + stopCount);
    }

    private void incThroughCount() {
      ++throughCount;
    }

    private void decThroughCount() {
      --throughCount;
    }

    ///
    /// このノードを停止位置として通過数と停止数をインクリメントする
    ///
    private void inc() {
      Restaurant node = this;
      node.incStopCount();
      while ((node = node.parent.get()) != null) {
        node.incThroughCount();
      }
    }

    ///
    /// このノードを停止位置として通過数と停止数をデクリメントする
    ///
    private void dec() {
      Restaurant node = this;
      node.decStopCount();
      while ((node = node.parent.get()) != null) {
        node.decThroughCount();
      }
    }

    ///
    /// 空のノードを削除する
    ///
    void trim() {
      // 空のテーブルを削除
      for (Iterator<Map.Entry<Character, List<Table>>>
        it = tables.entrySet().iterator(); it.hasNext();)
      {
        if (it.next().getValue().isEmpty()) {
          it.remove();
        }
      }

      // 空の子を削除
      for (Iterator<Map.Entry<Character, Restaurant>>
        it = children.entrySet().iterator(); it.hasNext();)
      {
        if (it.next().getValue().isEmpty()) {
          it.remove();
        }
      }

      // 再帰的に trim()
      for (Restaurant child : children.values()) {
        child.trim();
      }
    }

    ///
    /// 空ノードか?
    ///
    boolean isEmpty() {
      return (numCustomers == 0)
          && (numTables    == 0)
          && (stopCount    == 0)
          && (throughCount == 0);
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
      this.inc(); // 通過数と停止数を更新する

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
      this.dec(); // 通過数と停止数を更新する

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
      System.out.printf("%snumTables: %d%n", header, numTables);
      System.out.printf("%snumCustomers: %d%n", header, numCustomers);
      System.out.printf("%sthroughCount: %d%n", header, throughCount);
      System.out.printf("%sstopCount: %d%n", header, stopCount);
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
  /// @param alpha parameter of beta distribution
  /// @param beta parameter of beta distribution
  /// @param d discount parameter
  /// @param theta strength parameter
  ///
  VPYLM(double alpha, double beta, double d, double theta) {
    this.alpha = alpha;
    this.beta  = beta;
    this.d     = d;
    this.theta = theta;
  }

  ///
  /// 客の配置をサンプリングする.
  ///
  void sample(final int numEpoch, final Character[][] statements) {
    Random rnd = new Random();

    // 文の数
    final int size = statements.length;

    // ngram 長の変数を用意する
    final int[][] orders = new int[size][];
    for (int i = 0; i < size; ++i) {
      orders[i] = new int[statements[i].length];
    }

    for (int epoch = 0; epoch < numEpoch; ++ epoch) {
      System.err.printf("\repoch: %d%n", epoch);

      // statements をシャッフル
      shuffle(statements, orders, rnd);

      // すべての文について
      for (int i = 0; i < size; ++i) {
        if (epoch > 0) {
          removeCustomer(statements[i], orders[i], rnd);
        }
        addCustomer(statements[i], orders[i], rnd);
      }

      // ハイパーパラメータの更新

      // 無駄な領域を削除
      trim();
    }

    // すべて削除されるチェック
    //for (int i = 0; i < size; ++i) {
    //  removeCustomer(statements[i], orders[i], rnd);
    //}
    //trim();
  }

  ///
  /// 空のノードを削除する
  ///
  void trim() {
    this.root.trim();
  }

  ///
  /// 対応するノードを見つける (addCustomer 用).
  ///
  private Restaurant context(final Character[] statement, final int[] order, final int i, final Random rnd) {
    double xi = rnd.nextDouble();

    Restaurant node = this.root;
    for (int k = 1; k <= i; ++k) {
      // このノードでの停止確率
      final double pStop = node.stopProbability();
      if (xi < pStop) {
        order[i] = k - 1;
        return node;
      }
      // 乱数を補正 (毎回乱数を生成してもいいが、重いので)
      xi = (xi - pStop) / (1.0 - pStop);

      // 木をくだる
      node = node.child(statement[i-k]);
    }

    order[i] = i;
    return node;
  }

  ///
  /// 対応するノードを見つける (removeCustomer 用).
  ///
  private Restaurant context(final Character[] statement, final int i, final int order) {
    Restaurant node = this.root;
    for (int k = 1; k <= order; ++k) {
      node = node.child(statement[i-k]);
    }
    return node;
  }

  ///
  /// 対応するノードを見つける (probability 用).
  ///
  private Restaurant context(final Character[] statement, final int i, final Random rnd) {
    double xi = rnd.nextDouble();

    Restaurant node = this.root;
    for (int k = 1; k <= i; ++k) {
      // このノードでの停止確率
      final double pStop = node.stopProbability();
      if (xi < pStop) {
        return node;
      }
      // 乱数を補正 (毎回乱数を生成してもいいが、重いので)
      xi = (xi - pStop) / (1.0 - pStop);

      // 木をくだる
      node = node.child(statement[i-k]);
    }

    return node;
  }

  ///
  /// 客を追加する.
  ///
  private void addCustomer(Character[] statement, int[] order, Random rnd) {
    // すべての文字について
    for (int i = 0, n = statement.length; i < n; ++i) {
      context(statement, order, i, rnd).addCustomer(statement[i], rnd);
    }
  }

  ///
  /// 客を削除する.
  ///
  private void removeCustomer(Character[] statement, int[] order, Random rnd) {
    // すべての文字について
    for (int i = 0, n = statement.length; i < n; ++i) {
      context(statement, i, order[i]).removeCustomer(statement[i], rnd);
    }
  }

  ///
  /// 文章の生起確率を求める.
  ///
  double[] probability(Character[] statement, int sampleSize, Random rnd) {
    final int length = statement.length;

    double[] p = new double[length+1];

    // サンプリングする (オーダー変数を積分消去)
    for (int n = 0; n < sampleSize; ++n) {
      // すべての文字について
      for (int i = 0; i < length; ++i) {
        p[i] += context(statement, i, rnd).probability(statement[i]);
      }
    }
    // 期待値を求める
    for (int i = 0; i < length; ++i) {
      p[i] /= sampleSize;
    }

    // 最後の要素にパープレキシティ (平均分岐数) を挿入する
    double ppl = 0;
    for (int i = 0; i < length; ++i) {
      ppl += Math.log(p[i]);
    }
    p[length] = Math.exp(-ppl / length);

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
  private static <T,S> void shuffle(final T[] data, final S[] meta, final Random rnd) {
    for (int i = data.length; i --> 0;) {
      int j = rnd.nextInt(i + 1);
      swap(data, j, i);
      swap(meta, j, i);
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
      "その後猫にもだいぶ逢ったがこんな片輪には一度も出会わした事がない。",
      "のみならず顔の真中があまりに突起している。",
      "そうしてその穴の中から時々ぷうぷうと煙を吹く。",
      "どうも咽せぽくて実に弱った。",
      "これが人間の飲む煙草というものである事はようやくこの頃知った。",
    });

    // 学習 (サンプリング) する
    final VPYLM vpylm = new VPYLM(1.0, 3.0, 0.1, 2);
    vpylm.sample(1000, statements);
    vpylm.dump();

    // 標準入力の文の確率を出力する
    Random rnd = new Random();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
      String line;
      while ((line = in.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty()) {
          for (double p : vpylm.probability(toCharacters(line), 100, rnd)) {
            System.err.println(p);
          }
        }
      }
    }
  }
}

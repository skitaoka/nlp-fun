import java.util.Random;

final class Word2Vec {

  ///
  /// Word vectors
  ///
  private final double[][] vectors;

  ///
  /// Constructor
  ///
  /// @param v 単語のボキャブラリ数
  /// @param n 次元数
  ///
  Word2Vec(int v, int n) {
    vectors = new double[v][n];

    // 標準生起分布から初期値をサンプリング
    Random engine = new Random();
    for (int i = 0; i < v; ++i) {
      for (int j = 0; j < n; ++j) {
        vectors[i][j] = engine.nextGaussian();
      }
    }
  }

  ///
  /// Sigmoid function.
  ///
  /// @param x arbitrary real value
  /// @return 1/(1+e^{-x})
  ///
  private static double logistic(final double x) {
    return 1.0 / (1.0 + Math.exp(-x));
  }

  ///
  /// Compute a inner product between two vectors.
  ///
  /// @param w a word vector
  /// @param c a word vector related w's context
  /// @return w^T c
  ///
  private static double dot(final double[] w, final double[] c) {
    assert w.length == c.length;

    double retval = 0.0;
    for (int i = 0, length = w.length; i < length; ++i) {
      retval += w[i] * c[i];
    }
    return retval;
  }

  ///
  /// Compute daxpby.
  ///
  /// @param a a scalar value
  /// @param x a real vector
  /// @param b a scalar value
  /// @param y a real vector
  ///
  /// @return y = a * x + b * y.
  ///
  private static void daxpby(final double a, final double[] x, final double b, final double[] y) {
    assert x.length == y.length;

    for (int i = 0, length = x.length; i < length; ++i) {
      y[i] = a * x[i] + b * y[i];
    }
  }

  ///
  /// 正例の c は w の前後 d の範囲にある単語を使い、負例のペアは p(w) p(c)^2/3 の重み付きサンプリングで正例数の K 倍ほど選択する.
  ///
  /// @param epocs    学習の反復回数
  /// @param alpha    学習率
  /// @param lambda   1-正則化係数
  /// @param features 学習するペア
  ///
  public void learn(final int epocs, final double alpha, final double lambda, final int[][] features) {
    final int length = vectors[0].length;  // 単語ベクトルの次元
    final double[] v = new double[length]; // テンポラリの領域

    final double beta = alpha * lambda;
    for (int epoc = 1; epoc <= epocs; ++epoc) {
      for (int[] feature : features) {
        final double[] w = vectors[feature[0]]; //         単語のベクトル
        final double[] c = vectors[feature[1]]; // コンテキスト単語のベクトル
        final double   t =         feature[2] ; // c が w のコンテキスト内に存在するなら 1、そうでないなら -1

        // argmax log[logistic(t.dot(w,c))] + log[logistic(t.dot(w,c))] を求める
        final double gamma = alpha * t * (1.0 - logistic(dot(w, c)));
        System.arraycopy(w, 0, v, 0, length);
        Word2Vec.daxpby(gamma, c, beta, w);
        Word2Vec.daxpby(gamma, v, beta, c);
      }
    }
  }

  public static void main(String[] args) {
  }
}

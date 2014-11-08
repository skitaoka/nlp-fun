import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

final class LDA {

  /**
   * トピック数
   */
  private final int K;

  /**
   * 文章数
   */
  private final int M;

  /**
   * 語彙数
   */
  private final int V;

  /**
   * トピックごとの単語分布
   */
  private final double[][] phi_kv;

  /**
   * 文章ごとのトピック分布
   */
  private final double[][] theta_mk;

  /**
   * @param K トピック数
   * @param M 文章数
   * @param V 語彙数
   */
  LDA(final int K, final int M, final int V) {
    this.K = K;
    this.M = M;
    this.V = V;

    this.phi_kv   = new double[K][V];
    this.theta_mk = new double[M][K];
  }

  /**
   * @return パープレキシティ
   */
  private double perplexity(final Integer [][] w) {
    int n = 0; // 全単語数
    double sum = 0;
    for (int m = 0, M = w.length; m < M; ++m) {
      final int n_m = w[m].length;
      n += n_m;
      for (final int v : w[m]) {
        double dot = 0;
        for (int k = 0; k < K; ++k) {
          dot += theta_mk[m][k] * phi_kv[k][v];
        }
        sum -= Math.log(dot);
      }
    }
    return Math.exp(sum / n);
  }

  /**
   * トピックごとの単語分布を更新
   */
  private void update_phi_kv(
    final int   [][] n_vk,
    final int   []   n_k ,
    final double     beta)
  {
    for (int k = 0; k < K; ++k) {
      for (int v = 0; v < V; ++v) {
        phi_kv[k][v] = (n_vk[v][k] + beta) / (n_k[k] + V * beta);
      }
    }
  }

  /**
   * 文章ごとのトピック分布を更新
   */
  private void update_theta_mk(
    final int    [][] n_mk ,
    final Integer[][] w    ,
    final double      alpha)
  {
    for (int m = 0, M = w.length; m < M; ++m) {
      final int n_m = w[m].length;
      for (int k = 0; k < K; ++k) {
        theta_mk[m][k] = (n_mk[m][k] + alpha) / (n_m + K * alpha);
      }
    }
  }

  /**
   * @param w 文章ごとの単語集合
   * @param alpha ハイパーパラメータ
   * @param beta ハイパーパラメータ
   * @param N サンプルサイズ
   */
  void influence(final Integer[][] w, final double alpha, final double beta, final int sampleSize) {
    assert(w.length == M);

    final Random rnd = new Random();

    // 各単語のトピックをランダムに割り振る
    final int[][] z = new int[M][];
    for (int m = 0; m < M; ++m) {
      final int n_m = w[m].length;
      z[m] = new int[n_m];
      for (int i = 0; i < n_m; ++i) {
        z[m][i] = rnd.nextInt(K);
      }
    }

    // 文章 m 内のトピック k の単語数を数える
    final int[][] n_mk = new int[M][K];
    for (int m = 0; m < M; ++m) {
      for (int k : z[m]) {
        ++n_mk[m][k];
      }
    }

    // 単語 v がトピック k である数を数える
    final int[][] n_vk = new int[V][K];
    for (int m = 0; m < M; ++m) {
      final int n_m = w[m].length;
      for (int i = 0; i < n_m; ++i) {
        final int v = w[m][i];
        final int k = z[m][i];
        ++n_vk[v][k];
      }
    }

    // トピック k の単語数を数える
    final int[] n_k = new int[K];
    for (int k = 0; k < K; ++k) {
      int count = 0;
      for (int m = 0; m < M; ++m) {
        count += n_mk[m][k];
      }
      n_k[k] = count;
    }

    // ギブスサンプリング
    for (int n = 1; n <= sampleSize; ++n) {
      for (int m = 0; m < M; ++m) {
        for (int i = 0, n_m = w[m].length; i < n_m; ++i) {
          final int v = w[m][i];

          {
            final int k = z[m][i];
            --n_mk[m][k];
            --n_vk[v][k];
            --n_k    [k];
          }

          // 新しいトピックをサンプリング
          {
            final double[] theta = new double[K+1];
            for (int k = 0; k < K; ++k) {
              theta[k+1] = theta[k]
                + (n_mk[m][k] + alpha   )
                * (n_vk[v][k] + beta    )
                / (n_k    [k] + beta * V);
            }
            double xi = theta[K] * rnd.nextDouble();
            for (int k = 1; k <= K; ++k) {
              if (xi < theta[k]) {
                z[m][i] = k - 1;
                break;
              }
            }
          }

          {
            final int k = z[m][i];
            ++n_mk[m][k];
            ++n_vk[v][k];
            ++n_k    [k];
          }
        }
      }

      update_phi_kv  (n_vk, n_k, beta );
      update_theta_mk(n_mk, w  , alpha);

      // パープレキシティ
      System.err.printf("iter[%d]: %f%n", n, perplexity(w));
    }
  }

  private static final class Token implements Comparable<Token> {
    private final String sgn; // 符号
    private final double wgt; // 重み

    Token(final String sgn, final double wgt) {
      this.sgn = sgn.trim();
      this.wgt = wgt;
    }

    // 降順
    @Override
    public int compareTo(Token c) {
      if (wgt > c.wgt) {
        return -1;
      }
      if (wgt < c.wgt) {
        return +1;
      }
      {
        return 0;
      }
    }

    @Override
    public String toString() {
      return String.format("{text: \"%s\", size: %f}", sgn, wgt);
    }
  }

  void dump() {
    int sampleSize = 1000;
    Random rnd = new Random();
    double[] theta = new double[V+1];
    for (int k = 0; k < K; ++k) {
      for (int v = 0; v < V; ++v) {
        theta[v+1] = theta[v] + phi_kv[k][v];
      }
      for (int i = 0; i < sampleSize; ++i) {
        if (i > 0) {
          System.out.print(" ");
        }
        double xi = theta[V] * (i + rnd.nextDouble()) / sampleSize;
        for (int v = 0; v < V; ++v) {
          if (xi < theta[v+1]) {
            System.out.printf(CsaUtility.convertToString(v).trim());
            break;
          }
        }
      }
      System.out.println();
    }

    // トピックごとの単語分布
    Token[] chunks = new Token[V];
    for (int k = 0; k < K; ++k) {
      for (int v = 0; v < V; ++v) {
        chunks[v] = new Token(CsaUtility.convertToString(v), phi_kv[k][v]);
      }
      Arrays.sort(chunks);
      for (int v = 0; v < V; ++v) {
        if (v > 0) {
          System.out.print(", ");
        }
        System.out.print(chunks[v]);
      }
      System.out.println();
    }
/*
    System.out.println("{");

    // トピックごとの単語分布
    System.out.println("  \"phi_kv\": {");
    for (int k = 0; k < K; ++k) {
      System.out.printf("    \"%d\": {", k);
      for (int v = 0; v < V; ++v) {
        System.out.printf(" \"%s\": %f,", CsaUtility.convertToString(v), phi_kv[k][v]);
      }
      System.out.println(" },");
    }
    System.out.println("  },");

    // 文章ごとのトピック分布
    System.out.println("  \"theta_mk\": {");
    for (int m = 0; m < M; ++m) {
      System.out.printf("    \"%d\": [", m);
      for (int k = 0; k < K; ++k) {
        System.out.printf(" %f,", theta_mk[m][k]);
      }
      System.out.println(" ],");
    }
    System.out.println("  },");
    System.out.println("}");
*/
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 5) {
      System.err.println("Usage: java LDA corpus-file num-topics alpha beta sample-size");
      return;
    }

    final File   file       = new File(args[0]);
    final int    K          = Integer.parseInt   (args[1]);
    final double alpha      = Double .parseDouble(args[2]);
    final double beta       = Double .parseDouble(args[3]);
    final int    sampleSize = Integer.parseInt   (args[4]);

    final Integer[][] w;
    try (BufferedReader in = new BufferedReader(new FileReader(file))) {
      List<Integer[]> data = new ArrayList<>();

      in.lines()
        .map(line -> line.trim())
        .filter(line -> !line.isEmpty())
        .forEach(line -> {
          data.add(CsaUtility.toIntegers(line, false));
        });

      w = new Integer[data.size()][];
      for (int i = 0, length = w.length; i < length; ++i) {
        w[i] = data.get(i);
      }
    }

    final int V = 9*9*14;
    final int M = w.length;

    System.err.printf("K            = %d%n", K);
    System.err.printf("V            = %d%n", V);
    System.err.printf("M            = %d%n", M);
    System.err.printf("alpha        = %f%n", alpha);
    System.err.printf("beta         = %f%n", beta );
    System.err.printf("sample-size  = %d%n", sampleSize);

    LDA lda = new LDA(K, M, V);
    lda.influence(w, alpha, beta, sampleSize);
    lda.dump();
  }
}

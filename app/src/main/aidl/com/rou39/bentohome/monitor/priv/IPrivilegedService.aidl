package com.rou39.bentohome.monitor.priv;

// Shizuku がシェル権限で起動するプロセスの窓口（docs/monitor.md）。
interface IPrivilegedService {
    // Shizuku の決まり: この番号でプロセスを終了させる
    void destroy() = 16777114;

    // /proc から端末全体と全プロセスの値を読み、行形式のテキストで返す（ProcSnapshot が解析する）
    String snapshot() = 1;

    // シェル権限でコマンドを実行し、標準出力と標準エラーをまとめて返す
    String exec(in String[] command) = 2;
}

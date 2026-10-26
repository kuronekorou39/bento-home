# 計測用プロセスはクラス名から生成されるので、縮小で消さない（docs/monitor.md）
-keep class com.rou39.bentohome.monitor.priv.PrivilegedService { <init>(...); *; }

# 計測用プロセスは adb の app_process からクラス名で起動するので、縮小で消さない（docs/monitor.md）
-keep class com.rou39.bentohome.monitor.priv.Starter { public static void main(java.lang.String[]); }
-keep class com.rou39.bentohome.monitor.priv.PrivilegedService { <init>(...); *; }

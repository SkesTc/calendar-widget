# Glance、WorkManager、DataStore 都自帶 consumer rules；以下是保險起見的補充。

# Glance 用類別名稱建立 ActionCallback（例如月曆的切換月份按鈕）。
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# 保留行號，方便看 Play Console 的當機報告（會上傳 mapping 檔反混淆）。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

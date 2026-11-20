# Credential Manager가 Play 서비스 구현을 리플렉션으로 찾으므로 유지
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# 크래시 리포트에서 줄 번호를 볼 수 있게
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

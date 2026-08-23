# Uygulamada yansıma (reflection) kullanılmıyor; manifest'teki activity'ler ve
# düzenlerde adıyla geçen CompassView için gereken kuralları AGP kendisi üretir.
# Buraya yalnızca R8'in bilemeyeceği şeyler yazılır — şimdilik yok.

# Yığın izlerinin okunabilir kalması için satır numaraları korunur.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

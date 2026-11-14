# Rhino evaluates YouTube's player script. R8 must keep the interpreter it loads by name.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
# Android has no java.beans, javax.script, or jdk.dynalink. Rhino references them from
# optional engine code. NewPipe calls Context directly.
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**

# NewPipe's YouTube player payloads are protobuf lite messages. Generated accessors are reflective.
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-dontwarn com.google.protobuf.**

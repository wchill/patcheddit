-dontoptimize
-repackageclasses 'app.morphe.extension.vendored.jackson'
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions
-adaptclassstrings com.fasterxml.jackson.**

-keep,allowobfuscation class com.fasterxml.jackson.databind.ObjectMapper
-keep,allowobfuscation class com.fasterxml.jackson.databind.JsonNode
-keep,allowobfuscation class com.fasterxml.jackson.databind.node.**
-keep,allowobfuscation class com.fasterxml.jackson.core.JsonFactory
-keep,allowobfuscation class com.fasterxml.jackson.core.JsonParser
-keep,allowobfuscation class com.fasterxml.jackson.core.JsonGenerator
-keep,allowobfuscation class com.fasterxml.jackson.core.JsonToken
-keep,allowobfuscation class com.fasterxml.jackson.core.**Exception

# Jackson uses reflection; retain the members of classes kept by the archive and OAuth APIs.
-keep,allowobfuscation @interface com.fasterxml.jackson.annotation.** { *; }
-keep,allowobfuscation @com.fasterxml.jackson.annotation.** class com.fasterxml.jackson.**
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

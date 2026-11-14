# Project-specific R8 rules. Release shrinking is configured in spec step 12.

# --- NAS source (specs/android-nas.md AC 26) ---
# smbj dispatches session events through mbassador, which finds @Handler methods by
# reflection, and builds packets reflectively in places; dcerpc maps RPC structures. Keep
# both libraries whole (together about 1 MB); BouncyCastle is still shrunk by R8.
-keep class com.hierynomus.** { *; }
-keep class net.engio.mbassy.** { *; }
-keep class com.rapid7.client.dcerpc.** { *; }
# Optional parts these libraries reference but the app never uses: Kerberos (JGSS),
# mbassador's EL filters and Java RMI exceptions. Not on Android, never loaded.
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-dontwarn java.rmi.**
# BouncyCastle does the NTLM, signing and encryption crypto for smbj. With R8's default
# optimisation the minified build failed NTLM logins and signed reads intermittently
# (about 2 in 3 logins, 2026-11-14), the debug build never. Keeping the used crypto
# classes unoptimised fixed it (5/5); unused ones are still removed (+40 KB).
-keep,allowshrinking class org.bouncycastle.crypto.** { *; }

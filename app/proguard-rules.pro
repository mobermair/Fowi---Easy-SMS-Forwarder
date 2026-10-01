# R8 rules for release builds.
#
# No extra rules are needed so far: activities and receivers are kept through the manifest,
# WorkManager ships keep rules for its workers, and stored data is plain org.json without
# reflection. Add rules here if a release build behaves differently from the debug build.

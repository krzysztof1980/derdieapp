# R8 rules for the release build. Kept deliberately empty: the app reflects on nothing
# (no Gson, no service loaders), and Compose + androidx ship consumer proguard rules that
# R8 merges automatically. :app:assembleRelease is the check that this stays true — add a
# keep only when R8 actually breaks something, with the crash log as its justification.

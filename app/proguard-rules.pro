# Room, Compose, Coil and AndroidX ship their own consumer rules.
# Keep enum names: they are persisted in the database by name.
-keepclassmembers enum com.apolilla.calendar.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

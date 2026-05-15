package com.chenweikeng.mcparks.config;

public class ConfigSetting {
    public int volume = ConfigDefaults.VOLUME;
    public int audioCacheLimitMb = ConfigDefaults.AUDIO_CACHE_LIMIT_MB;
    public boolean autoConnect = ConfigDefaults.AUTO_CONNECT;
    public boolean cursorReleaseOnRide = ConfigDefaults.CURSOR_RELEASE_ON_RIDE;
    public boolean hideMountMessageOnRide = ConfigDefaults.HIDE_MOUNT_MESSAGE_ON_RIDE;
    public boolean autoFly = ConfigDefaults.AUTO_FLY;
    public float speedMultiplier = ConfigDefaults.SPEED_MULTIPLIER;
    public boolean hideHealthBar = ConfigDefaults.HIDE_HEALTH_BAR;
    public boolean hideExperienceLevel = ConfigDefaults.HIDE_EXPERIENCE_LEVEL;
    public FullbrightMode fullbrightMode = ConfigDefaults.FULLBRIGHT_MODE;
    public boolean hideJoinLeaveMessages = ConfigDefaults.HIDE_JOIN_LEAVE_MESSAGES;
    public boolean hideScoreboard = ConfigDefaults.HIDE_SCOREBOARD;
    public boolean hideChat = ConfigDefaults.HIDE_CHAT;
    public boolean hideHotbar = ConfigDefaults.HIDE_HOTBAR;
    public boolean hidePlayerOnRide = ConfigDefaults.HIDE_PLAYER_ON_RIDE;
    /**
     * When enabled, remap MCParks' {@code /w} (whisper) and {@code /warp}
     * commands to the ImagineFun-style aliases: {@code /msg} and {@code /w}.
     * Outgoing commands, suggestion requests, and the local command tree
     * are all rewritten client-side so tab-completion works.
     */
    public boolean imagineFunCommandAliases = ConfigDefaults.IMAGINE_FUN_COMMAND_ALIASES;
    /**
     * When enabled, add timezone-converted tooltip lines and a "Next show"
     * banner to the MCParks "Show Times" chest GUI.
     */
    public boolean showTimesEnhancements = ConfigDefaults.SHOW_TIMES_ENHANCEMENTS;
    /**
     * Optional IANA zone id ({@code "America/Los_Angeles"}, {@code "Europe/Berlin"},
     * ...) used as the display timezone for Show Times. Empty means use the
     * system default.
     */
    public String showTimesTimezone = ConfigDefaults.SHOW_TIMES_TIMEZONE;
}

package cn.lcxqy.starfree.economy;

import java.util.Map;

public final class EconomyConfig {
    private final int clock;
    private final int clockExp;
    private final int clockPoints;
    private final int postExp;
    private final int reviewExp;
    private final int deleteExp;
    private final int allowDelete;
    private final int contentAuditLevel;
    private final int commentAuditLevel;
    private final int postMax;
    private final String forbidden;
    private final int banRobots;
    private final int silenceTime;
    private final int disableCode;

    private EconomyConfig(Map<String, Object> row) {
        this.clock = integer(row, "clock");
        this.clockExp = integer(row, "clockExp");
        this.clockPoints = integer(row, "clockPoints");
        this.postExp = integer(row, "postExp");
        this.reviewExp = integer(row, "reviewExp");
        this.deleteExp = integer(row, "deleteExp");
        this.allowDelete = integer(row, "allowDelete");
        this.contentAuditLevel = integer(row, "contentAuditlevel");
        this.commentAuditLevel = integer(row, "auditlevel");
        this.postMax = integer(row, "postMax");
        Object value = value(row, "forbidden");
        this.forbidden = value == null ? "" : String.valueOf(value);
        this.banRobots = integer(row, "banRobots");
        this.silenceTime = integer(row, "silenceTime");
        this.disableCode = integer(row, "disableCode");
    }

    public static EconomyConfig from(Map<String, Object> row) {
        return new EconomyConfig(row);
    }

    private static int integer(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public int getClock() {
        return clock;
    }

    public int getClockExp() {
        return clockExp;
    }

    public int getClockPoints() {
        return clockPoints;
    }

    public int getPostExp() {
        return postExp;
    }

    public int getReviewExp() {
        return reviewExp;
    }

    public int getDeleteExp() {
        return deleteExp;
    }

    public boolean isUserDeleteAllowed() {
        return allowDelete != 0;
    }

    public int getContentAuditLevel() {
        return contentAuditLevel;
    }

    public int getCommentAuditLevel() {
        return commentAuditLevel;
    }

    public int getPostMax() {
        return postMax;
    }

    public String getForbidden() {
        return forbidden;
    }

    public boolean isRobotProtectionEnabled() {
        return banRobots == 1;
    }

    public int getSilenceTime() {
        return silenceTime > 0 ? silenceTime : 600;
    }

    public boolean isCodeDisabled() {
        return disableCode == 1;
    }
}

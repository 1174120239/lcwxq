package cn.lcxqy.starfree.user;

import java.util.Collections;
import java.util.Map;

final class RegistrationConfig {
    private final boolean emailRequired;
    private final boolean invitationRequired;
    private final int phoneMode;
    private final String forbidden;
    private final int rebateLevel;
    private final int rebateAmount;
    private final boolean robotProtection;
    private final int silenceSeconds;

    RegistrationConfig(Map<String, Object> row) {
        Map<String, Object> source = row == null
                ? Collections.<String, Object>emptyMap() : row;
        this.emailRequired = integer(source, "isEmail") > 0;
        this.invitationRequired = integer(source, "isInvite") == 1;
        this.phoneMode = integer(source, "isPhone");
        this.forbidden = text(source, "forbidden");
        this.rebateLevel = integer(source, "rebateLevel");
        this.rebateAmount = Math.max(0, integer(source, "rebateNum"));
        this.robotProtection = integer(source, "banRobots") == 1;
        int configuredSilence = integer(source, "silenceTime");
        this.silenceSeconds = configuredSilence > 0 ? configuredSilence : 600;
    }

    boolean isEmailRequired() {
        return emailRequired;
    }

    int getEmailMode() {
        return emailRequired ? 1 : 0;
    }

    boolean isInvitationRequired() {
        return invitationRequired;
    }

    int getInvitationMode() {
        return invitationRequired ? 1 : 0;
    }

    int getPhoneMode() {
        return phoneMode;
    }

    String getForbidden() {
        return forbidden;
    }

    int getRebateAmount() {
        return rebateLevel == 1 || rebateLevel == 3 ? rebateAmount : 0;
    }

    boolean isRobotProtection() {
        return robotProtection;
    }

    int getSilenceSeconds() {
        return silenceSeconds;
    }

    private static int integer(Map<String, Object> row, String key) {
        Object value = value(row, key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? "" : String.valueOf(value);
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
}

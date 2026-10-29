package me.matl114.utils.commands;

import java.util.List;

public final class CommandPermissionCalculator {
    private CommandPermissionCalculator() {}

    // A plain grant covers itself and descendants; a trailing .* covers descendants only.
    public static boolean hasPermission(List<String> grantedPermissions, String requiredPermission) {
        if (requiredPermission == null) {
            return true;
        }
        if (grantedPermissions == null || requiredPermission.isBlank()) {
            return false;
        }
        return grantedPermissions.stream().anyMatch(granted -> matches(granted, requiredPermission));
    }

    public static boolean matches(String grantedPermission, String requiredPermission) {
        if (grantedPermission == null
                || grantedPermission.isBlank()
                || requiredPermission == null
                || requiredPermission.isBlank()) {
            return false;
        }
        if (grantedPermission.endsWith(".*")) {
            String prefix = grantedPermission.substring(0, grantedPermission.length() - 2);
            return !prefix.isEmpty()
                    && requiredPermission.startsWith(prefix + ".")
                    && requiredPermission.length() > prefix.length() + 1;
        }
        return requiredPermission.equals(grantedPermission) || requiredPermission.startsWith(grantedPermission + ".");
    }
}

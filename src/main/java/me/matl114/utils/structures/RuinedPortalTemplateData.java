package me.matl114.utils.structures;

/** Vanilla 1.21.11 template dimensions and unopened chest block positions. */
public record RuinedPortalTemplateData(
        int sizeX, int sizeY, int sizeZ, int chestX, int chestY, int chestZ) {
    public static RuinedPortalTemplateData get(boolean giant, int start) {
        if (giant) {
            return switch (start) {
                case 1 -> new RuinedPortalTemplateData(11, 17, 16, 4, 3, 3);
                case 2 -> new RuinedPortalTemplateData(11, 16, 16, 9, 1, 9);
                case 3 -> new RuinedPortalTemplateData(16, 16, 16, 9, 2, 3);
                default -> throw new IllegalArgumentException("Invalid giant portal template index");
            };
        }
        return switch (start) {
            case 1 -> new RuinedPortalTemplateData(6, 10, 6, 2, 2, 0);
            case 2 -> new RuinedPortalTemplateData(9, 12, 9, 8, 2, 6);
            case 3 -> new RuinedPortalTemplateData(8, 9, 9, 3, 3, 6);
            case 4 -> new RuinedPortalTemplateData(8, 9, 9, 3, 3, 2);
            case 5 -> new RuinedPortalTemplateData(10, 10, 7, 4, 3, 2);
            case 6 -> new RuinedPortalTemplateData(5, 7, 7, 1, 1, 4);
            case 7 -> new RuinedPortalTemplateData(9, 7, 9, 0, 1, 2);
            case 8 -> new RuinedPortalTemplateData(14, 9, 9, 4, 4, 2);
            case 9 -> new RuinedPortalTemplateData(10, 8, 9, 4, 1, 0);
            case 10 -> new RuinedPortalTemplateData(12, 8, 10, 2, 1, 7);
            default -> throw new IllegalArgumentException("Invalid ruined portal template index");
        };
    }
}

package me.matl114.hooks.impl.xaerowaypoints;

public interface IXWaypoint {
    public int getX();

    public int getY();

    public int getZ();

    public void setX(int x);

    public void setY(int y);

    public void setZ(int z);

    public String getName();

    public void setName(String name);

    public String getInitials();

    public int getColor();

    public void setColor(int color);

    public int getPurpose();

    public void setPurpose(int purpose);

    public boolean isTemp();

    public boolean isYInclude();

    public long getCreatedAt();
}

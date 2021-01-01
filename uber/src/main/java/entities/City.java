package entities;

import java.awt.*;
import java.util.Objects;

// TODO: mapping City -> RouteGuideClient (not here...)

public enum City {
    A(1, "A", new Point(0,0)),
    B(2, "B", new Point(10,10)),
    C(3, "C", new Point(20,20));

    private long id;
    private String name;
    private Point location;

    City(int id, String name, Point location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Point getLocation() {
        return location;
    }

    public void setLocation(Point location) {
        this.location = location;
    }
}


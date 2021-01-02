package entities;

import java.awt.*;
import java.util.Objects;

// TODO: mapping City -> RouteGuideClient (not here...)

public enum City {
    A(1, "A", new Point(0,0), generated.City.A),
    B(2, "B", new Point(10,10), generated.City.B),
    C(3, "C", new Point(20,20), generated.City.C);

    private long id;
    private String name;
    private Point location;
    private generated.City protoType;

    City(int id, String name, Point location, generated.City protoType) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.protoType = protoType;
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

    public generated.City getProtoType() {
        return protoType;
    }

    public void setProtoType(generated.City protoType) {
        this.protoType = protoType;
    }
}


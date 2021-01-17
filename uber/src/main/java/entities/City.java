package entities;

import lombok.Getter;
import lombok.Setter;

import java.awt.Point;

public enum City {
    A("A", new Point(0,0), generated.City.A),
    B("B", new Point(10,10), generated.City.B),
    C("C", new Point(20,10), generated.City.C),
    D("D", new Point(11,20), generated.City.D),
    E("E", new Point(4,20), generated.City.E),
    F("F", new Point(14,0), generated.City.F),
    G("G", new Point(10,19), generated.City.G),
    H("H", new Point(3,10), generated.City.H),
    I("I", new Point(31,27), generated.City.I),
    J("J", new Point(12,32), generated.City.J),
    K("K", new Point(25,9), generated.City.K),
    L("L", new Point(6,31), generated.City.L),
    M("M", new Point(8,10), generated.City.M),
    N("N", new Point(9,13), generated.City.N),
    O("O", new Point(4,23), generated.City.O);

    @Getter @Setter
    private String name;

    @Getter @Setter
    private Point location;

    @Getter @Setter
    private generated.City protoType;

    City(String name, Point location, generated.City protoType) {
        this.name = name;
        this.location = location;
        this.protoType = protoType;
    }
}


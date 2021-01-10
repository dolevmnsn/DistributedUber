package entities;

import lombok.Getter;
import lombok.Setter;

import java.awt.Point;

public enum City {
    A("A", new Point(0,0), generated.City.A),
    B("B", new Point(10,10), generated.City.B),
    C("C", new Point(20,20), generated.City.C),
    D("D", new Point(20,20), generated.City.D),
    E("E", new Point(20,20), generated.City.E),
    F("F", new Point(20,20), generated.City.F),
    G("G", new Point(20,20), generated.City.G),
    H("H", new Point(20,20), generated.City.H),
    I("I", new Point(20,20), generated.City.I),
    J("J", new Point(20,20), generated.City.J),
    K("K", new Point(20,20), generated.City.K),
    L("L", new Point(20,20), generated.City.L),
    M("M", new Point(20,20), generated.City.M),
    N("N", new Point(20,20), generated.City.N),
    O("O", new Point(20,20), generated.City.O),
    P("P", new Point(20,20), generated.City.P);

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


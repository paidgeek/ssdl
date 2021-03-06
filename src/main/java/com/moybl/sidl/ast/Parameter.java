package com.moybl.sidl.ast;

import com.moybl.sidl.Position;

public class Parameter extends Node {

  private String name;
  private Type type;

  public Parameter(Position position, String name, Type type) {
    super(position);
    this.name = name;
    this.type = type;
  }

  public void accept(Visitor visitor) {
    visitor.visit(this);
  }

  public String getName() {
    return name;
  }

  public Type getType() {
    return type;
  }

}

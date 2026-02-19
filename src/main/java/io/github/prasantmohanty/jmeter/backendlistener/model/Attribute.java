package io.github.prasantmohanty.jmeter.backendlistener.model;

public class Attribute {
  private String key;
  private String value;
  private Boolean system;

  public Attribute() {}

  public Attribute(String key, String value, Boolean system) {
    this.key = key;
    this.value = value;
    this.system = system;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Boolean getSystem() {
    return system;
  }

  public void setSystem(Boolean system) {
    this.system = system;
  }
}

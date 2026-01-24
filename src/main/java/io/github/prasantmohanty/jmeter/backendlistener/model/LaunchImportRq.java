
package io.github.prasantmohanty.jmeter.backendlistener.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LaunchImportRq {
  private String name;
  private String description;
  private String mode = "DEFAULT"; // or "DEBUG"
  private Instant startTime;        // optional
  private List<Attribute> attributes = new ArrayList<>();

  public String getName() { return name; }
  public LaunchImportRq setName(String name) { this.name = name; return this; }

  public String getDescription() { return description; }
  public LaunchImportRq setDescription(String description) { this.description = description; return this; }

  public String getMode() { return mode; }
  public LaunchImportRq setMode(String mode) { this.mode = mode; return this; }

  public Instant getStartTime() { return startTime; }
  public LaunchImportRq setStartTime(Instant startTime) { this.startTime = startTime; return this; }

  public List<Attribute> getAttributes() { return attributes; }
  public LaunchImportRq setAttributes(List<Attribute> attributes) { this.attributes = attributes; return this; }

  public LaunchImportRq addAttribute(String key, String value, boolean system) {
    this.attributes.add(new Attribute(key, value, system));
    return this;
  }
}


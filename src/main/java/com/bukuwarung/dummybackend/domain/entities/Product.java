package com.bukuwarung.dummybackend.domain.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Table(name = "products")
@Entity
public class Product extends BaseEntity {

  @NotBlank private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

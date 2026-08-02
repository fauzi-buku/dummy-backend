package com.bukuwarung.dummybackend.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.bukuwarung.dummybackend")
@EntityScan(basePackages = "com.bukuwarung.dummybackend")
@EnableJpaRepositories(basePackages = "com.bukuwarung.dummybackend")
public class DummyBackend {

  public static void main(String[] args) {
    SpringApplication.run(DummyBackend.class, args);
  }
}

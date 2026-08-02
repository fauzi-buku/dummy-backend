package com.bukuwarung.dummybackend.web.apis;

import com.bukuwarung.dummybackend.domain.dtos.ProductDTO;
import com.bukuwarung.dummybackend.usecases.filter_product.FilterProductUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product")
public class ProductController {

  private final FilterProductUseCase filterProductUseCase;

  public ProductController(FilterProductUseCase filterProductUseCase) {
    this.filterProductUseCase = filterProductUseCase;
  }

  @GetMapping("/filter")
  public List<ProductDTO> filterProduct(@RequestParam(name = "nameContains") String name) {
    return filterProductUseCase.filterProduct(name);
  }
}

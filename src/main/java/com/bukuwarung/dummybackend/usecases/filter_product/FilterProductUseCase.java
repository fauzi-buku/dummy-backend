package com.bukuwarung.dummybackend.usecases.filter_product;

import com.bukuwarung.dummybackend.domain.dtos.ProductDTO;
import com.bukuwarung.dummybackend.repositories.ProductRepository;
import com.bukuwarung.dummybackend.utils.MapperUtil;
import java.util.List;

public class FilterProductUseCase {

  private final ProductRepository productRepository;
  private final MapperUtil mapperUtil;

  public FilterProductUseCase(ProductRepository productRepository, MapperUtil mapperUtil) {
    this.productRepository = productRepository;
    this.mapperUtil = mapperUtil;
  }

  public List<ProductDTO> filterProduct(String name) {
    return productRepository.findByNameContaining(name).stream()
        .map(mapperUtil::mapProductToProductDTO)
        .toList();
  }
}

package com.bukuwarung.dummybackend.utils;

import com.bukuwarung.dummybackend.domain.dtos.ProductDTO;
import com.bukuwarung.dummybackend.domain.entities.Product;
import org.springframework.stereotype.Component;

@Component
public class MapperUtil {

  public ProductDTO mapProductToProductDTO(Product product) {
    ProductDTO productDTO = new ProductDTO();
    productDTO.setId(product.getId());
    productDTO.setName(product.getName());
    productDTO.setCreatedAt(product.getCreatedAt());
    return productDTO;
  }
}

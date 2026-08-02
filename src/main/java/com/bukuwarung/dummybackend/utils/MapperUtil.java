package com.bukuwarung.dummybackend.utils;

import com.bukuwarung.dummybackend.domain.dtos.ProductDTO;
import com.bukuwarung.dummybackend.domain.dtos.TransferBatchResponseDTO;
import com.bukuwarung.dummybackend.domain.dtos.TransferItemResponseDTO;
import com.bukuwarung.dummybackend.domain.entities.Product;
import com.bukuwarung.dummybackend.domain.entities.TransferBatch;
import com.bukuwarung.dummybackend.domain.entities.TransferItem;
import java.util.List;
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

  public TransferBatchResponseDTO mapToTransferBatchResponse(
      TransferBatch batch, List<TransferItem> items) {
    TransferBatchResponseDTO dto = new TransferBatchResponseDTO();
    dto.setBatchId(batch.getId());
    dto.setStatus(batch.getStatus());
    dto.setItems(items.stream().map(this::mapTransferItemToResponse).toList());
    return dto;
  }

  public TransferItemResponseDTO mapTransferItemToResponse(TransferItem item) {
    TransferItemResponseDTO dto = new TransferItemResponseDTO();
    dto.setClientReference(item.getClientReference());
    dto.setStatus(item.getStatus());
    dto.setBankReference(item.getBankReference());
    dto.setFailureReason(item.getFailureReason());
    return dto;
  }
}
